package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.UbuntuHeader
import com.example.viewmodel.UbuntuViewModel

@Composable
fun MainScreen(
    viewModel: UbuntuViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val activeTab by viewModel.activeTab.collectAsState()
    val systemInfo by viewModel.systemInfo.collectAsState()
    val installState by viewModel.installState.collectAsState()
    val selectedDistro by viewModel.selectedDistro.collectAsState()
    val terminalLines by viewModel.terminalLines.collectAsState()
    val isTerminalRunning by viewModel.isTerminalRunning.collectAsState()
    val isCommandExecuting by viewModel.isCommandExecuting.collectAsState()
    val currentWorkingDir by viewModel.currentWorkingDir.collectAsState()
    val packageStatuses by viewModel.packageStatuses.collectAsState()
    val isPRootReady by viewModel.isPRootReady.collectAsState()
    val desktopState by viewModel.desktopState.collectAsState()
    val selectedResolution by viewModel.selectedResolution.collectAsState()

    var isDesktopFullscreen by remember { mutableStateOf(false) }

    // Reset fullscreen when switching away from desktop tab
    LaunchedEffect(activeTab) {
        if (activeTab != 4) {
            isDesktopFullscreen = false
        }
    }

    // Manage immersive full-screen system bars when Desktop is fullscreen
    DisposableEffect(activeTab, isDesktopFullscreen) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (activeTab == 4 && isDesktopFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            val window = activity?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Android back button exits fullscreen if active
    BackHandler(enabled = activeTab == 4 && isDesktopFullscreen) {
        isDesktopFullscreen = false
    }

    val showHeader = !(activeTab == 4 && isDesktopFullscreen)

    Scaffold(
        topBar = {
            if (showHeader) {
                UbuntuHeader(
                    activeTab = activeTab,
                    onTabSelected = { viewModel.setActiveTab(it) },
                    installState = installState
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (showHeader) Modifier.padding(innerPadding) else Modifier)
        ) {
            when (activeTab) {
                0 -> SystemCheckScreen(
                    systemInfo = systemInfo,
                    onRefresh = { viewModel.refreshSystemInfo() },
                    onProceedToInstall = { viewModel.setActiveTab(1) }
                )
                1 -> InstallWizardScreen(
                    installState = installState,
                    selectedDistro = selectedDistro,
                    onSelectDistro = { viewModel.selectDistro(it) },
                    onStartInstall = { viewModel.startInstallation() },
                    onCancelInstall = { viewModel.cancelInstallation() },
                    onUninstall = { viewModel.uninstallUbuntu() },
                    onOpenTerminal = { viewModel.setActiveTab(2) }
                )
                2 -> TerminalScreen(
                    terminalLines = terminalLines,
                    isTerminalRunning = isTerminalRunning,
                    isCommandExecuting = isCommandExecuting,
                    currentWorkingDir = currentWorkingDir,
                    installState = installState,
                    onSendCommand = { viewModel.sendCommand(it) },
                    onSendSpecialKey = { viewModel.sendSpecialKey(it) },
                    onStartTerminal = { viewModel.startTerminal() },
                    onStopTerminal = { viewModel.stopTerminal() },
                    onClearTerminal = { viewModel.clearTerminal() },
                    onGoToInstall = { viewModel.setActiveTab(1) }
                )
                3 -> PackageManagerScreen(
                    installState = installState,
                    packageStatuses = packageStatuses,
                    isPRootReady = isPRootReady,
                    onRefreshStatuses = { viewModel.checkPackageStatuses() },
                    onInstallPRoot = { viewModel.installPRootEngine() },
                    onInstallPackage = { viewModel.installPackage(it) },
                    onRunAptUpdate = { viewModel.runAptUpdate() },
                    onRunAptUpgrade = { viewModel.runAptUpgrade() },
                    onRunAptClean = { viewModel.runAptClean() },
                    onGoToTerminal = { viewModel.setActiveTab(2) }
                )
                4 -> DesktopScreen(
                    installState = installState,
                    desktopState = desktopState,
                    selectedResolution = selectedResolution,
                    isFullscreen = isDesktopFullscreen,
                    onToggleFullscreen = { isDesktopFullscreen = it },
                    onSelectResolution = { viewModel.setDesktopResolution(it) },
                    onInstallDesktop = { viewModel.installDesktopEnvironment() },
                    onStartDesktop = { viewModel.startDesktop() },
                    onStopDesktop = { viewModel.stopDesktop() },
                    onGoToInstall = { viewModel.setActiveTab(1) }
                )
            }
        }
    }
}
