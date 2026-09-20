package com.example.engine

import android.content.Context
import com.example.model.DesktopResolution
import com.example.model.DesktopState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
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

        xstartup.writeText(
            """
            #!/bin/sh
            unset SESSION_MANAGER
            unset DBUS_SESSION_BUS_ADDRESS
            export XKL_XMODMAP_DISABLE=1
            export LANG=C.UTF-8
            export HOME=/root
            export USER=root
            export WEBKIT_DISABLE_SANDBOX_THIS_IS_DANGEROUS=1
            export WEBKIT_FORCE_SANDBOX=0
            export WEBKIT_DISABLE_COMPOSITING_MODE=1
            export WEBKIT_DISABLE_DMABUF_RENDERER=1

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
     * Generates the command string to start VNC and noVNC inside Ubuntu
     */
    fun getStartDesktopCommand(resolution: DesktopResolution = _selectedResolution.value): String {
        configureVncStartup()
        val geometry = resolution.geometry

        return """
            export HOME=/root
            export USER=root
            export LANG=C.UTF-8

            # Clean previous instances
            vncserver -kill :1 2>/dev/null || true
            pkill -f websockify 2>/dev/null || true
            pkill -f novnc 2>/dev/null || true
            rm -rf /tmp/.X11-unix/X1 /tmp/.X1-lock 2>/dev/null || true

            # Start TigerVNC server on display :1 (port 5901)
            vncserver :1 -geometry $geometry -depth 24 -SecurityTypes None

            # Patch noVNC to remove the light grey background curve and border-bottom-right-radius (800px 600px)
            if [ -f /usr/share/novnc/app/styles/base.css ]; then
                sed -i 's/border-bottom-right-radius:[^;]*;/border-bottom-right-radius: 0px !important;/g' /usr/share/novnc/app/styles/base.css 2>/dev/null || true
                sed -i 's/border-radius:[^;]*;/border-radius: 0px !important;/g' /usr/share/novnc/app/styles/base.css 2>/dev/null || true
                sed -i 's/background-position:right bottom;/background-position: center; background-image: none !important;/g' /usr/share/novnc/app/styles/base.css 2>/dev/null || true
            fi

            # Patch noVNC to export window.UI and window.rfb globally and hide all default noVNC toolbars/buttons
            if [ -f /usr/share/novnc/vnc.html ]; then
                grep -q "window.UI" /usr/share/novnc/vnc.html || sed -i 's/import UI from "\.\/app\/ui\.js";/import UI from ".\/app\/ui.js"; window.UI = UI;/' /usr/share/novnc/vnc.html 2>/dev/null || true
                grep -q "window.UI" /usr/share/novnc/vnc.html || sed -i "s/import UI from '\.\/app\/ui\.js';/import UI from '.\/app\/ui.js'; window.UI = UI;/" /usr/share/novnc/vnc.html 2>/dev/null || true
                grep -q "ubuntu_novnc_hide" /usr/share/novnc/vnc.html || sed -i 's/<\/head>/<style id="ubuntu_novnc_hide">#noVNC_control_bar_anchor,#noVNC_control_bar,#noVNC_control_bar_handle,.noVNC_control_bar_hint,.noVNC_hint_anchor,#noVNC_mobile_buttons,#noVNC_transition,#noVNC_status{display:none!important;visibility:hidden!important;opacity:0!important;pointer-events:none!important;}.noVNC_panel{visibility:hidden!important;opacity:0!important;pointer-events:none!important;position:fixed!important;left:-9999px!important;}html,body,#noVNC_container{background:#000000!important;background-image:none!important;border-radius:0px!important;-webkit-border-radius:0px!important;}<\/style><\/head>/' /usr/share/novnc/vnc.html 2>/dev/null || true
            fi
            if [ -f /usr/share/novnc/app/ui.js ]; then
                grep -q "window.rfb" /usr/share/novnc/app/ui.js || sed -i 's/this\.rfb = new RFB(/window.rfb = this.rfb = new RFB(/' /usr/share/novnc/app/ui.js 2>/dev/null || true
            fi

            # Configure universal browser launcher for XFCE & PRoot environment
            mkdir -p /etc/xdg/xfce4 /root/.config/xfce4 /usr/share/xfce4/helpers /root/Desktop /usr/share/applications /usr/local/bin 2>/dev/null || true
            cat << 'EOF' > /usr/local/bin/x-browser-launcher
#!/bin/sh
export WEBKIT_DISABLE_SANDBOX_THIS_IS_DANGEROUS=1
export WEBKIT_FORCE_SANDBOX=0
export WEBKIT_DISABLE_COMPOSITING_MODE=1
export WEBKIT_DISABLE_DMABUF_RENDERER=1

# Ensure DBus session bus is active
if [ -z "\${'$'}DBUS_SESSION_BUS_ADDRESS" ] && command -v dbus-launch >/dev/null 2>&1; then
    eval ${'$'}(dbus-launch --sh-syntax)
fi

# Try launching installed browsers in order of compatibility
if command -v epiphany-browser >/dev/null 2>&1; then
    exec epiphany-browser "\${'$'}@"
elif command -v epiphany >/dev/null 2>&1; then
    exec epiphany "\${'$'}@"
elif command -v netsurf-gtk >/dev/null 2>&1; then
    exec netsurf-gtk "\${'$'}@"
elif command -v chromium-browser >/dev/null 2>&1; then
    exec chromium-browser --no-sandbox --disable-gpu --disable-dev-shm-usage "\${'$'}@"
elif command -v firefox >/dev/null 2>&1; then
    exec firefox "\${'$'}@"
else
    zenity --info --title="Web Tarayicisi Bulunamadi" --text="Sistemde kurulu web tarayicisi bulunamadi.\nLutfen Terminalden 'apt install -y epiphany-browser netsurf-gtk' komutunu calistirin." 2>/dev/null || \
    xmessage -center "Web tarayicisi henuz kurulu degil. Lutfen terminalden: apt install -y epiphany-browser netsurf-gtk calistirin." 2>/dev/null || true
fi
EOF
            chmod +x /usr/local/bin/x-browser-launcher 2>/dev/null || true
            ln -sf /usr/local/bin/x-browser-launcher /usr/local/bin/epiphany-browser 2>/dev/null || true
            ln -sf /usr/local/bin/x-browser-launcher /usr/bin/x-www-browser 2>/dev/null || true

            cat << 'EOF' > /root/Desktop/Browser.desktop
[Desktop Entry]
Version=1.0
Type=Application
Name=Web Tarayıcısı
Comment=İnternette Gezinin
Exec=/usr/local/bin/x-browser-launcher %U
Icon=org.gnome.Epiphany
Terminal=false
Categories=Network;WebBrowser;
StartupNotify=true
EOF
            chmod +x /root/Desktop/Browser.desktop 2>/dev/null || true
            cp /root/Desktop/Browser.desktop /usr/share/applications/web-browser.desktop 2>/dev/null || true

            cat << 'EOF' > /usr/share/xfce4/helpers/custom-browser.desktop
[Desktop Entry]
Version=1.0
Icon=org.gnome.Epiphany
Type=X-XFCE-Helper
Name=X-Browser
StartupNotify=true
X-XFCE-Binaries=x-browser-launcher;epiphany-browser;epiphany;netsurf-gtk;chromium-browser;
X-XFCE-Category=WebBrowser
X-XFCE-Commands=/usr/local/bin/x-browser-launcher;
X-XFCE-CommandsWithParameter=/usr/local/bin/x-browser-launcher "%s";
EOF
            echo "WebBrowser=custom-browser" > /etc/xdg/xfce4/helpers.rc 2>/dev/null || true
            echo "WebBrowser=custom-browser" > /root/.config/xfce4/helpers.rc 2>/dev/null || true

            # Start noVNC WebSocket bridge on port 6080
            if [ -d /usr/share/novnc ]; then
                websockify --web /usr/share/novnc 6080 localhost:5901 >/dev/null 2>&1 &
            elif command -v novnc >/dev/null 2>&1; then
                novnc --listen 6080 --vnc localhost:5901 >/dev/null 2>&1 &
            fi

            echo ">>> XFCE4 Masaüstü ve noVNC başlatıldı (Port: 6080)"
        """.trimIndent()
    }

    /**
     * Generates command string to stop VNC and desktop processes
     */
    fun getStopDesktopCommand(): String {
        return """
            vncserver -kill :1 2>/dev/null || true
            pkill -f websockify 2>/dev/null || true
            pkill -f novnc 2>/dev/null || true
            pkill -f xfce4 2>/dev/null || true
            echo ">>> XFCE4 Masaüstü sunucusu durduruldu."
        """.trimIndent()
    }

    /**
     * Command to install XFCE4, TigerVNC and noVNC inside Ubuntu
     */
    fun getInstallDesktopCommand(): String {
        return "rm -f /etc/apt/apt.conf.d/00_debconf 2>/dev/null; " +
               "export DEBIAN_FRONTEND=noninteractive; " +
               "chmod -R 755 /usr/share/debconf /var/lib/dpkg/info 2>/dev/null; " +
               "apt-get update && " +
               "apt-get install -y --no-install-recommends " +
               "xfce4 xfce4-terminal tigervnc-standalone-server tigervnc-common novnc websockify dbus-x11 adwaita-icon-theme epiphany-browser"
    }

    fun markRunning(resolution: DesktopResolution = _selectedResolution.value) {
        _desktopState.value = DesktopState.Running(
            vncPort = 5901,
            webPort = 6080,
            resolution = resolution.geometry,
            url = "http://127.0.0.1:6080/vnc.html?autoconnect=true&reconnect=true&reconnect_delay=1500&resize=scale"
        )
    }

    fun markStopped() {
        _desktopState.value = DesktopState.Stopped
    }

    fun markStarting() {
        _desktopState.value = DesktopState.Starting
    }

    fun markError(msg: String) {
        _desktopState.value = DesktopState.Error(msg)
    }
}
