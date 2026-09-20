package com.example.engine

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

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

    val nativeLibDir: File?
        get() = context.applicationInfo?.nativeLibraryDir?.takeIf { it.isNotBlank() }?.let { File(it) }

    val nativeProot: File?
        get() = nativeLibDir?.let { File(it, "libproot.so") }

    val nativeLoader: File?
        get() = nativeLibDir?.let { File(it, "libloader.so") }

    val nativeLoader32: File?
        get() = nativeLibDir?.let { File(it, "libloader-m32.so") }

    val prootBinary: File
        get() = nativeProot?.takeIf { it.exists() && it.length() > 50_000 } ?: File(binDir, "proot")

    val loaderBinary: File
        get() = nativeLoader?.takeIf { it.exists() && it.length() > 5_000 } ?: File(binDir, "loader")

    val loader32Binary: File
        get() = nativeLoader32?.takeIf { it.exists() && it.length() > 2_000 } ?: File(binDir, "loader-m32")

    val isPRootInstalled: Boolean
        get() = (nativeProot?.let { it.exists() && it.length() > 50_000 } == true) ||
                (File(binDir, "proot").exists() && File(binDir, "proot").canExecute() && File(binDir, "proot").length() > 50_000)

    private val isArm64: Boolean
        get() = Build.SUPPORTED_ABIS.any { 
            it.equals("arm64-v8a", ignoreCase = true) || it.equals("aarch64", ignoreCase = true) 
        }

    /**
     * Download URL for precompiled static Android PRoot package
     */
    private val prootDownloadUrl: String
        get() {
            return if (isArm64) {
                "https://github.com/ahmed-alnassif/proot/releases/latest/download/proot-aarch64.zip"
            } else {
                "https://github.com/ahmed-alnassif/proot/releases/latest/download/proot-x86_64.zip"
            }
        }

    /**
     * Extracts PRoot and its loaders from APK bundled assets or downloads if needed
     */
    @Synchronized
    fun ensurePRootInstalled(): Boolean {
        if (nativeProot?.let { it.exists() && it.length() > 50_000 } == true) {
            return true
        }

        if (isPRootInstalled && loaderBinary.exists()) {
            return true
        }

        // 1. Try extracting from bundled assets first (Instant, offline, zero-fail)
        try {
            val assetName = if (isArm64) "proot/proot-aarch64.zip" else "proot/proot-x86_64.zip"
            context.assets.open(assetName).use { inputStream ->
                if (extractZipStream(inputStream)) {
                    if (isPRootInstalled) return true
                }
            }
        } catch (_: Exception) {
            // Asset might not be present or failed, fall back
        }

        return isPRootInstalled
    }

    /**
     * Unzips proot, loader, loader-m32 from an input stream into binDir
     */
    private fun extractZipStream(inputStream: InputStream): Boolean {
        return try {
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name.substringAfterLast("/")
                    if (entryName.isNotEmpty() && !entry.isDirectory) {
                        val outFile = File(binDir, entryName)
                        if (outFile.exists()) outFile.delete()
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                        outFile.setExecutable(true, false)
                        outFile.setReadable(true, false)
                    }
                    entry = zis.nextEntry
                }
            }
            prootBinary.setExecutable(true, false)
            loaderBinary.setExecutable(true, false)
            loader32Binary.setExecutable(true, false)
            isPRootInstalled
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Downloads and prepares the PRoot package if not bundled
     */
    suspend fun installPRoot(
        onProgress: (progress: Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        // First try local asset extraction
        if (ensurePRootInstalled()) {
            onProgress(1.0f)
            return@withContext
        }

        val tempZip = File(binDir, "proot_download.tmp")
        try {
            onProgress(0.1f)
            val request = Request.Builder()
                .url(prootDownloadUrl)
                .header("User-Agent", "UbuntuARM64-PRoot/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body ?: throw IllegalStateException("PRoot sunucu yanıtı boş")
                val totalLength = body.contentLength().coerceAtLeast(1L)
                var downloaded = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempZip).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(0.1f + (downloaded.toFloat() / totalLength.toFloat()) * 0.7f)
                        }
                        output.flush()
                    }
                }

                tempZip.inputStream().use {
                    extractZipStream(it)
                }
                onProgress(1.0f)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (tempZip.exists()) tempZip.delete()
        }

        // Final verification
        ensurePRootInstalled()
    }

    /**
     * Recursively grants executable and read permissions to Linux binaries in rootfs
     */
    fun fixPermissions() {
        val rootfs = installer.rootfsDir
        if (!rootfs.exists()) return

        // 1. Ensure usr/bin, usr/sbin, debconf, and dpkg scripts are executable
        val binaryFolders = listOf(
            File(rootfs, "bin"),
            File(rootfs, "usr/bin"),
            File(rootfs, "sbin"),
            File(rootfs, "usr/sbin"),
            File(rootfs, "usr/lib/apt/methods"),
            File(rootfs, "usr/libexec"),
            File(rootfs, "usr/lib/dpkg"),
            File(rootfs, "usr/share/debconf"),
            File(rootfs, "var/lib/dpkg/info"),
            File(rootfs, "var/lib/dpkg/tmp.ci"),
            File(rootfs, "etc/cron.daily"),
            File(rootfs, "etc/cron.hourly"),
            File(rootfs, "etc/network")
        )

        for (folder in binaryFolders) {
            if (folder.exists()) {
                folder.walkTopDown().maxDepth(3).forEach { file ->
                    try {
                        file.setReadable(true, false)
                        if (!file.isDirectory) {
                            file.setExecutable(true, false)
                        } else {
                            file.setExecutable(true, false) // folders need x to traverse
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        // Specific guarantee for debconf frontend
        val debconfFrontend = File(rootfs, "usr/share/debconf/frontend")
        if (debconfFrontend.exists()) {
            debconfFrontend.setReadable(true, false)
            debconfFrontend.setExecutable(true, false)
        }

        // 2. Ensure dynamic linker /lib/ld-linux-aarch64.so.1 exists and is accessible
        ensureDynamicLinker()
    }

    /**
     * Ubuntu 24.04 uses usrmerge (/bin -> usr/bin, /lib -> usr/lib).
     * If symbolic links fail on certain Android filesystems or paths,
     * this guarantees that /lib/ld-linux-aarch64.so.1 and /bin/bash can be resolved.
     */
    private fun ensureDynamicLinker() {
        val rootfs = installer.rootfsDir
        try {
            // Find ld-linux-aarch64.so.1
            val possibleLinkers = listOf(
                File(rootfs, "usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1"),
                File(rootfs, "usr/lib/ld-linux-aarch64.so.1"),
                File(rootfs, "lib/aarch64-linux-gnu/ld-linux-aarch64.so.1"),
                File(rootfs, "lib/ld-linux-aarch64.so.1")
            )
            val realLinker = possibleLinkers.firstOrNull { it.exists() && it.length() > 10_000 }

            if (realLinker != null) {
                realLinker.setReadable(true, false)
                realLinker.setExecutable(true, false)

                // Ensure /lib/ld-linux-aarch64.so.1 exists
                val targetLibDir = File(rootfs, "lib").apply { if (!exists()) mkdirs() }
                val targetLinker = File(targetLibDir, "ld-linux-aarch64.so.1")
                if (!targetLinker.exists() || targetLinker.length() == 0L) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            java.nio.file.Files.createSymbolicLink(
                                targetLinker.toPath(),
                                java.nio.file.Paths.get("usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1")
                            )
                        }
                    } catch (_: Exception) {
                        // If symlink creation fails, copy the linker directly
                        realLinker.copyTo(targetLinker, overwrite = true)
                    }
                }
                targetLinker.setReadable(true, false)
                targetLinker.setExecutable(true, false)
            }

            // Ensure /bin/sh and /bin/bash exist
            val usrBinBash = File(rootfs, "usr/bin/bash")
            val binDir = File(rootfs, "bin").apply { if (!exists()) mkdirs() }
            val binBash = File(binDir, "bash")
            val binSh = File(binDir, "sh")

            if (usrBinBash.exists()) {
                usrBinBash.setReadable(true, false)
                usrBinBash.setExecutable(true, false)
                if (!binBash.exists() || binBash.length() == 0L) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            java.nio.file.Files.createSymbolicLink(
                                binBash.toPath(),
                                java.nio.file.Paths.get("usr/bin/bash")
                            )
                        }
                    } catch (_: Exception) {
                        usrBinBash.copyTo(binBash, overwrite = true)
                    }
                }
                binBash.setReadable(true, false)
                binBash.setExecutable(true, false)
            }

            val usrBinSh = File(rootfs, "usr/bin/sh")
            if (usrBinSh.exists() && (!binSh.exists() || binSh.length() == 0L)) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        java.nio.file.Files.createSymbolicLink(
                            binSh.toPath(),
                            java.nio.file.Paths.get("usr/bin/sh")
                        )
                    }
                } catch (_: Exception) {
                    usrBinSh.copyTo(binSh, overwrite = true)
                }
                binSh.setReadable(true, false)
                binSh.setExecutable(true, false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
                Acquire::Retries "3";
                Acquire::http::Timeout "30";
                Acquire::https::Timeout "30";
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
            policyRcD.setReadable(true, false)

            // 4. DNS resolv.conf check & restore
            val resolvConf = File(rootfs, "etc/resolv.conf")
            if (!resolvConf.exists() || resolvConf.length() < 10) {
                resolvConf.writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
            }

            // 5. Clean apt/dpkg locks if previous crash occurred
            val locks = listOf(
                File(rootfs, "var/lib/apt/lists/lock"),
                File(rootfs, "var/lib/dpkg/lock"),
                File(rootfs, "var/lib/dpkg/lock-frontend"),
                File(rootfs, "var/cache/apt/archives/lock")
            )
            locks.forEach { if (it.exists()) it.delete() }

            // 6. Ensure noninteractive environment and debconf defaults
            val etcEnv = File(rootfs, "etc/environment")
            etcEnv.writeText(
                """
                PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
                DEBIAN_FRONTEND="noninteractive"
                DEBCONF_NONINTERACTIVE_SEEN="true"
                LANG="C.UTF-8"
                LC_ALL="C.UTF-8"
                """.trimIndent() + "\n"
            )

            // Remove bad debconf apt config if exists (caused syntax error)
            val badDebconf = File(aptConfDir, "00_debconf")
            if (badDebconf.exists()) {
                badDebconf.delete()
            }

            val dpkgOptions = File(aptConfDir, "00_dpkg_options")
            dpkgOptions.writeText(
                """
                DPkg::Options {
                   "--force-confdef";
                   "--force-confold";
                };
                """.trimIndent() + "\n"
            )

            val profileDir = File(rootfs, "etc/profile.d").apply { if (!exists()) mkdirs() }
            val noninteractiveScript = File(profileDir, "00_noninteractive.sh")
            noninteractiveScript.writeText(
                """
                export DEBIAN_FRONTEND=noninteractive
                export DEBCONF_NONINTERACTIVE_SEEN=true
                """.trimIndent() + "\n"
            )
            noninteractiveScript.setExecutable(true, false)
            noninteractiveScript.setReadable(true, false)

            // 7. Ensure required directories exist
            File(rootfs, "tmp").apply { if (!exists()) mkdirs() }
            File(rootfs, "dev/shm").apply { if (!exists()) mkdirs() }

            // 8. Fix permissions of binaries and debconf/dpkg scripts
            fixPermissions()

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

        ensurePRootInstalled()
        applyAptDpkgFixes()

        val shell = if (File(rootfs, "bin/bash").exists()) "/bin/bash" else "/bin/sh"

        return if (isPRootInstalled) {
            val cmd = mutableListOf(
                prootBinary.absolutePath,
                "--link2symlink",
                "-0", // Fake root (UID 0)
                "-r", rootfsPath,
                "-b", "/dev",
                "-b", "/proc",
                "-b", "/sys",
                "-b", "$rootfsPath/tmp:/dev/shm",
                "-w", "/root"
            )

            if (subCommand.isNullOrBlank()) {
                cmd.addAll(listOf(shell, "-l"))
            } else {
                cmd.addAll(listOf(shell, "-l", "-c", subCommand))
            }
            cmd
        } else {
            // Extreme fallback if PRoot binary is not yet available
            if (subCommand.isNullOrBlank()) {
                listOf("/system/bin/sh", "-i")
            } else {
                listOf("/system/bin/sh", "-c", "cd $rootfsPath/root 2>/dev/null || cd $rootfsPath; $subCommand")
            }
        }
    }
}

