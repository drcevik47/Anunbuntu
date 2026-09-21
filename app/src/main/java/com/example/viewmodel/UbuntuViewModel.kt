package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.UbuntuDownloadManager
import com.example.engine.UbuntuInstaller
import com.example.engine.UbuntuRunner
import com.example.model.InstallState
import com.example.model.LineType
import com.example.model.SystemInfo
import com.example.model.TerminalOutputLine
import com.example.model.UbuntuDistro
import com.example.model.UbuntuDistros
import com.example.util.DeviceSystemHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.net.InetSocketAddress
import java.net.Socket

import com.example.engine.DesktopManager
import com.example.model.DesktopResolution
import com.example.model.DesktopState
import com.example.model.PackageCategory
import com.example.model.PackageStatus
import com.example.model.PredefinedPackages
import com.example.model.UbuntuPackage
import java.io.File
import kotlinx.coroutines.delay

class UbuntuViewModel(application: Application) : AndroidViewModel(application) {

    private val downloadManager = UbuntuDownloadManager(application)
    val installer = UbuntuInstaller(application)
    val runner = UbuntuRunner(application, installer)
    val desktopManager = DesktopManager(application, installer, runner)

    private val _systemInfo = MutableStateFlow(DeviceSystemHelper.getSystemInfo(application))
    val systemInfo: StateFlow<SystemInfo> = _systemInfo.asStateFlow()

    private val _selectedDistro = MutableStateFlow<UbuntuDistro>(UbuntuDistros.UBUNTU_24_04)
    val selectedDistro: StateFlow<UbuntuDistro> = _selectedDistro.asStateFlow()

    private val _installState = MutableStateFlow<InstallState>(InstallState.Idle)
    val installState: StateFlow<InstallState> = _installState.asStateFlow()

    private val _terminalLines = MutableStateFlow<List<TerminalOutputLine>>(emptyList())
    val terminalLines: StateFlow<List<TerminalOutputLine>> = _terminalLines.asStateFlow()

    val isTerminalRunning: StateFlow<Boolean> = runner.isRunningFlow
    val isCommandExecuting: StateFlow<Boolean> = runner.isExecuting
    val currentWorkingDir: StateFlow<String> = runner.currentWorkingDir

    private val _packageStatuses = MutableStateFlow<Map<String, PackageStatus>>(emptyMap())
    val packageStatuses: StateFlow<Map<String, PackageStatus>> = _packageStatuses.asStateFlow()

    private val _isPRootReady = MutableStateFlow(runner.prootManager.isPRootInstalled)
    val isPRootReady: StateFlow<Boolean> = _isPRootReady.asStateFlow()

    val desktopState: StateFlow<DesktopState> = desktopManager.desktopState
    val selectedResolution: StateFlow<DesktopResolution> = desktopManager.selectedResolution

    val logs = com.example.engine.AppLogManager.logs

    private val _activeTab = MutableStateFlow(0) // 0: Sistem, 1: Kurulum, 2: Terminal, 3: Paketler, 4: Masaüstü, 5: Loglar
    val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

    fun setActiveTab(tab: Int) {
        _activeTab.value = tab
        if (tab == 5) {
            fetchSystemLogs()
        }
    }

