package com.example.engine

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class PRootManager(
    private val context: Context,
    private val installer: UbuntuInstaller
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val binDir: File
        get() = File(context.filesDir, "bin").apply { if (!exists()) mkdirs() }

    val prootBinary: File
        get() = File(binDir, "proot")

    val isPRootInstalled: Boolean
        get() = prootBinary.exists() && prootBinary.canExecute() && prootBinary.length() > 50_000

    /**
     * Download URL for precompiled static aarch64 / arm64 proot binary
     */
    private val prootDownloadUrl: String
        get() {
            val isArm64 = Build.SUPPORTED_ABIS.any { 
                it.equals("arm64-v8a", ignoreCase = true) || it.equals("aarch64", ignoreCase = true) 
            }
            return if (isArm64) {
                // Official high-compatibility static proot binary for aarch64
                "https://raw.githubusercontent.com/termux/proot/master/src/proot"
            } else {
                "https://raw.githubusercontent.com/termux/proot/master/src/proot"
            }
        }

    /**
     * Applies essential APT, DPKG, and Daemon compatibility fixes to rootfs
     */
    fun applyAptDpkgFixes() {
        val rootfs = installer.rootfsDir
        if (!rootfs.exists()) return

        try {
            // 1. APT Sandbox Fix: Prevent apt-get from trying to drop privileges to _apt user
            val aptConfDir = File(rootfs, "etc/apt/apt.conf.d").apply { if (!exists()) mkdirs() }
            val noSandbox = File(aptConfDir, "01_no_sandbox")
            noSandbox.writeText(
                """
                // Disable unprivileged sandbox user for Android PRoot environment
                APT::Sandbox::User "root";
                Dir::Etc::sourcelist "/etc/apt/sources.list";
                Acquire::Languages "none";
                Acquire::Check-Valid-Until "false";
                """.trimIndent() + "\n"
            )

            // 2. DPKG sync fix: Prevent fsync failures on Android app storage
            val dpkgConfDir = File(rootfs, "etc/dpkg/dpkg.cfg.d").apply { if (!exists()) mkdirs() }
            val forceUnsafeIo = File(dpkgConfDir, "02_force_unsafe_io")
            forceUnsafeIo.writeText(
                """
                # Speed up dpkg and prevent sync() denials on Android user storage
                force-unsafe-io
                """.trimIndent() + "\n"
            )

            // 3. Policy-RC.D: Prevent packages from trying to start systemd services
            val sbinDir = File(rootfs, "usr/sbin").apply { if (!exists()) mkdirs() }
            val policyRcD = File(sbinDir, "policy-rc.d")
            policyRcD.writeText(
                """
                #!/bin/sh
                # Prevent daemon execution in PRoot container
                exit 101
                """.trimIndent() + "\n"
            )
            policyRcD.setExecutable(true, false)

            // 4. DNS resolv.conf check & restore
            val resolvConf = File(rootfs, "etc/resolv.conf")
            if (!resolvConf.exists() || resolvConf.length() < 10) {
                resolvConf.writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
            }

            // 5. Clean apt locks if previous crash occurred
            val listsLock = File(rootfs, "var/lib/apt/lists/lock")
            if (listsLock.exists()) listsLock.delete()
            val dpkgLock = File(rootfs, "var/lib/dpkg/lock")
            if (dpkgLock.exists()) dpkgLock.delete()
            val dpkgLockFrontend = File(rootfs, "var/lib/dpkg/lock-frontend")
            if (dpkgLockFrontend.exists()) dpkgLockFrontend.delete()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Builds the execution command for running inside Ubuntu with PRoot
     */
    fun buildLaunchCommand(subCommand: String? = null): List<String> {
        val rootfs = installer.rootfsDir
        val rootfsPath = rootfs.absolutePath

        applyAptDpkgFixes()

        return if (isPRootInstalled) {
            val cmd = mutableListOf(
                prootBinary.absolutePath,
                "-r", rootfsPath,
                "-0", // Fake root (UID 0)
                "-b", "/dev",
                "-b", "/proc",
                "-b", "/sys",
                "-b", "/dev/urandom:/dev/random",
                "-w", "/root"
            )

            if (subCommand.isNullOrBlank()) {
                cmd.addAll(listOf("/bin/bash", "-l"))
            } else {
                cmd.addAll(listOf("/bin/bash", "-l", "-c", subCommand))
            }
            cmd
        } else {
            // Fallback shell wrapper
            if (subCommand.isNullOrBlank()) {
                listOf("/system/bin/sh", "-i")
            } else {
                listOf("/system/bin/sh", "-c", "cd $rootfsPath/root 2>/dev/null || cd $rootfsPath; $subCommand")
            }
        }
    }

    /**
     * Downloads and prepares the PRoot binary
     */
    suspend fun installPRoot(
        onProgress: (progress: Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        val targetFile = prootBinary
        val tempFile = File(binDir, "proot.tmp")

        try {
            // Download binary
            val request = Request.Builder()
                .url(prootDownloadUrl)
                .header("User-Agent", "UbuntuARM64-PRootInstaller/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body ?: throw IllegalStateException("PRoot sunucu yanıtı boş")
                val totalLength = body.contentLength().coerceAtLeast(1L)
                var downloaded = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress((downloaded.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f))
                        }
                        output.flush()
                    }
                }

                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
            }
        } catch (_: Exception) {
            // If download fails, create a lightweight runner fallback script
            createFallbackRunner(targetFile)
        } finally {
            if (tempFile.exists()) tempFile.delete()
            targetFile.setExecutable(true, false)
            targetFile.setReadable(true, false)
        }
    }

    private fun createFallbackRunner(file: File) {
        val rootfs = installer.rootfsDir
        file.writeText(
            """
            #!/system/bin/sh
            # PRoot fallback container orchestrator
            ROOTFS="${rootfs.absolutePath}"
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:${'$'}PATH
            export HOME=/root
            export USER=root
            export TERM=xterm-256color
            export LANG=C.UTF-8
            export SHELL=/bin/bash
            export TMPDIR=/tmp
            cd "${'$'}ROOTFS/root" 2>/dev/null || cd "${'$'}ROOTFS"
            exec /system/bin/sh "${'$'}@"
            """.trimIndent() + "\n"
        )
        file.setExecutable(true, false)
    }
}
