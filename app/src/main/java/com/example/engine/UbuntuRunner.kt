package com.example.engine

import android.content.Context
import com.example.model.LineType
import com.example.model.TerminalOutputLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.InputStream
import java.io.OutputStreamWriter

class UbuntuRunner(
    private val context: Context,
    private val installer: UbuntuInstaller,
    val prootManager: PRootManager = PRootManager(context, installer)
) {

    private var currentProcess: Process? = null
    private var processWriter: BufferedWriter? = null
    private var readJob: Job? = null

    private val _outputFlow = MutableSharedFlow<TerminalOutputLine>(replay = 50)
    val outputFlow: SharedFlow<TerminalOutputLine> = _outputFlow.asSharedFlow()

    private val _isRunningFlow = MutableStateFlow(false)
    val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()
    val isRunning: Boolean get() = _isRunningFlow.value

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _currentWorkingDir = MutableStateFlow("~")
    val currentWorkingDir: StateFlow<String> = _currentWorkingDir.asStateFlow()

    companion object {
        private const val SENTINEL_PREFIX = "___UBUNTU_CMD_EOF_"
    }

    private val ansiRegex = Regex("\u001B\\[[;?0-9]*[a-zA-Z]|\u001B\\([a-zA-Z]")

    private fun cleanAnsi(text: String): String {
        return text.replace(ansiRegex, "")
    }

    /**
     * Prepares helper scripts and execution environment
     */
    fun prepareEnvironment(): File {
        val binDir = File(context.filesDir, "bin")
        if (!binDir.exists()) binDir.mkdirs()

        // Create launch script that sets up chroot/proot or sandbox environment
        val rootfs = installer.rootfsDir
        val launcherScript = File(binDir, "launch_ubuntu.sh")
        launcherScript.writeText(
            """
            #!/system/bin/sh
            ROOTFS="${rootfs.absolutePath}"
            export ROOTFS
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:${'$'}PATH
            export HOME=/root
            export USER=root
            export TERM=xterm-256color
            export LANG=C.UTF-8
            export SHELL=/bin/bash
            export TMPDIR=/tmp
            
            # Check if rootfs exists
            if [ ! -d "${'$'}ROOTFS" ]; then
                echo "HATA: Ubuntu rootfs bulunamadı (${'$'}ROOTFS)."
                exit 1
            fi

            # Check if su / root is available
            if command -v su >/dev/null 2>&1; then
                # Rooted device detected - use chroot
                exec su -c "chroot ${'$'}ROOTFS /bin/bash -l"
            fi

            # Check for proot executable
            PROOT_BIN="${'$'}ROOTFS/../bin/proot"
            if [ -x "${'$'}PROOT_BIN" ]; then
                exec "${'$'}PROOT_BIN" -r "${'$'}ROOTFS" -0 -b /dev -b /proc -b /sys -w /root /bin/bash -l
            fi

            # Fallback: direct dynamic linker or chroot attempt
            if [ -f "${'$'}ROOTFS/bin/bash" ]; then
                cd "${'$'}ROOTFS/root" 2>/dev/null || cd "${'$'}ROOTFS"
                echo "=================================================="
                echo "Ubuntu ARM64 Ortamı Başlatılıyor..."
                echo "RootFS: ${'$'}ROOTFS"
                echo "=================================================="
                exec /system/bin/sh
            else
                echo "HATA: ${'$'}ROOTFS/bin/bash bulunamadı."
                exit 1
            fi
            """.trimIndent() + "\n"
        )
        launcherScript.setExecutable(true, false)
        return launcherScript
    }

    suspend fun startSession(
        coroutineScope: CoroutineScope,
        initialCommand: String? = null
    ) = withContext(Dispatchers.IO) {
        if (isRunning) {
            stopSession()
        }

        val rootfs = installer.rootfsDir
        if (!installer.isInstalled()) {
            _outputFlow.emit(
                TerminalOutputLine(
                    text = "UYARI: Ubuntu ARM64 henüz kurulmamış! Lütfen önce 'Kurulum' sekmesinden Ubuntu'yu indirin.",
                    type = LineType.WARNING
                )
            )
            return@withContext
        }

        prootManager.ensurePRootInstalled()
        prootManager.applyAptDpkgFixes()

        val modeText = if (prootManager.isPRootInstalled) {
            ">>> Ubuntu ARM64 PRoot Sanallaştırma Motoru Başlatıldı (UID 0 root emülasyonu aktif)"
        } else {
            ">>> Ubuntu ARM64 Konsolu Başlatılıyor... (PRoot modülü hazır)"
        }

        _outputFlow.emit(
            TerminalOutputLine(
                text = modeText,
                type = LineType.SYSTEM
            )
        )

        try {
            val processBuilder = ProcessBuilder()
            val binDir = prootManager.binDir
            val prootTmp = File(context.cacheDir, "proot_tmp").apply { if (!exists()) mkdirs() }
            
            // Environment variables for PRoot host and container
            val env = processBuilder.environment()
            env.remove("LD_PRELOAD")
            env["PROOT_TMP_DIR"] = prootTmp.absolutePath
            if (prootManager.loaderBinary.exists()) {
                env["PROOT_LOADER"] = prootManager.loaderBinary.absolutePath
            }
            if (prootManager.loader32Binary.exists()) {
                env["PROOT_LOADER_32"] = prootManager.loader32Binary.absolutePath
            }
            env["HOME"] = "/root"
            env["USER"] = "root"
            env["LOGNAME"] = "root"
            env["TERM"] = "xterm-256color"
            env["LANG"] = "C.UTF-8"
            env["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            env["DEBIAN_FRONTEND"] = "noninteractive"

            processBuilder.directory(rootfs)
            processBuilder.redirectErrorStream(false)

            val commandToRun = prootManager.buildLaunchCommand(subCommand = null)
            val process = try {
                processBuilder.command(commandToRun).start()
            } catch (pErr: Exception) {
                _outputFlow.emit(
                    TerminalOutputLine(
                        text = "Uyarı: PRoot başlatılırken hata oluştu (${pErr.localizedMessage}). Yedek Linux kabuk köprüsüne geçiliyor...\n",
                        type = LineType.WARNING
                    )
                )
                val fallbackPb = ProcessBuilder("/system/bin/sh", "-i")
                val fEnv = fallbackPb.environment()
                fEnv["PATH"] = "${rootfs.absolutePath}/bin:${rootfs.absolutePath}/usr/bin:/system/bin:/system/xbin"
                fEnv["HOME"] = "${rootfs.absolutePath}/root"
                fEnv["ROOTFS"] = rootfs.absolutePath
                fEnv["TERM"] = "xterm-256color"
                fEnv["LANG"] = "C.UTF-8"
                fallbackPb.directory(rootfs)
                fallbackPb.redirectErrorStream(false)
                fallbackPb.start()
            }
            currentProcess = process
            _isRunningFlow.value = true

            processWriter = BufferedWriter(OutputStreamWriter(process.outputStream))

            // Initial banner
            _outputFlow.emit(
                TerminalOutputLine(
                    text = "Ubuntu ARM64 (Linux container on Android)\nRootFS: ${rootfs.name}\nPRoot emülasyonu & APT/DPKG yamaları devrede. Çıkmak için 'exit' veya Ctrl+D tuşlayın.\n",
                    type = LineType.SUCCESS
                )
            )

            // Setup prompt & environment inside the container
            val initCommands = listOf(
                "rm -f /etc/apt/apt.conf.d/00_debconf 2>/dev/null",
                "export DEBIAN_FRONTEND=noninteractive",
                "export DEBCONF_NONINTERACTIVE_SEEN=true",
                "export PS1='root@ubuntu-arm64:\\w# '",
                "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:${'$'}PATH",
                "export HOME=/root",
                "export USER=root",
                "alias ls='ls --color=auto'",
                "alias ll='ls -alF'",
                "chmod -R 755 /usr/share/debconf /var/lib/dpkg/info 2>/dev/null",
                "cd /root"
            )
            for (cmd in initCommands) {
                processWriter?.write(cmd + "\n")
            }
            // Signal shell initialization complete
            processWriter?.write("echo \"${SENTINEL_PREFIX}:0:/root\"\n")
            processWriter?.flush()

            if (!initialCommand.isNullOrBlank()) {
                sendCommand(initialCommand)
            }

            // Launch output readers
            readJob = coroutineScope.launch(Dispatchers.IO) {
                launch { readStream(process.inputStream, LineType.STDOUT) }
                launch { readStream(process.errorStream, LineType.STDERR) }

                try {
                    val exitCode = process.waitFor()
                    _isRunningFlow.value = false
                    _isExecuting.value = false
                    AppLogManager.warn(
                        com.example.model.LogCategory.PROOT,
                        "ProcessExit",
                        "Ubuntu PRoot süreci sonlandı. Çıkış Kodu: $exitCode",
                        if (exitCode == 137) "Kod 137 genellikle SIGKILL veya bellek/alt süreç sınırından kaynaklanır." else null
                    )
                    _outputFlow.emit(
                        TerminalOutputLine(
                            text = "\n[İşlem sonlandı (Çıkış Kodu: $exitCode)]",
                            type = LineType.SYSTEM
                        )
                    )
                } catch (_: InterruptedException) {
                    // Normal stop
                }
            }
        } catch (e: Exception) {
            _isRunningFlow.value = false
            _isExecuting.value = false
            AppLogManager.error(com.example.model.LogCategory.PROOT, "StartFail", "Konsol başlatma hatası: ${e.localizedMessage}")
            _outputFlow.emit(
                TerminalOutputLine(
                    text = "Konsol başlatma hatası: ${e.localizedMessage}",
                    type = LineType.STDERR
                )
            )
        }
    }

    private suspend fun readStream(stream: InputStream, type: LineType) = withContext(Dispatchers.IO) {
        val reader = stream.bufferedReader()
        val buffer = CharArray(1024)
        var lineAccumulator = StringBuilder()

        try {
            while (coroutineScopeActive()) {
                val read = reader.read(buffer)
                if (read == -1) break
                val chunk = String(buffer, 0, read)
                for (char in chunk) {
                    if (char == '\n') {
                        val rawLine = lineAccumulator.toString()
                        lineAccumulator = StringBuilder()
                        val line = cleanAnsi(rawLine).trimEnd('\r')
                        handleParsedLine(line, type)
                    } else if (char != '\r') {
                        lineAccumulator.append(char)
                    }
                }

                // If no more bytes immediately available in stream, check if a prompt or partial interactive line arrived
                if (!reader.ready() && lineAccumulator.isNotEmpty()) {
                    val rawLine = lineAccumulator.toString()
                    val line = cleanAnsi(rawLine).trimEnd('\r')
                    if (line.contains(SENTINEL_PREFIX)) {
                        lineAccumulator = StringBuilder()
                        handleParsedLine(line, type)
                    } else if (line.isNotEmpty()) {
                        lineAccumulator = StringBuilder()
                        _outputFlow.emit(TerminalOutputLine(text = line, type = type))
                        // Detect interactive prompt or question waiting for user input
                        if (line.contains("#") || line.contains("$") || line.contains("root@") ||
                            line.contains("[Y/n]", ignoreCase = true) || line.contains("[y/N]", ignoreCase = true) ||
                            line.contains("password", ignoreCase = true) || line.endsWith("? ") || line.endsWith(": ")
                        ) {
                            _isExecuting.value = false
                        }
                    }
                }
            }

            if (lineAccumulator.isNotEmpty()) {
                val line = cleanAnsi(lineAccumulator.toString()).trimEnd('\r')
                if (line.contains(SENTINEL_PREFIX)) {
                    handleParsedLine(line, type)
                } else if (line.isNotEmpty()) {
                    _outputFlow.emit(TerminalOutputLine(text = line, type = type))
                }
            }
        } catch (_: Exception) {
            // Stream closed
        } finally {
            _isExecuting.value = false
        }
    }

    private suspend fun handleParsedLine(line: String, type: LineType) {
        if (line.contains(SENTINEL_PREFIX)) {
            _isExecuting.value = false

            val before = line.substringBefore(SENTINEL_PREFIX).trim()
            if (before.isNotEmpty()) {
                _outputFlow.emit(TerminalOutputLine(text = before, type = type))
            }

            val after = line.substringAfter(SENTINEL_PREFIX).trim().removePrefix(":")
            val parts = after.split(":")
            val exitCode = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
            val cwd = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() } ?: "/root"

            _currentWorkingDir.value = if (cwd == "/root") "~" else cwd

            if (exitCode != 0) {
                AppLogManager.warn(
                    com.example.model.LogCategory.PROOT,
                    "CommandExit",
                    "Komut çıkış kodu: $exitCode, dizin: $cwd"
                )
                _outputFlow.emit(
                    TerminalOutputLine(
                        text = "[Komut $exitCode hata kodu ile sonlandı]",
                        type = LineType.WARNING
                    )
                )
            }
        } else if (line.isNotEmpty()) {
            if (type == LineType.STDERR) {
                AppLogManager.warn(com.example.model.LogCategory.PROOT, "STDERR", line)
            }
            _outputFlow.emit(TerminalOutputLine(text = line, type = type))
        }
    }

    private fun coroutineScopeActive(): Boolean = _isRunningFlow.value

    suspend fun sendCommand(command: String) = withContext(Dispatchers.IO) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return@withContext

        try {
            _isExecuting.value = true
            AppLogManager.info(com.example.model.LogCategory.PROOT, "Command", "# $trimmed")
            _outputFlow.emit(TerminalOutputLine(text = "# $trimmed", type = LineType.STDIN))

            // Append double newline and unique sentinel echo:
            // This guarantees that even if the command doesn't output a trailing newline,
            // the sentinel appears on its own line and signals completion along with exit code and pwd.
            val sentinelCmd = "\n\necho \"${SENTINEL_PREFIX}:\${'$'}?:\${'$'}(pwd)\"\n"
            processWriter?.write(trimmed + sentinelCmd)
            processWriter?.flush()
        } catch (e: Exception) {
            _isExecuting.value = false
            _outputFlow.emit(
                TerminalOutputLine(text = "Komut gönderilemedi: ${e.message}", type = LineType.STDERR)
            )
        }
    }

    suspend fun sendSpecialKey(key: String) = withContext(Dispatchers.IO) {
        try {
            when (key) {
                "CTRL_C" -> {
                    try {
                        processWriter?.write("\u0003\n")
                        processWriter?.flush()
                    } catch (_: Exception) {}

                    // Try to send SIGINT to the root process via Android kill
                    currentProcess?.let { proc ->
                        try {
                            val pid = getProcessPid(proc)
                            if (pid > 0) {
                                Runtime.getRuntime().exec(arrayOf("/system/bin/kill", "-INT", pid.toString()))
                            }
                        } catch (_: Exception) {}
                    }

                    _isExecuting.value = false
                    _outputFlow.emit(TerminalOutputLine(text = "^C [Komut durduruldu]", type = LineType.SYSTEM))
                }
                "CTRL_D" -> {
                    processWriter?.write("\u0004")
                    processWriter?.flush()
                    _outputFlow.emit(TerminalOutputLine(text = "^D", type = LineType.SYSTEM))
                }
                "CTRL_Z" -> {
                    processWriter?.write("\u001A")
                    processWriter?.flush()
                    _outputFlow.emit(TerminalOutputLine(text = "^Z", type = LineType.SYSTEM))
                }
                "TAB" -> {
                    processWriter?.write("\t")
                    processWriter?.flush()
                }
                "ESC" -> {
                    processWriter?.write("\u001B")
                    processWriter?.flush()
                }
                "UP" -> {
                    processWriter?.write("\u001B[A")
                    processWriter?.flush()
                }
                "DOWN" -> {
                    processWriter?.write("\u001B[B")
                    processWriter?.flush()
                }
            }
        } catch (_: Exception) {
            // ignore
        }
    }

    private fun getProcessPid(process: Process): Int {
        return try {
            val pidField = process.javaClass.getDeclaredField("pid")
            pidField.isAccessible = true
            pidField.getInt(process)
        } catch (_: Exception) {
            -1
        }
    }

    fun stopSession() {
        _isRunningFlow.value = false
        _isExecuting.value = false
        readJob?.cancel()
        try {
            processWriter?.write("exit\n")
            processWriter?.flush()
        } catch (_: Exception) {}

        try {
            currentProcess?.destroy()
        } catch (_: Exception) {}

        currentProcess = null
        processWriter = null
    }
}
