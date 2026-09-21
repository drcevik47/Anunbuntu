package com.example.model

enum class LogLevel {
    INFO,
    WARN,
    ERROR,
    SYSTEM,
    SUCCESS
}

enum class LogCategory(val displayName: String) {
    ALL("Tümü"),
    SYSTEM("Sistem"),
    PROOT("PRoot / Çekirdek"),
    DESKTOP("Masaüstü"),
    VNC("VNC / Ağ"),
    BROWSER("Tarayıcı"),
    WEBVIEW("WebView"),
    APT("Paket Yöneticisi")
}

data class LogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.INFO,
    val category: LogCategory = LogCategory.SYSTEM,
    val tag: String,
    val message: String,
    val details: String? = null
)
