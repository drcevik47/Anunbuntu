package com.example.model

enum class PackageCategory(val title: String) {
    ESSENTIAL("Temel Araçlar"),
    DEVELOPMENT("Geliştirme & Diller"),
    SYSTEM("Sistem & İzleme"),
    NETWORK("Ağ & İletişim")
}

enum class PackageStatus {
    NOT_INSTALLED,
    CHECKING,
    INSTALLING,
    INSTALLED,
    FAILED
}

data class UbuntuPackage(
    val id: String,
    val name: String,
    val category: PackageCategory,
    val description: String,
    val installPackageName: String,
    val checkBinaryPath: String,
    val approxSizeMb: Int
)

object PredefinedPackages {
    val ALL = listOf(
        // Temel Araçlar
        UbuntuPackage(
            id = "curl",
            name = "cURL",
            category = PackageCategory.ESSENTIAL,
            description = "Komut satırından HTTP/HTTPS veri aktarımı ve API istekleri aracı.",
            installPackageName = "curl",
            checkBinaryPath = "usr/bin/curl",
            approxSizeMb = 3
        ),
        UbuntuPackage(
            id = "wget",
            name = "Wget",
            category = PackageCategory.ESSENTIAL,
            description = "İnternetten dosya ve arşiv indirme yardımcı programı.",
            installPackageName = "wget",
            checkBinaryPath = "usr/bin/wget",
            approxSizeMb = 2
        ),
        UbuntuPackage(
            id = "git",
            name = "Git",
            category = PackageCategory.ESSENTIAL,
            description = "Dağıtık sürüm kontrol sistemi (GitHub/GitLab klonlama).",
            installPackageName = "git",
            checkBinaryPath = "usr/bin/git",
            approxSizeMb = 35
        ),
        UbuntuPackage(
            id = "nano",
            name = "Nano Editor",
            category = PackageCategory.ESSENTIAL,
            description = "Kullanımı kolay konsol metin düzenleyicisi.",
            installPackageName = "nano",
            checkBinaryPath = "usr/bin/nano",
            approxSizeMb = 3
        ),

        // Geliştirme & Diller
        UbuntuPackage(
            id = "python3",
            name = "Python 3",
            category = PackageCategory.DEVELOPMENT,
            description = "Python 3 yorumlayıcısı ve temel kütüphaneleri.",
            installPackageName = "python3 python3-pip",
            checkBinaryPath = "usr/bin/python3",
            approxSizeMb = 65
        ),
        UbuntuPackage(
            id = "nodejs",
            name = "Node.js & NPM",
            category = PackageCategory.DEVELOPMENT,
            description = "JavaScript çalışma zamanı ortamı ve paket yöneticisi.",
            installPackageName = "nodejs npm",
            checkBinaryPath = "usr/bin/node",
            approxSizeMb = 80
        ),
        UbuntuPackage(
            id = "build_essential",
            name = "Build Essential (GCC/Make)",
            category = PackageCategory.DEVELOPMENT,
            description = "C/C++ derleyicileri (gcc, g++, make) ve geliştirme başlıkları.",
            installPackageName = "build-essential",
            checkBinaryPath = "usr/bin/gcc",
            approxSizeMb = 120
        ),

        // Sistem & İzleme
        UbuntuPackage(
            id = "neofetch",
            name = "Neofetch",
            category = PackageCategory.SYSTEM,
            description = "Ubuntu logosu ile sistem ve donanım özetini görselleştiren CLI aracı.",
            installPackageName = "neofetch",
            checkBinaryPath = "usr/bin/neofetch",
            approxSizeMb = 4
        ),
        UbuntuPackage(
            id = "htop",
            name = "Htop",
            category = PackageCategory.SYSTEM,
            description = "İnteraktif süreç ve CPU/RAM kullanım paneli.",
            installPackageName = "htop",
            checkBinaryPath = "usr/bin/htop",
            approxSizeMb = 3
        ),

        // Ağ & İletişim
        UbuntuPackage(
            id = "net_tools",
            name = "Net-Tools (ifconfig/netstat)",
            category = PackageCategory.NETWORK,
            description = "Ağ arayüzü yapılandırma ve port kontrol araçları.",
            installPackageName = "net-tools iputils-ping",
            checkBinaryPath = "bin/netstat",
            approxSizeMb = 5
        )
    )
}
