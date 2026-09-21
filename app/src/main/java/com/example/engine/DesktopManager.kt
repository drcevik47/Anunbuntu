package com.example.engine

import android.content.Context
import com.example.model.DesktopResolution
import com.example.model.DesktopState
import com.example.model.LogCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class DesktopManager(
    private val context: Context,
    private val installer: UbuntuInstaller,
    private val runner: UbuntuRunner
) {

    private val _desktopState = MutableStateFlow<DesktopState>(DesktopState.NotInstalled)
    val desktopState: StateFlow<DesktopState> = _desktopState.asStateFlow()

    private val _selectedResolution = MutableStateFlow(DesktopResolution.HD_720P)
    val selectedResolution: StateFlow<DesktopResolution> = _selectedResolution.asStateFlow()

    fun setResolution(res: DesktopResolution) {
        _selectedResolution.value = res
        AppLogManager.info(LogCategory.DESKTOP, "Resolution", "Çözünürlük ayarlandı: ${res.label} (${res.geometry})")
    }

    /**
     * Checks whether XFCE4 and VNC are installed in rootfs
     */
    fun isDesktopInstalled(): Boolean {
        if (!installer.isInstalled()) return false
        val rootfs = installer.rootfsDir
        val xfceSession = File(rootfs, "usr/bin/xfce4-session")
        val vncServer = File(rootfs, "usr/bin/vncserver")
        val tigervnc = File(rootfs, "usr/bin/tigervncserver")
        return xfceSession.exists() && (vncServer.exists() || tigervnc.exists())
    }

    fun refreshState() {
        if (!installer.isInstalled()) {
            _desktopState.value = DesktopState.NotInstalled
            return
        }
        if (!isDesktopInstalled()) {
            _desktopState.value = DesktopState.NotInstalled
        } else {
            if (_desktopState.value is DesktopState.NotInstalled) {
                _desktopState.value = DesktopState.Stopped
            }
        }
    }

    /**
     * Prepares ~/.vnc/xstartup script to start XFCE4 environment
     */
    fun configureVncStartup() {
        val rootfs = installer.rootfsDir
        val vncDir = File(rootfs, "root/.vnc").apply { if (!exists()) mkdirs() }
        val xstartup = File(vncDir, "xstartup")

        AppLogManager.info(LogCategory.VNC, "Config", "XFCE4 xstartup yapılandırması hazırlanıyor...")

        xstartup.writeText(
            """
            #!/bin/sh
            unset SESSION_MANAGER
            unset DBUS_SESSION_BUS_ADDRESS
            export DISPLAY=:1
            export XKL_XMODMAP_DISABLE=1
            export LANG=C.UTF-8
            export HOME=/root
            export USER=root
            export WEBKIT_DISABLE_SANDBOX_THIS_IS_DANGEROUS=1
            export WEBKIT_FORCE_SANDBOX=0
            export WEBKIT_DISABLE_COMPOSITING_MODE=1
            export WEBKIT_DISABLE_DMABUF_RENDERER=1
            export G_SLICE=always-malloc
            export LIBGL_ALWAYS_SOFTWARE=1

            # Start DBus session bus required by modern GTK/WebKit/Epiphany
            if command -v dbus-launch >/dev/null 2>&1; then
                eval ${'$'}(dbus-launch --sh-syntax)
                export DBUS_SESSION_BUS_ADDRESS
            fi

            # Start XFCE4 Window Manager and Desktop Session
            if [ -x /usr/bin/startxfce4 ]; then
                exec /usr/bin/startxfce4
            elif [ -x /usr/bin/xfce4-session ]; then
                exec /usr/bin/xfce4-session
            else
                exec x-window-manager
            fi
            """.trimIndent() + "\n"
        )
        xstartup.setExecutable(true, false)

        // Passwordless local VNC configuration for in-app internal webview
        val vncConfig = File(vncDir, "config")
        vncConfig.writeText(
            """
            securitytypes=none
            localhost=yes
            alwaysshared
            """.trimIndent() + "\n"
        )
    }

    /**
     * Prepares all desktop helper scripts directly inside the RootFS
     */
    fun prepareDesktopFiles(resolution: DesktopResolution = _selectedResolution.value) {
        val rootfs = installer.rootfsDir
        val localBin = File(rootfs, "usr/local/bin").apply { if (!exists()) mkdirs() }
        val desktopDir = File(rootfs, "root/Desktop").apply { if (!exists()) mkdirs() }
        val appsDir = File(rootfs, "usr/share/applications").apply { if (!exists()) mkdirs() }
        val helpersDir = File(rootfs, "usr/share/xfce4/helpers").apply { if (!exists()) mkdirs() }
        val xdgXfce = File(rootfs, "etc/xdg/xfce4").apply { if (!exists()) mkdirs() }
        val rootXfce = File(rootfs, "root/.config/xfce4").apply { if (!exists()) mkdirs() }

        // Ensure snap is permanently blocked in existing rootfs as well
        val aptPrefDir = File(rootfs, "etc/apt/preferences.d").apply { if (!exists()) mkdirs() }
        val noSnapPref = File(aptPrefDir, "nosnap.pref")
        if (!noSnapPref.exists()) {
            noSnapPref.writeText(
                """
                # Snap paketlerini kalici olarak engelle
                Package: snapd
                Pin: release *
                Pin-Priority: -10

                Package: snapd:*
                Pin: release *
                Pin-Priority: -10
                """.trimIndent() + "\n"
            )
        }

        // Ensure repositories are correctly configured (Debian vs Ubuntu)
        try {
            val isDebian = File(rootfs, "etc/debian_version").exists() && !File(rootfs, "etc/lsb-release").exists()
            val sourcesList = File(rootfs, "etc/apt/sources.list")
            val currentSources = if (sourcesList.exists()) sourcesList.readText() else ""
            if (isDebian) {
                if (!currentSources.contains("non-free") || !currentSources.contains("bookworm")) {
                    sourcesList.writeText(
                        """
                        deb http://deb.debian.org/debian bookworm main contrib non-free non-free-firmware
                        deb http://deb.debian.org/debian bookworm-updates main contrib non-free non-free-firmware
                        deb http://security.debian.org/debian-security bookworm-security main contrib non-free non-free-firmware
                        """.trimIndent() + "\n"
                    )
                }
            } else {
                if (!currentSources.contains("universe") || !currentSources.contains("multiverse")) {
                    sourcesList.writeText(
                        """
                        deb http://ports.ubuntu.com/ubuntu-ports/ jammy main restricted universe multiverse
                        deb http://ports.ubuntu.com/ubuntu-ports/ jammy-updates main restricted universe multiverse
                        deb http://ports.ubuntu.com/ubuntu-ports/ jammy-security main restricted universe multiverse
                        deb http://ports.ubuntu.com/ubuntu-ports/ jammy-backports main restricted universe multiverse
                        """.trimIndent() + "\n"
                    )
                }
            }
        } catch (_: Exception) {}

        configureVncStartup()

        AppLogManager.info(LogCategory.DESKTOP, "PrepareFiles", "Masaüstü başlatma ve tarayıcı betikleri hazırlanıyor...")

        // 1. Browser launcher script with comprehensive debugging & fallback
        val launcherFile = File(localBin, "x-browser-launcher")
        launcherFile.writeText(
            """
            #!/bin/sh
            LOG="/tmp/browser_launch.log"
            echo "==========================================" >> "${'$'}LOG"
            echo "[${'$'}(date)] Web Tarayıcısı Başlatma İsteği" >> "${'$'}LOG"
            echo "Parametreler: ${'$'}@" >> "${'$'}LOG"

            if [ -z "${'$'}DISPLAY" ]; then
                export DISPLAY=:1
            fi
            echo "DISPLAY=${'$'}DISPLAY" >> "${'$'}LOG"

            export HOME=/root
            export USER=root
            export WEBKIT_DISABLE_SANDBOX_THIS_IS_DANGEROUS=1
            export WEBKIT_FORCE_SANDBOX=0
            export WEBKIT_DISABLE_COMPOSITING_MODE=1
            export WEBKIT_DISABLE_DMABUF_RENDERER=1
            export G_SLICE=always-malloc
            export LIBGL_ALWAYS_SOFTWARE=1

            # Ensure DBus session bus is active
            if [ -z "${'$'}DBUS_SESSION_BUS_ADDRESS" ] && command -v dbus-launch >/dev/null 2>&1; then
                eval ${'$'}(dbus-launch --sh-syntax)
                echo "DBus Başlatıldı: ${'$'}DBUS_SESSION_BUS_ADDRESS" >> "${'$'}LOG"
            fi

            # Check for Firefox / Firefox ESR (Debian official is firefox-esr)
            if command -v firefox-esr >/dev/null 2>&1; then
                echo "Firefox ESR çalıştırılıyor..." >> "${'$'}LOG"
                exec firefox-esr "${'$'}@" >> "${'$'}LOG" 2>&1
            elif [ -x /usr/bin/firefox ] && ! grep -q "snap" /usr/bin/firefox 2>/dev/null; then
                echo "Firefox çalıştırılıyor (/usr/bin/firefox)..." >> "${'$'}LOG"
                exec /usr/bin/firefox "${'$'}@" >> "${'$'}LOG" 2>&1
            elif [ -x /opt/firefox/firefox ]; then
                echo "Firefox çalıştırılıyor (/opt/firefox/firefox)..." >> "${'$'}LOG"
                exec /opt/firefox/firefox "${'$'}@" >> "${'$'}LOG" 2>&1
            elif command -v netsurf-gtk >/dev/null 2>&1; then
                echo "NetSurf GTK çalıştırılıyor..." >> "${'$'}LOG"
                exec netsurf-gtk "${'$'}@" >> "${'$'}LOG" 2>&1
            elif command -v epiphany-browser >/dev/null 2>&1 || command -v epiphany >/dev/null 2>&1; then
                BIN=${'$'}(command -v epiphany-browser || command -v epiphany)
                echo "Epiphany çalıştırılıyor (${'$'}BIN)..." >> "${'$'}LOG"
                exec "${'$'}BIN" "${'$'}@" >> "${'$'}LOG" 2>&1
            elif command -v chromium-browser >/dev/null 2>&1; then
                echo "Chromium çalıştırılıyor..." >> "${'$'}LOG"
                exec chromium-browser --no-sandbox --disable-gpu --disable-dev-shm-usage "${'$'}@" >> "${'$'}LOG" 2>&1
            elif command -v firefox >/dev/null 2>&1; then
                echo "Firefox çalıştırılıyor..." >> "${'$'}LOG"
                exec firefox "${'$'}@" >> "${'$'}LOG" 2>&1
            else
                echo "UYARI: Sistemde hazır grafiksel tarayıcı bulunamadı veya snap kısıtlaması var!" >> "${'$'}LOG"
                xfce4-terminal -T "Firefox & Web Tarayıcısı" -e "sh -c 'echo [BILGI] Ubuntu snap yerine gercek Firefox deb paketi veya NetSurf kullanmalidir.; echo; echo NetSurf GTK aninda acilabilir durumda mi kontrol ediliyor...; if command -v netsurf-gtk >/dev/null 2>&1; then exec netsurf-gtk; else echo NetSurf kurmak icin Enter tusuna basin...; read; apt-get update && apt-get install -y netsurf-gtk; exec netsurf-gtk; fi'" 2>/dev/null || true
            fi
            """.trimIndent() + "\n"
        )
        launcherFile.setExecutable(true, false)

        // Software Store (Synaptic) Launcher Helper with root and autoinstall fallback
        val storeLauncher = File(localBin, "x-software-store")
        storeLauncher.writeText(
            """
            #!/bin/sh
            if [ -z "${'$'}DISPLAY" ]; then
                export DISPLAY=:1
            fi
            export HOME=/root
            export USER=root

            if command -v synaptic >/dev/null 2>&1; then
                exec synaptic "${'$'}@"
            else
                xfce4-terminal -T "Uygulama Mağazası (Synaptic)" -e "sh -c 'echo [UYGULAMA MAĞAZASI] Synaptic kuruluyor, lütfen bekleyin...; echo; apt-get update && apt-get install -y synaptic; echo; echo [BİLGİ] Kurulum bitti! Synaptic açılıyor...; sleep 1; exec synaptic;'" 2>/dev/null || true
            fi
            """.trimIndent() + "\n"
        )
        storeLauncher.setExecutable(true, false)

        // Symlink shortcuts for browser
        val epLink = File(localBin, "epiphany-browser")
        if (!epLink.exists()) {
            File(rootfs, "usr/bin/x-www-browser").delete()
        }

        // 2. Desktop shortcuts
        val browserDesktop = File(desktopDir, "Browser.desktop")
        browserDesktop.writeText(
            """
            [Desktop Entry]
            Version=1.0
            Type=Application
            Name=Web Tarayıcısı
            Comment=İnternette Gezinin
            Exec=/usr/local/bin/x-browser-launcher %U
            Icon=web-browser
            Terminal=false
            Categories=Network;WebBrowser;
            StartupNotify=true
            """.trimIndent() + "\n"
        )
        browserDesktop.setExecutable(true, false)
        File(appsDir, "web-browser.desktop").writeText(browserDesktop.readText())

        // Dedicated Firefox desktop icon
        val firefoxDesktop = File(desktopDir, "Firefox.desktop")
        firefoxDesktop.writeText(
            """
            [Desktop Entry]
            Version=1.0
            Type=Application
            Name=Firefox Web Browser
            Comment=Firefox İnternet Tarayıcısı
            Exec=/usr/local/bin/x-browser-launcher %U
            Icon=firefox
            Terminal=false
            Categories=Network;WebBrowser;
            StartupNotify=true
            """.trimIndent() + "\n"
        )
        firefoxDesktop.setExecutable(true, false)
        File(appsDir, "firefox.desktop").writeText(firefoxDesktop.readText())

        // Dedicated NetSurf desktop icon
        val netsurfDesktop = File(desktopDir, "NetSurf.desktop")
        netsurfDesktop.writeText(
            """
            [Desktop Entry]
            Version=1.0
            Type=Application
            Name=NetSurf Tarayıcı
            Comment=Hafif ve Hızlı Web Tarayıcısı
            Exec=netsurf-gtk %U
            Icon=netsurf
            Terminal=false
            Categories=Network;WebBrowser;
            StartupNotify=true
            """.trimIndent() + "\n"
        )
        netsurfDesktop.setExecutable(true, false)

        val termDesktop = File(desktopDir, "Terminal.desktop")
        termDesktop.writeText(
            """
            [Desktop Entry]
            Version=1.0
            Type=Application
            Name=Uçbirim (Terminal)
            Comment=Linux Komut Satırı
            Exec=xfce4-terminal
            Icon=utilities-terminal
            Terminal=false
            Categories=System;TerminalEmulator;
            StartupNotify=true
            """.trimIndent() + "\n"
        )
        termDesktop.setExecutable(true, false)

        // Gerçek Uygulama Mağazası / Marketi (Synaptic)
        val storeDesktop = File(desktopDir, "Software.desktop")
        storeDesktop.writeText(
            """
            [Desktop Entry]
            Version=1.0
            Type=Application
            Name=Uygulama Mağazası
            Comment=Linux Uygulamalarını Arayın ve Kurun
            Exec=/usr/local/bin/x-software-store
            Icon=synaptic
            Terminal=false
            Categories=System;Settings;PackageManager;
            StartupNotify=true
            """.trimIndent() + "\n"
        )
        storeDesktop.setExecutable(true, false)
        File(appsDir, "software-store.desktop").writeText(storeDesktop.readText())

        // Dosya Yöneticisi (Thunar)
        val filesDesktop = File(desktopDir, "Files.desktop")
        filesDesktop.writeText(
            """
            [Desktop Entry]
            Version=1.0
            Type=Application
            Name=Dosyalar (Thunar)
            Comment=Dosyaları ve Dizinleri Yönetin
            Exec=thunar /root
            Icon=system-file-manager
            Terminal=false
            Categories=System;FileManager;
            StartupNotify=true
            """.trimIndent() + "\n"
        )
        filesDesktop.setExecutable(true, false)

        // Metin Düzenleyici (Mousepad / Geany)
        val editorDesktop = File(desktopDir, "Editor.desktop")
        editorDesktop.writeText(
            """
            [Desktop Entry]
            Version=1.0
            Type=Application
            Name=Metin Düzenleyici
            Comment=Notlar ve Kod Dosyalarını Düzenle
            Exec=sh -c 'if command -v mousepad >/dev/null 2>&1; then mousepad; elif command -v geany >/dev/null 2>&1; then geany; else xfce4-terminal -e nano; fi'
            Icon=accessories-text-editor
            Terminal=false
            Categories=Utility;TextEditor;
            StartupNotify=true
            """.trimIndent() + "\n"
        )
        editorDesktop.setExecutable(true, false)

        // Medya Oynatıcı / VLC
        val mediaDesktop = File(desktopDir, "VLC.desktop")
        mediaDesktop.writeText(
            """
            [Desktop Entry]
            Version=1.0
            Type=Application
            Name=Medya Oynatıcı
            Comment=Müzik ve Video Oynatıcı
            Exec=sh -c 'if command -v vlc >/dev/null 2>&1; then vlc; elif command -v audacious >/dev/null 2>&1; then audacious; else xfce4-terminal -e "echo Medya oynatici icin apt install vlc calistirabilirsiniz; read"; fi'
            Icon=vlc
            Terminal=false
            Categories=AudioVideo;Player;
            StartupNotify=true
            """.trimIndent() + "\n"
        )
        mediaDesktop.setExecutable(true, false)

        // LibreOffice Ofis Paketi
        val officeDesktop = File(desktopDir, "Office.desktop")
        officeDesktop.writeText(
            """
            [Desktop Entry]
            Version=1.0
            Type=Application
            Name=LibreOffice
            Comment=Ofis ve Belge Paketi (Word, Excel)
            Exec=sh -c 'if command -v libreoffice >/dev/null 2>&1; then libreoffice; else xfce4-terminal -e "echo LibreOffice icin Paketler menusunden veya apt install libreoffice calistirabilirsiniz; read"; fi'
            Icon=libreoffice-main
            Terminal=false
            Categories=Office;
            StartupNotify=true
            """.trimIndent() + "\n"
        )
        officeDesktop.setExecutable(true, false)

        // Delete old broken desktop icon if exists
        File(desktopDir, "Epiphany.desktop").delete()

        // 3. XFCE Browser Helper
        val helperDesktop = File(helpersDir, "custom-browser.desktop")
        helperDesktop.writeText(
            """
            [Desktop Entry]
            Version=1.0
            Icon=web-browser
            Type=X-XFCE-Helper
            Name=Web Tarayıcısı
            StartupNotify=true
            X-XFCE-Binaries=x-browser-launcher;netsurf-gtk;epiphany-browser;epiphany;chromium-browser;
            X-XFCE-Category=WebBrowser
            X-XFCE-Commands=/usr/local/bin/x-browser-launcher;
            X-XFCE-CommandsWithParameter=/usr/local/bin/x-browser-launcher "%s";
            """.trimIndent() + "\n"
        )
        File(xdgXfce, "helpers.rc").writeText("WebBrowser=custom-browser\n")
        File(rootXfce, "helpers.rc").writeText("WebBrowser=custom-browser\n")

        // 4. Dedicated start-desktop script with logging
        val geometry = resolution.geometry
        val startDesktopFile = File(localBin, "start-desktop")
        startDesktopFile.writeText(
            """
            #!/bin/sh
            export HOME=/root
            export USER=root
            export LANG=C.UTF-8
            export DISPLAY=:1
            LOG="/tmp/desktop_service.log"
            echo "==========================================" > "${'$'}LOG"
            echo "[${'$'}(date)] start-desktop çalıştırıldı (Geometri: $geometry)" >> "${'$'}LOG"

            # Clean previous VNC & WebSocket instances
            echo "Önceki VNC ve noVNC oturumları temizleniyor..." >> "${'$'}LOG"
            vncserver -kill :1 >> "${'$'}LOG" 2>&1 || true
            pkill -f websockify >> "${'$'}LOG" 2>&1 || true
            pkill -f novnc >> "${'$'}LOG" 2>&1 || true
            rm -rf /tmp/.X11-unix/X1 /tmp/.X1-lock /tmp/.vnc/*.pid 2>/dev/null || true

            # Start TigerVNC server on display :1 (port 5901)
            echo "TigerVNC başlatılıyor (:1, port 5901)..." >> "${'$'}LOG"
            vncserver :1 -geometry $geometry -depth 24 -SecurityTypes None >> "${'$'}LOG" 2>&1
            VNC_RES=${'$'}?
            echo "TigerVNC çıkış kodu: ${'$'}VNC_RES" >> "${'$'}LOG"

            # Patch noVNC style if present
            if [ -f /usr/share/novnc/app/styles/base.css ]; then
                sed -i 's/border-bottom-right-radius:[^;]*;/border-bottom-right-radius: 0px !important;/g' /usr/share/novnc/app/styles/base.css 2>/dev/null || true
                sed -i 's/border-radius:[^;]*;/border-radius: 0px !important;/g' /usr/share/novnc/app/styles/base.css 2>/dev/null || true
            fi
            if [ -f /usr/share/novnc/vnc.html ]; then
                grep -q "ubuntu_novnc_hide" /usr/share/novnc/vnc.html || sed -i 's/<\/head>/<style id="ubuntu_novnc_hide">#noVNC_control_bar_anchor,#noVNC_control_bar,#noVNC_control_bar_handle,.noVNC_control_bar_hint,.noVNC_hint_anchor,#noVNC_mobile_buttons,#noVNC_transition,#noVNC_status{display:none!important;visibility:hidden!important;opacity:0!important;pointer-events:none!important;}.noVNC_panel{visibility:hidden!important;opacity:0!important;pointer-events:none!important;position:fixed!important;left:-9999px!important;}html,body,#noVNC_container{background:#000000!important;background-image:none!important;border-radius:0px!important;-webkit-border-radius:0px!important;}<\/style><\/head>/' /usr/share/novnc/vnc.html 2>/dev/null || true
            fi

            # Start noVNC WebSocket bridge on port 6080
            echo "noVNC köprüsü (Port 6080) başlatılıyor..." >> "${'$'}LOG"
            if [ -d /usr/share/novnc ]; then
                nohup websockify --web /usr/share/novnc 6080 localhost:5901 >> "${'$'}LOG" 2>&1 &
            elif command -v novnc >/dev/null 2>&1; then
                nohup novnc --listen 6080 --vnc localhost:5901 >> "${'$'}LOG" 2>&1 &
            fi

            echo ">>> XFCE4 Masaüstü ve noVNC arka planda başlatıldı (Port: 6080)" >> "${'$'}LOG"
            echo ">>> XFCE4 Masaüstü ve noVNC arka planda başlatıldı (Port: 6080)"
            """.trimIndent() + "\n"
        )
        startDesktopFile.setExecutable(true, false)

        // 5. Dedicated stop-desktop script
        val stopDesktopFile = File(localBin, "stop-desktop")
        stopDesktopFile.writeText(
            """
            #!/bin/sh
            vncserver -kill :1 2>/dev/null || true
            pkill -f websockify 2>/dev/null || true
            pkill -f novnc 2>/dev/null || true
            pkill -f xfce4 2>/dev/null || true
            rm -rf /tmp/.X11-unix/X1 /tmp/.X1-lock 2>/dev/null || true
            echo ">>> XFCE4 Masaüstü sunucusu durduruldu."
            """.trimIndent() + "\n"
        )
        stopDesktopFile.setExecutable(true, false)
    }

    /**
     * Generates the command string to start VNC and noVNC inside Ubuntu
     */
    fun getStartDesktopCommand(resolution: DesktopResolution = _selectedResolution.value): String {
        prepareDesktopFiles(resolution)
        AppLogManager.info(LogCategory.DESKTOP, "StartCmd", "Masaüstü başlatma komutu oluşturuldu (start-desktop).")
        return "chmod +x /usr/local/bin/start-desktop /usr/local/bin/stop-desktop /usr/local/bin/x-browser-launcher 2>/dev/null; /usr/local/bin/start-desktop"
    }

    /**
     * Generates command string to stop VNC and desktop processes
     */
    fun getStopDesktopCommand(): String {
        AppLogManager.info(LogCategory.DESKTOP, "StopCmd", "Masaüstü durdurma komutu oluşturuldu (stop-desktop).")
        return "chmod +x /usr/local/bin/stop-desktop 2>/dev/null; /usr/local/bin/stop-desktop"
    }

    /**
     * Command to install XFCE4, TigerVNC, noVNC and browsers inside Ubuntu
     */
    fun getInstallDesktopCommand(): String {
        AppLogManager.info(LogCategory.APT, "InstallDesktop", "XFCE4, TigerVNC, noVNC ve Tarayıcı paketleri kurulum komutu hazırlanıyor...")
        return "rm -f /etc/apt/apt.conf.d/00_debconf 2>/dev/null; " +
               "export DEBIAN_FRONTEND=noninteractive; " +
               "chmod -R 755 /usr/share/debconf /var/lib/dpkg/info 2>/dev/null; " +
               "apt-get update && " +
               "apt-get install -y --no-install-recommends " +
               "xfce4 xfce4-terminal tigervnc-standalone-server tigervnc-common novnc websockify dbus-x11 adwaita-icon-theme netsurf-gtk epiphany-browser"
    }

    fun markRunning(resolution: DesktopResolution = _selectedResolution.value) {
        val url = "http://127.0.0.1:6080/vnc.html?autoconnect=true&reconnect=true&reconnect_delay=1500&resize=scale"
        _desktopState.value = DesktopState.Running(
            vncPort = 5901,
            webPort = 6080,
            resolution = resolution.geometry,
            url = url
        )
        AppLogManager.success(LogCategory.DESKTOP, "State", "Masaüstü oturumu AKTİF. WebView URL: $url")
    }

    fun markStopped() {
        _desktopState.value = DesktopState.Stopped
        AppLogManager.info(LogCategory.DESKTOP, "State", "Masaüstü oturumu DURDURULDU.")
    }

    fun markStarting() {
        _desktopState.value = DesktopState.Starting
        AppLogManager.info(LogCategory.DESKTOP, "State", "Masaüstü oturumu başlatılıyor...")
    }

    fun markError(msg: String) {
        _desktopState.value = DesktopState.Error(msg)
        AppLogManager.error(LogCategory.DESKTOP, "StateError", msg)
    }
}
