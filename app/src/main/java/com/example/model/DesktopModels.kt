package com.example.model

sealed class DesktopState {
    object NotInstalled : DesktopState()
    data class Installing(val step: String, val progress: Float = 0f) : DesktopState()
    object Stopped : DesktopState()
    object Starting : DesktopState()
    data class Running(
        val vncPort: Int = 5901,
        val webPort: Int = 6080,
        val resolution: String = "1280x720",
        val url: String = "http://127.0.0.1:6080/vnc.html?autoconnect=true&resize=scale"
    ) : DesktopState()
    data class Error(val message: String) : DesktopState()
}

enum class DesktopResolution(val label: String, val geometry: String) {
    HD_720P("1280 x 720 (16:9 HD)", "1280x720"),
    MOBILE_WIDE("1440 x 720 (18:9 Mobil)", "1440x720"),
    STANDARD_XGA("1024 x 768 (4:3 Tablet)", "1024x768"),
    FHD_1080P("1920 x 1080 (16:9 Full HD)", "1920x1080")
}
