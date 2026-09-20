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
        return "export DEBIAN_FRONTEND=noninteractive; " +
               "apt-get update && " +
               "apt-get install -y --no-install-recommends " +
               "xfce4 xfce4-terminal tigervnc-standalone-server tigervnc-common novnc websockify dbus-x11 adwaita-icon-theme"
    }

    fun markRunning(resolution: DesktopResolution = _selectedResolution.value) {
        _desktopState.value = DesktopState.Running(
            vncPort = 5901,
            webPort = 6080,
            resolution = resolution.geometry,
            url = "http://127.0.0.1:6080/vnc.html?autoconnect=true&resize=scale"
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
