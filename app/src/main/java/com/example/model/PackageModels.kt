package com.example.model

enum class PackageCategory(val title: String) {
    ESSENTIAL("Temel Araçlar"),
    DESKTOP("Masaüstü & Ofis"),
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
        UbuntuPackage(
            id = "epiphany",
            name = "Web Tarayıcısı (Epiphany / GNOME Web)",
            category = PackageCategory.ESSENTIAL,
            description = "XFCE için grafiksel web tarayıcısı.",
            installPackageName = "epiphany-browser",
            checkBinaryPath = "usr/bin/epiphany",
            approxSizeMb = 45
        ),
        UbuntuPackage(
            id = "firefox",
            name = "Mozilla Firefox (Debian / Gerçek DEB)",
            category = PackageCategory.ESSENTIAL,
            description = "Snap gerektirmeyen resmi Mozilla PPA deb paketi. PRoot ve ARM64 uyumlu tam web tarayıcısı.",
            installPackageName = "firefox",
            checkBinaryPath = "usr/lib/firefox/firefox",
            approxSizeMb = 75
        ),
        UbuntuPackage(
            id = "netsurf",
            name = "Hızlı Web Tarayıcısı (NetSurf GTK)",
            category = PackageCategory.ESSENTIAL,
            description = "PRoot ortamı için ultra hafif, hızlı ve anında açılan web tarayıcısı (~10MB).",
            installPackageName = "netsurf-gtk",
            checkBinaryPath = "usr/bin/netsurf-gtk",
            approxSizeMb = 12
        ),
        UbuntuPackage(
            id = "chromium",
            name = "Chromium Web Browser",
            category = PackageCategory.ESSENTIAL,
            description = "Tam özellikli Google Chromium tarayıcısı.",
            installPackageName = "chromium-browser",
            checkBinaryPath = "usr/bin/chromium-browser",
            approxSizeMb = 110
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
        ),

        // Masaüstü & Ofis
        UbuntuPackage(
            id = "synaptic",
            name = "Uygulama Mağazası (Synaptic)",
            category = PackageCategory.DESKTOP,
            description = "Linux için görsel uygulama mağazası. İstediğiniz tüm paketleri arayıp tek tıkla kurun.",
            installPackageName = "synaptic",
            checkBinaryPath = "usr/sbin/synaptic",
            approxSizeMb = 18
        ),
        UbuntuPackage(
            id = "mousepad",
            name = "Mousepad Metin Düzenleyici",
            category = PackageCategory.DESKTOP,
            description = "XFCE için hızlı, hafif grafiksel metin ve not düzenleyici.",
            installPackageName = "mousepad",
            checkBinaryPath = "usr/bin/mousepad",
            approxSizeMb = 4
        ),
        UbuntuPackage(
            id = "file_roller",
            name = "Arşiv Yöneticisi (File Roller & Unrar)",
            category = PackageCategory.DESKTOP,
            description = "Zip, Tar, Gz, 7z ve Rar arşivlerini açıp çıkarma aracı.",
            installPackageName = "file-roller p7zip-full unrar-free unzip",
            checkBinaryPath = "usr/bin/file-roller",
            approxSizeMb = 15
        ),
        UbuntuPackage(
            id = "libreoffice",
            name = "LibreOffice Writer & Calc",
            category = PackageCategory.DESKTOP,
            description = "Tam özellikli açık kaynak ofis paketi (Word, Excel, Sunum).",
            installPackageName = "libreoffice-writer libreoffice-calc libreoffice-gtk3",
            checkBinaryPath = "usr/bin/libreoffice",
            approxSizeMb = 180
        ),
        UbuntuPackage(
            id = "vlc",
            name = "VLC Medya Oynatıcı",
            category = PackageCategory.DESKTOP,
            description = "Tüm video ve ses formatlarını oynatan güçlü medya oynatıcı.",
            installPackageName = "vlc",
            checkBinaryPath = "usr/bin/vlc",
            approxSizeMb = 60
        ),
        UbuntuPackage(
            id = "box64",
            name = "Box64 (x86_64 Emülatörü - Steam Desteği)",
            category = PackageCategory.DESKTOP,
            description = "ARM64 üzerinde x86_64 Linux oyun ve programlarını (Steam vb.) çalıştırma emülatörü.",
            installPackageName = "box64-android",
            checkBinaryPath = "usr/local/bin/box64",
            approxSizeMb = 25
        )
    )
}
