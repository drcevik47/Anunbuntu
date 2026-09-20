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

    private val _isTerminalRunning = MutableStateFlow(false)
    val isTerminalRunning: StateFlow<Boolean> = _isTerminalRunning.asStateFlow()

    private val _packageStatuses = MutableStateFlow<Map<String, PackageStatus>>(emptyMap())
    val packageStatuses: StateFlow<Map<String, PackageStatus>> = _packageStatuses.asStateFlow()

    private val _isPRootReady = MutableStateFlow(runner.prootManager.isPRootInstalled)
    val isPRootReady: StateFlow<Boolean> = _isPRootReady.asStateFlow()

    val desktopState: StateFlow<DesktopState> = desktopManager.desktopState
    val selectedResolution: StateFlow<DesktopResolution> = desktopManager.selectedResolution

    private val _activeTab = MutableStateFlow(0) // 0: Sistem, 1: Kurulum, 2: Terminal, 3: Paketler, 4: Masaüstü
    val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

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

    fun setActiveTab(index: Int) {
        _activeTab.value = index
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
                _installState.value = InstallState.Error(
                    message = e.localizedMessage ?: "Kurulum sırasında beklenmeyen bir hata oluştu.",
                    details = e.stackTraceToString().take(300)
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
            _isTerminalRunning.value = runner.isRunning
        }
    }

    fun stopTerminal() {
        runner.stopSession()
        _isTerminalRunning.value = false
    }

    fun sendCommand(command: String) {
        if (command.isBlank()) return
        viewModelScope.launch {
            if (!runner.isRunning) {
                runner.startSession(viewModelScope, command)
                _isTerminalRunning.value = runner.isRunning
            } else {
                runner.sendCommand(command)
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
        val newMap = mutableMapOf<String, PackageStatus>()
        for (pkg in PredefinedPackages.ALL) {
            val binary = File(rootfs, pkg.checkBinaryPath)
            newMap[pkg.id] = if (binary.exists()) PackageStatus.INSTALLED else PackageStatus.NOT_INSTALLED
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
            val aptCmd = "export DEBIAN_FRONTEND=noninteractive; apt-get update && apt-get install -y ${pkg.installPackageName}"
            if (!runner.isRunning) {
                runner.startSession(viewModelScope, aptCmd)
                _isTerminalRunning.value = runner.isRunning
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
            val startCmd = desktopManager.getStartDesktopCommand()
            sendCommand(startCmd)
            // Wait for VNC and Websockify sockets to bind
            delay(2500)
            desktopManager.markRunning()
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
