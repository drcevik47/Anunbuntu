package com.example.engine

import android.content.Context
import com.example.model.LineType
import com.example.model.TerminalOutputLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    private var _isRunning: Boolean = false
    val isRunning: Boolean get() = _isRunning

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
        if (_isRunning) {
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
            env["PROOT_LOADER"] = File(binDir, "loader").absolutePath
            env["PROOT_LOADER_32"] = File(binDir, "loader-m32").absolutePath
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
            val process = processBuilder.command(commandToRun).start()
            currentProcess = process
            _isRunning = true

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
                "export PS1='\\[\\033[01;32m\\]root@ubuntu-arm64\\[\\033[00m\\]:\\[\\033[01;34m\\]\\w\\[\\033[00m\\]# '",
                "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:${'$'}PATH",
                "export HOME=/root",
                "export USER=root",
                "alias ls='ls --color=auto'",
                "alias ll='ls -alF'",
                "cd /root"
            )
            for (cmd in initCommands) {
                processWriter?.write(cmd + "\n")
            }
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
                    _isRunning = false
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
            _isRunning = false
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
                        val line = lineAccumulator.toString()
                        lineAccumulator = StringBuilder()
                        _outputFlow.emit(TerminalOutputLine(text = line, type = type))
                    } else if (char != '\r') {
                        lineAccumulator.append(char)
                    }
                }
                if (lineAccumulator.length > 300) {
                    val line = lineAccumulator.toString()
                    lineAccumulator = StringBuilder()
                    _outputFlow.emit(TerminalOutputLine(text = line, type = type))
                }
            }
            if (lineAccumulator.isNotEmpty()) {
                _outputFlow.emit(TerminalOutputLine(text = lineAccumulator.toString(), type = type))
            }
        } catch (_: Exception) {
            // Stream closed
        }
    }

    private fun coroutineScopeActive(): Boolean = _isRunning

    suspend fun sendCommand(command: String) = withContext(Dispatchers.IO) {
        try {
            _outputFlow.emit(TerminalOutputLine(text = "# $command", type = LineType.STDIN))
            processWriter?.write(command + "\n")
            processWriter?.flush()
        } catch (e: Exception) {
            _outputFlow.emit(
                TerminalOutputLine(text = "Komut gönderilemedi: ${e.message}", type = LineType.STDERR)
            )
        }
    }

    suspend fun sendSpecialKey(key: String) = withContext(Dispatchers.IO) {
        try {
            when (key) {
                "CTRL_C" -> {
                    processWriter?.write("\u0003")
                    _outputFlow.emit(TerminalOutputLine(text = "^C", type = LineType.SYSTEM))
                }
                "CTRL_D" -> {
                    processWriter?.write("\u0004")
                    _outputFlow.emit(TerminalOutputLine(text = "^D", type = LineType.SYSTEM))
                }
                "CTRL_Z" -> {
                    processWriter?.write("\u001A")
                    _outputFlow.emit(TerminalOutputLine(text = "^Z", type = LineType.SYSTEM))
                }
                "TAB" -> {
                    processWriter?.write("\t")
                }
                "ESC" -> {
                    processWriter?.write("\u001B")
                }
                "UP" -> {
                    processWriter?.write("\u001B[A")
                }
                "DOWN" -> {
                    processWriter?.write("\u001B[B")
                }
            }
            processWriter?.flush()
        } catch (_: Exception) {
            // ignore
        }
    }

    fun stopSession() {
        _isRunning = false
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