    fun fetchSystemLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            com.example.engine.AppLogManager.fetchUbuntuSystemLogs(installer.rootfsDir)
        }
    }

    fun clearLogs() {
        com.example.engine.AppLogManager.clear()
    }

    fun exportLogs(): String = com.example.engine.AppLogManager.exportLogsText()

    fun testRunBrowser() {
        viewModelScope.launch {
            com.example.engine.AppLogManager.info(
                com.example.model.LogCategory.BROWSER,
                "TestRun",
                "Tarayıcı terminal ortamında doğrudan test ediliyor..."
            )
            if (!runner.isRunning) {
                runner.startSession(viewModelScope)
                delay(1000)
            }
            val testCmd = "export DISPLAY=:1; echo '=== Browser Test ==='; which netsurf-gtk epiphany-browser chromium-browser firefox; /usr/local/bin/x-browser-launcher --help 2>&1 | head -n 10; cat /tmp/browser_launch.log 2>/dev/null | tail -n 25"
            sendCommand(testCmd)
            delay(2500)
            fetchSystemLogs()
        }
    }

    private var installJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runner.prootManager.ensurePRootInstalled()
            _isPRootReady.value = runner.prootManager.isPRootInstalled
        }
        checkExistingInstallation()
        observeTerminalOutput()
    }

    private fun checkExistingInstallation() {
        if (installer.isInstalled()) {
            val distroName = installer.getInstalledDistroName() ?: "Ubuntu ARM64"
            val sizeMb = installer.getRootfsSizeMb()
            val matchedDistro = UbuntuDistros.ALL.firstOrNull { distroName.contains(it.version) } 
                ?: UbuntuDistros.UBUNTU_24_04

            _installState.value = InstallState.Installed(
                distro = matchedDistro,
                rootfsPath = installer.rootfsDir.absolutePath,
                installedDate = System.currentTimeMillis(),
                totalSizeMb = sizeMb
            )
            checkPackageStatuses()
            desktopManager.refreshState()
        }
    }

    private fun observeTerminalOutput() {
        viewModelScope.launch {
            runner.outputFlow.collect { line ->
                _terminalLines.update { current ->
                    // Limit buffer size to 1000 lines
                    if (current.size > 1000) {
                        current.drop(100) + line
                    } else {
                        current + line
                    }
                }
            }
        }
    }

    fun selectDistro(distro: UbuntuDistro) {
        if (_installState.value !is InstallState.Downloading && _installState.value !is InstallState.Extracting) {
            _selectedDistro.value = distro
        }
    }

    fun refreshSystemInfo() {
        _systemInfo.value = DeviceSystemHelper.getSystemInfo(getApplication())
    }

    fun startInstallation() {
        if (installJob?.isActive == true) return

        val distro = _selectedDistro.value
        installJob = viewModelScope.launch {
            try {
                com.example.engine.AppLogManager.info(
                    com.example.model.LogCategory.SYSTEM,
                    "Install",
                    "${distro.name} indirmesi başlatılıyor (${distro.downloadUrl})..."
                )

                // 1. Download phase
                _installState.value = InstallState.Downloading(
                    distro = distro,
                    progress = 0.0f,
                    downloadedBytes = 0L,
                    totalBytes = distro.approxDownloadMb * 1024L * 1024L,
                    speedKbps = 0L
                )

                val archiveFile = downloadManager.downloadDistro(distro) { progress, downloaded, total, speed ->
                    _installState.value = InstallState.Downloading(
                        distro = distro,
                        progress = progress,
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        speedKbps = speed
                    )
                }

                com.example.engine.AppLogManager.info(
                    com.example.model.LogCategory.SYSTEM,
                    "Install",
                    "İndirme tamamlandı: ${archiveFile.name} (~${archiveFile.length() / (1024 * 1024)} MB). Arşiv açılıyor..."
                )

                // 2. Extract phase
                _installState.value = InstallState.Extracting(
                    distro = distro,
                    processedFiles = 0,
                    currentFileName = "Arşiv açılıyor..."
                )

                installer.extractAndSetup(
                    archiveFile = archiveFile,
                    distro = distro,
                    onProgress = { count, file ->
                        _installState.value = InstallState.Extracting(
                            distro = distro,
                            processedFiles = count,
                            currentFileName = file
                        )
                    },
                    onConfiguring = { step ->
                        com.example.engine.AppLogManager.info(
                            com.example.model.LogCategory.SYSTEM,
                            "Configuring",
                            step
                        )
                        _installState.value = InstallState.Configuring(
                            distro = distro,
                            currentStep = step
                        )
                    }
                )

                // 3. Mark Installed
                val rootfsSize = installer.getRootfsSizeMb()
                _installState.value = InstallState.Installed(
                    distro = distro,
                    rootfsPath = installer.rootfsDir.absolutePath,
                    installedDate = System.currentTimeMillis(),
                    totalSizeMb = rootfsSize
                )

                com.example.engine.AppLogManager.success(
                    com.example.model.LogCategory.SYSTEM,
                    "Install",
                    "${distro.name} başarıyla kuruldu (~${rootfsSize} MB)."
                )

                // Add welcome log to terminal
                _terminalLines.update {
                    it + TerminalOutputLine(
                        text = "Kurulum Başarılı: ${distro.name} ARM64 hazır.",
                        type = LineType.SUCCESS
                    )
                }

                // Automatically switch to terminal tab
                _activeTab.value = 2
                startTerminal("uname -a && cat /etc/os-release")

            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: "Kurulum sırasında beklenmeyen bir hata oluştu."
                val stack = e.stackTraceToString()
                com.example.engine.AppLogManager.error(
                    com.example.model.LogCategory.SYSTEM,
                    "InstallError",
                    errorMsg,
                    stack.take(1000)
                )
                _installState.value = InstallState.Error(
                    message = errorMsg,
                    details = stack.take(300)
                )
            }
        }
    }

    fun cancelInstallation() {
        installJob?.cancel()
        installJob = null
        _installState.value = InstallState.Idle
    }

    fun uninstallUbuntu() {
        viewModelScope.launch {
            stopTerminal()
            installer.uninstall()
            downloadManager.deleteDownloadedArchive(_selectedDistro.value)
            _installState.value = InstallState.Idle
            _terminalLines.value = listOf(
                TerminalOutputLine(
                    text = "Ubuntu rootfs ve önbellek tamamen temizlendi.",
                    type = LineType.SYSTEM
                )
            )
        }
    }

    fun startTerminal(initialCommand: String? = null) {
        viewModelScope.launch {
            runner.startSession(viewModelScope, initialCommand)
        }
    }

    fun stopTerminal() {
        runner.stopSession()
    }

    fun sendCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return
        if (trimmed == "clear" || trimmed == "cls") {
            clearTerminal()
        }
        viewModelScope.launch {
            if (!runner.isRunning) {
                runner.startSession(viewModelScope, trimmed)
            } else {
                runner.sendCommand(trimmed)
            }
        }
    }

    fun sendSpecialKey(key: String) {
        viewModelScope.launch {
            runner.sendSpecialKey(key)
        }
    }

    fun clearTerminal() {
        _terminalLines.value = emptyList()
    }

    fun checkPackageStatuses() {
        if (!installer.isInstalled()) return
        val rootfs = installer.rootfsDir

        // Auto-enforce nosnap pinning on existing rootfs
        try {
            val prefDir = File(rootfs, "etc/apt/preferences.d").apply { if (!exists()) mkdirs() }
            val noSnapFile = File(prefDir, "nosnap.pref")
            if (!noSnapFile.exists()) {
                noSnapFile.writeText(
                    """
                    Package: snapd
                    Pin: release *
                    Pin-Priority: -10

                    Package: snapd:*
                    Pin: release *
                    Pin-Priority: -10
                    """.trimIndent() + "\n"
                )
            }
        } catch (_: Exception) {}

        val newMap = mutableMapOf<String, PackageStatus>()
        for (pkg in PredefinedPackages.ALL) {
            val isInstalled = if (pkg.id == "firefox") {
                val ppaBin = File(rootfs, "usr/lib/firefox/firefox")
                val optBin = File(rootfs, "opt/firefox/firefox")
                val usrBin = File(rootfs, "usr/bin/firefox")
                ppaBin.exists() || optBin.exists() || (usrBin.exists() && !usrBin.readText().contains("snap"))
            } else if (pkg.id == "synaptic") {
                File(rootfs, "usr/sbin/synaptic").exists() || File(rootfs, "usr/bin/synaptic").exists()
            } else if (pkg.id == "box64") {
                File(rootfs, "usr/local/bin/box64").exists() || File(rootfs, "usr/bin/box64").exists()
            } else {
                File(rootfs, pkg.checkBinaryPath).exists()
            }
            newMap[pkg.id] = if (isInstalled) PackageStatus.INSTALLED else PackageStatus.NOT_INSTALLED
        }
        _packageStatuses.value = newMap
        _isPRootReady.value = runner.prootManager.isPRootInstalled
    }

    fun installPRootEngine() {
        viewModelScope.launch {
            _terminalLines.value = _terminalLines.value + TerminalOutputLine(
                text = ">>> PRoot ARM64 Sanallaştırma Motoru Hazırlanıyor...",
                type = LineType.SYSTEM
            )
            runner.prootManager.installPRoot { progress ->
                // Progress callback
            }
            _isPRootReady.value = runner.prootManager.isPRootInstalled
            _terminalLines.value = _terminalLines.value + TerminalOutputLine(
                text = if (runner.prootManager.isPRootInstalled) {
                    ">>> PRoot ARM64 motoru başarıyla etkinleştirildi! (UID 0 root yetkisi aktif)"
                } else {
                    ">>> PRoot motoru hazırlanırken bir sorun oluştu."
                },
                type = if (runner.prootManager.isPRootInstalled) LineType.SUCCESS else LineType.WARNING
            )
        }
    }

    fun installPackage(pkg: UbuntuPackage) {
        if (!installer.isInstalled()) return
        _packageStatuses.update { it + (pkg.id to PackageStatus.INSTALLING) }
        
        viewModelScope.launch {
            _activeTab.value = 2 // Switch to terminal so user can see live apt progress
            val aptCmd = if (pkg.id == "firefox") {
                // Ubuntu 22.04 snap bypass: add mozillateam PPA and set apt pinning so it installs real deb
                """
                echo ">>> Mozilla PPA ve Gerçek Firefox DEB Paketi Hazırlanıyor...";
                export DEBIAN_FRONTEND=noninteractive;
                apt-get update && apt-get install -y software-properties-common gpg wget;
                add-apt-repository -y ppa:mozillateam/ppa;
                printf 'Package: *\nPin: release o=LP-PPA-mozillateam\nPin-Priority: 1001\n' > /etc/apt/preferences.d/mozilla-firefox;
                printf 'Package: firefox*\nPin: release o=Ubuntu*\nPin-Priority: -1\n' >> /etc/apt/preferences.d/mozilla-firefox;
                apt-get update && apt-get install -y --allow-downgrades firefox;
                update-alternatives --install /usr/bin/x-www-browser x-www-browser /usr/bin/firefox 200;
                mkdir -p /root/Desktop;
                printf '[Desktop Entry]\nVersion=1.0\nType=Application\nName=Firefox Web Browser\nExec=firefox %%U\nIcon=firefox\nTerminal=false\nCategories=Network;WebBrowser;\n' > /root/Desktop/Firefox.desktop;
                chmod +x /root/Desktop/Firefox.desktop;
                echo ">>> Firefox başarıyla kuruldu ve masaüstü simgesi eklendi!"
                """.trimIndent().replace("\n", " ")
            } else if (pkg.id == "box64") {
                // Official Box64 Debian/Ubuntu ARM64 repo
                """
                echo ">>> Box64 (x86_64 Emülatörü) Kuruluyor...";
                export DEBIAN_FRONTEND=noninteractive;
                apt-get update && apt-get install -y wget gpg;
                wget -qO- https://ryanfortner.github.io/box64-debs/KEY.gpg | gpg --dearmor -o /etc/apt/trusted.gpg.d/box64-debs-archive-keyring.gpg 2>/dev/null;
                echo "deb [signed-by=/etc/apt/trusted.gpg.d/box64-debs-archive-keyring.gpg] https://ryanfortner.github.io/box64-debs/debian ./ " > /etc/apt/sources.list.d/box64.list;
                apt-get update && apt-get install -y box64-android;
                echo ">>> Box64 başarıyla kuruldu! x86_64 ikilileri çalıştırılabilir."
                """.trimIndent().replace("\n", " ")
            } else {
                "export DEBIAN_FRONTEND=noninteractive; apt-get update && apt-get install -y ${pkg.installPackageName}"
            }

            if (!runner.isRunning) {
                runner.startSession(viewModelScope, aptCmd)
            } else {
                sendCommand(aptCmd)
            }
        }
    }

    fun runAptUpdate() {
        if (!installer.isInstalled()) return
        viewModelScope.launch {
            _activeTab.value = 2
            sendCommand("apt-get update")
        }
    }

    fun runAptUpgrade() {
        if (!installer.isInstalled()) return
        viewModelScope.launch {
            _activeTab.value = 2
            sendCommand("export DEBIAN_FRONTEND=noninteractive; apt-get upgrade -y")
        }
    }

    fun runAptClean() {
        if (!installer.isInstalled()) return
        viewModelScope.launch {
            sendCommand("apt-get clean && rm -rf /var/lib/apt/lists/*")
            checkPackageStatuses()
        }
    }

    /**
     * One-click full desktop workstation installation:
     * Installs File Roller, Mousepad, LibreOffice, VLC, Media Codecs, and NetSurf
     */
    fun installFullDesktopSuite() {
        if (!installer.isInstalled()) return
        viewModelScope.launch {
            _activeTab.value = 2 // Go to terminal to see live installation
            val suiteCmd = """
            echo "==================================================";
            echo ">>> TAM MASAÜSTÜ LİNUX PAKETİ (DESKTOP SUITE) KURULUYOR";
            echo ">>> Paketler: Ofis, Dosya Arşivleyici, Metin Düzenleyici, VLC, Web";
            echo "==================================================";
            export DEBIAN_FRONTEND=noninteractive;
            apt-get update && apt-get install -y \
                mousepad \
                file-roller p7zip-full unrar-free unzip \
                netsurf-gtk \
                libreoffice-writer libreoffice-calc libreoffice-gtk3 \
                vlc fonts-dejavu fonts-liberation;
            desktopManager.prepareDesktopFiles();
            echo "";
            echo ">>> TEBRİKLER! Tam Masaüstü Linux Paketi Başarıyla Kuruldu.";
            echo ">>> Masaüstü sekmesine geçip oturumu başlatabilirsiniz.";
            """.trimIndent().replace("\n", " ")

            if (!runner.isRunning) {
                runner.startSession(viewModelScope, suiteCmd)
            } else {
                sendCommand(suiteCmd)
            }
        }
    }

    fun setDesktopResolution(resolution: DesktopResolution) {
        desktopManager.setResolution(resolution)
    }

    fun installDesktopEnvironment() {
        if (!installer.isInstalled()) return
        viewModelScope.launch {
            _activeTab.value = 2 // Switch to terminal to watch installation
            val cmd = desktopManager.getInstallDesktopCommand()
            sendCommand(cmd)
            desktopManager.refreshState()
        }
    }

    fun startDesktop() {
        if (!installer.isInstalled()) return
        viewModelScope.launch {
            desktopManager.markStarting()

            // 1. If terminal session is not running, start it first
            if (!runner.isRunning) {
                runner.startSession(viewModelScope)
                delay(1200)
            }

            val startCmd = desktopManager.getStartDesktopCommand()
            sendCommand(startCmd)
            
            // Wait actively until VNC/Websockify port 6080 is open and accepting connections
            val portReady = withContext(Dispatchers.IO) {
                var ready = false
                val start = System.currentTimeMillis()
                try { Thread.sleep(1200) } catch (_: Exception) {}
                while (!ready && (System.currentTimeMillis() - start < 18000)) {
                    try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress("127.0.0.1", 6080), 500)
                            ready = true
                        }
                    } catch (_: Exception) {
                        try { Thread.sleep(400) } catch (_: Exception) {}
                    }
                }
                ready
            }

            if (portReady) {
                delay(500)
                com.example.engine.AppLogManager.success(
                    com.example.model.LogCategory.DESKTOP,
                    "DesktopReady",
                    "XFCE4 & noVNC servisi hazır (Port 6080 aktif)."
                )
                desktopManager.markRunning()
            } else {
                com.example.engine.AppLogManager.error(
                    com.example.model.LogCategory.DESKTOP,
                    "DesktopTimeout",
                    "Port 6080 (noVNC/Websockify) zaman aşımına uğradı. Servis yanıt vermiyor.",
                    "Xvnc veya websockify süreci başlatılamamış olabilir. Hata loglarını kontrol edin."
                )
                fetchSystemLogs()
                desktopManager.markError("Masaüstü servisi (Port 6080) henüz hazır değil. Lütfen 'Yeniden Başlat' butonuna dokunun.")
            }
        }
    }

    fun stopDesktop() {
        viewModelScope.launch {
            val stopCmd = desktopManager.getStopDesktopCommand()
            sendCommand(stopCmd)
            desktopManager.markStopped()
        }
    }

    override fun onCleared() {
        super.onCleared()
        desktopManager.markStopped()
        runner.stopSession()
    }
}
