package com.example.model

/**
 * System hardware & architecture analysis model
 */
data class SystemInfo(
    val isArm64: Boolean,
    val primaryAbi: String,
    val allSupportedAbis: List<String>,
    val freeStorageBytes: Long,
    val totalStorageBytes: Long,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val kernelVersion: String,
    val androidVersion: String,
    val apiLevel: Int
) {
    val freeStorageMb: Long get() = freeStorageBytes / (1024 * 1024)
    val totalStorageMb: Long get() = totalStorageBytes / (1024 * 1024)
    val totalRamMb: Long get() = totalRamBytes / (1024 * 1024)
    val availableRamMb: Long get() = availableRamBytes / (1024 * 1024)
    val hasEnoughSpace: Boolean get() = freeStorageMb >= 500
}

/**
 * Official Ubuntu ARM64 distribution definitions
 */
data class UbuntuDistro(
    val id: String,
    val name: String,
    val codename: String,
    val version: String,
    val downloadUrls: List<String>,
    val approxDownloadMb: Int,
    val approxInstalledMb: Int,
    val description: String,
    val isLts: Boolean = true
) {
    val downloadUrl: String get() = downloadUrls.first()
}

object UbuntuDistros {
    val UBUNTU_24_04 = UbuntuDistro(
        id = "ubuntu-24.04-arm64",
        name = "Ubuntu 24.04 LTS",
        codename = "Noble Numbat",
        version = "24.04",
        downloadUrls = listOf(
            "https://cdimage.ubuntu.com/ubuntu-base/releases/noble/release/ubuntu-base-24.04-base-arm64.tar.gz",
            "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04-base-arm64.tar.gz",
            "https://cdimage.ubuntu.com/ubuntu-base/daily/current/noble-base-arm64.tar.gz",
            "https://mirrors.kernel.org/ubuntu-cdimage/ubuntu-base/releases/24.04/release/ubuntu-base-24.04-base-arm64.tar.gz"
        ),
        approxDownloadMb = 31,
        approxInstalledMb = 98,
        description = "En güncel Long-Term Support (LTS) Ubuntu sürümü. ARM64 mimarisi için optimize edilmiş minimal rootfs tabanı.",
        isLts = true
    )

    val UBUNTU_22_04 = UbuntuDistro(
        id = "ubuntu-22.04-arm64",
        name = "Ubuntu 22.04 LTS",
        codename = "Jammy Jellyfish",
        version = "22.04",
        downloadUrls = listOf(
            "https://cdimage.ubuntu.com/ubuntu-base/releases/jammy/release/ubuntu-base-22.04-base-arm64.tar.gz",
            "https://cdimage.ubuntu.com/ubuntu-base/releases/22.04/release/ubuntu-base-22.04-base-arm64.tar.gz",
            "https://cdimage.ubuntu.com/ubuntu-base/daily/current/jammy-base-arm64.tar.gz",
            "https://mirrors.kernel.org/ubuntu-cdimage/ubuntu-base/releases/22.04/release/ubuntu-base-22.04-base-arm64.tar.gz"
        ),
        approxDownloadMb = 29,
        approxInstalledMb = 92,
        description = "Maksimum paket kararlılığı ve uyumluluk sunan güvenilir LTS sürümü.",
        isLts = true
    )

    val ALL = listOf(UBUNTU_24_04, UBUNTU_22_04)
}

/**
 * Installation step / state machine
 */
sealed class InstallState {
    data object Idle : InstallState()
    
    data class Downloading(
        val distro: UbuntuDistro,
        val progress: Float, // 0.0f to 1.0f
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedKbps: Long
    ) : InstallState()

    data class Extracting(
        val distro: UbuntuDistro,
        val processedFiles: Int,
        val currentFileName: String
    ) : InstallState()

    data class Configuring(
        val distro: UbuntuDistro,
        val currentStep: String
    ) : InstallState()

    data class Installed(
        val distro: UbuntuDistro,
        val rootfsPath: String,
        val installedDate: Long,
        val totalSizeMb: Long
    ) : InstallState()

    data class Error(
        val message: String,
        val details: String? = null
    ) : InstallState()
}

/**
 * Terminal line output model
 */
enum class LineType {
    STDOUT,
    STDERR,
    STDIN,
    SYSTEM,
    SUCCESS,
    WARNING
}

data class TerminalOutputLine(
    val id: Long = System.nanoTime(),
    val text: String,
    val type: LineType = LineType.STDOUT,
    val timestamp: Long = System.currentTimeMillis()
)
