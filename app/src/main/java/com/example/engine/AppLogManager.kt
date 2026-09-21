package com.example.engine

import android.util.Log
import com.example.model.LogCategory
import com.example.model.LogEntry
import com.example.model.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogManager {
    private const val TAG = "UbuntuApp"
    private const val MAX_LOGS = 1000

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun log(
        level: LogLevel,
        category: LogCategory,
        tag: String,
        message: String,
        details: String? = null
    ) {
        val entry = LogEntry(
            level = level,
            category = category,
            tag = tag,
            message = message,
            details = details
        )

        when (level) {
            LogLevel.INFO -> Log.i("$TAG:$tag", message)
            LogLevel.WARN -> Log.w("$TAG:$tag", "$message ${details.orEmpty()}")
            LogLevel.ERROR -> Log.e("$TAG:$tag", "$message ${details.orEmpty()}")
            LogLevel.SYSTEM -> Log.d("$TAG:$tag", message)
            LogLevel.SUCCESS -> Log.i("$TAG:$tag", "[OK] $message")
        }

        _logs.update { current ->
            val updated = current + entry
            if (updated.size > MAX_LOGS) updated.takeLast(MAX_LOGS) else updated
        }
    }

    fun info(category: LogCategory, tag: String, message: String, details: String? = null) =
        log(LogLevel.INFO, category, tag, message, details)

    fun warn(category: LogCategory, tag: String, message: String, details: String? = null) =
        log(LogLevel.WARN, category, tag, message, details)

    fun error(category: LogCategory, tag: String, message: String, details: String? = null) =
        log(LogLevel.ERROR, category, tag, message, details)

    fun system(category: LogCategory, tag: String, message: String, details: String? = null) =
        log(LogLevel.SYSTEM, category, tag, message, details)

    fun success(category: LogCategory, tag: String, message: String, details: String? = null) =
        log(LogLevel.SUCCESS, category, tag, message, details)

    fun clear() {
        _logs.value = emptyList()
        system(LogCategory.SYSTEM, "LogManager", "Log kayıtları temizlendi.")
    }

    /**
     * Inspects Ubuntu rootfs and reads active log files (VNC, browser launch, xstartup, network ports)
     */
    fun fetchUbuntuSystemLogs(rootfsDir: File) {
        if (!rootfsDir.exists()) {
            warn(LogCategory.SYSTEM, "RootFS", "Ubuntu rootfs dizini bulunamadı: ${rootfsDir.absolutePath}")
            return
        }

        info(LogCategory.SYSTEM, "LogManager", "Ubuntu içindeki sistem logları taranıyor...")

        // 1. Tarayıcı Başlatma Günlüğü (/tmp/browser_launch.log)
        val browserLog = File(rootfsDir, "tmp/browser_launch.log")
        if (browserLog.exists() && browserLog.length() > 0) {
            val content = browserLog.readText().takeLast(3000)
            val level = if (content.contains("hata", ignoreCase = true) || content.contains("error", ignoreCase = true) || content.contains("failed", ignoreCase = true)) {
                LogLevel.ERROR
            } else {
                LogLevel.INFO
            }
            log(level, LogCategory.BROWSER, "browser_launch.log", "Masaüstü Tarayıcı Başlatıcı Çıktısı", content)
        } else {
            info(LogCategory.BROWSER, "browser_launch.log", "Henüz tarayıcı başlatma kaydı yok veya dosya boş.")
        }

        // 2. Epiphany Hata Günlüğü (/tmp/epiphany_err.log)
        val epiphanyLog = File(rootfsDir, "tmp/epiphany_err.log")
        if (epiphanyLog.exists() && epiphanyLog.length() > 0) {
            val content = epiphanyLog.readText().takeLast(3000)
            error(LogCategory.BROWSER, "epiphany_err.log", "Epiphany çökme/hata çıktısı bulundu!", content)
        }

        // 3. Masaüstü Servisi Başlatma Günlüğü (/tmp/desktop_service.log)
        val desktopLog = File(rootfsDir, "tmp/desktop_service.log")
        if (desktopLog.exists() && desktopLog.length() > 0) {
            val content = desktopLog.readText().takeLast(3000)
            log(LogLevel.INFO, LogCategory.DESKTOP, "desktop_service.log", "Masaüstü Servis Başlatma Günlüğü", content)
        }

        // 4. VNC Xvnc Sunucu Günlükleri (/root/.vnc/*.log)
        val vncDir = File(rootfsDir, "root/.vnc")
        if (vncDir.exists()) {
            val vncLogs = vncDir.listFiles { _, name -> name.endsWith(".log") }
            if (vncLogs != null && vncLogs.isNotEmpty()) {
                for (file in vncLogs) {
                    val lines = file.readLines().takeLast(30).joinToString("\n")
                    val level = if (lines.contains("error", ignoreCase = true) || lines.contains("fatal", ignoreCase = true)) {
                        LogLevel.ERROR
                    } else {
                        LogLevel.INFO
                    }
                    log(level, LogCategory.VNC, file.name, "Xvnc Ekran Günlüğü (Son 30 satır)", lines)
                }
            } else {
                info(LogCategory.VNC, "Xvnc", "Henüz .vnc/*.log dosyası oluşmadı.")
            }
        }

        // 5. Port Bağlantı Testleri (5901 VNC ve 6080 noVNC)
        testLocalPort(5901, "TigerVNC (RFB)")
        testLocalPort(6080, "noVNC (WebSocket/HTTP)")

        // 6. APT Terminal Günlüğü (/var/log/apt/term.log)
        val aptTermLog = File(rootfsDir, "var/log/apt/term.log")
        if (aptTermLog.exists() && aptTermLog.length() > 0) {
            val lines = aptTermLog.readLines().takeLast(35).joinToString("\n")
            val hasError = lines.contains("error", ignoreCase = true) || lines.contains("hata", ignoreCase = true) || lines.contains("E: ", ignoreCase = false)
            log(
                if (hasError) LogLevel.ERROR else LogLevel.INFO,
                LogCategory.APT,
                "apt/term.log",
                "APT Terminal Çıktısı (Son 35 satır)",
                lines
            )
        }

        // 7. DPKG Kurulum Günlüğü (/var/log/dpkg.log)
        val dpkgLog = File(rootfsDir, "var/log/dpkg.log")
        if (dpkgLog.exists() && dpkgLog.length() > 0) {
            val lines = dpkgLog.readLines().takeLast(30).joinToString("\n")
            info(LogCategory.APT, "dpkg.log", "Son Paket İşlemleri (dpkg.log)", lines)
        }

        // 8. X11 / xstartup Hata Günlüğü (/tmp/xstartup.log)
        val xstartupLog = File(rootfsDir, "tmp/xstartup.log")
        if (xstartupLog.exists() && xstartupLog.length() > 0) {
            val lines = xstartupLog.readLines().takeLast(30).joinToString("\n")
            log(LogLevel.WARN, LogCategory.DESKTOP, "xstartup.log", "xstartup log kaydı", lines)
        }
    }

    private fun testLocalPort(port: Int, serviceName: String) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 400)
                success(LogCategory.VNC, "PortCheck", "127.0.0.1:$port ($serviceName) açık ve bağlantı kabul ediyor.")
            }
        } catch (e: Exception) {
            warn(LogCategory.VNC, "PortCheck", "127.0.0.1:$port ($serviceName) kapalı veya dinlemiyor: ${e.message}")
        }
    }

    /**
     * Formats all logs into a single shareable/copyable text block
     */
    fun exportLogsText(): String {
        val list = _logs.value
        if (list.isEmpty()) return "Henüz log kaydı bulunmuyor."

        val sb = StringBuilder()
        sb.append("=== UBUNTU ARM64 SİSTEM VE HATA LOGLARI ===\n")
        sb.append("Oluşturulma Zamanı: ${Date()}\n")
        sb.append("Toplam Kayıt: ${list.size}\n\n")

        for (entry in list) {
            val time = timeFormatter.format(Date(entry.timestamp))
            val levelStr = String.format("%-7s", entry.level.name)
            val catStr = String.format("%-10s", "[${entry.category.name}]")
            sb.append("$time $levelStr $catStr ${entry.tag}: ${entry.message}\n")
            if (!entry.details.isNullOrBlank()) {
                sb.append("   DETAYLAR:\n")
                entry.details.lines().forEach { line ->
                    sb.append("   | $line\n")
                }
            }
        }
        sb.append("=== LOG SONU ===")
        return sb.toString()
    }
}
