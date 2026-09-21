package com.example.engine

import android.content.Context
import android.os.Build
import com.example.model.UbuntuDistro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths

class UbuntuInstaller(private val context: Context) {

    val rootfsDir: File
        get() = File(context.filesDir, "ubuntu_rootfs")

    private val markerFile: File
        get() = File(rootfsDir, ".installed_info")

    fun isInstalled(): Boolean {
        if (!rootfsDir.exists() || !markerFile.exists()) return false
        val binBash = File(rootfsDir, "bin/bash")
        val binSh = File(rootfsDir, "bin/sh")
        val usrBinBash = File(rootfsDir, "usr/bin/bash")
        return binBash.exists() || binSh.exists() || usrBinBash.exists()
    }

    fun getInstalledDistroName(): String? {
        if (!isInstalled()) return null
        return try {
            val osRelease = File(rootfsDir, "etc/os-release")
            if (osRelease.exists()) {
                osRelease.readLines().firstOrNull { it.startsWith("PRETTY_NAME=") }
                    ?.removePrefix("PRETTY_NAME=")
                    ?.trim('"', '\'') ?: "Ubuntu ARM64"
            } else {
                markerFile.readText().lines().firstOrNull() ?: "Ubuntu ARM64"
            }
        } catch (_: Exception) {
            "Ubuntu ARM64"
        }
    }

    fun getRootfsSizeMb(): Long {
        return try {
            calculateDirectorySize(rootfsDir) / (1024 * 1024)
        } catch (_: Exception) {
            0L
        }
    }

    private fun calculateDirectorySize(dir: File): Long {
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (f in files) {
            size += if (f.isDirectory) calculateDirectorySize(f) else f.length()
        }
        return size
    }

    suspend fun extractAndSetup(
        archiveFile: File,
        distro: UbuntuDistro,
        onProgress: (processedFiles: Int, currentFileName: String) -> Unit,
        onConfiguring: (step: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (!rootfsDir.exists()) {
            rootfsDir.mkdirs()
        }

        // 1. Extract tar.gz archive
        var fileCount = 0
        val targetPath = rootfsDir.toPath()

        FileInputStream(archiveFile).use { fis ->
            BufferedInputStream(fis, 64 * 1024).use { bis ->
                GzipCompressorInputStream(bis).use { gzis ->
                    TarArchiveInputStream(gzis).use { tarIn ->
                        var entry: TarArchiveEntry? = tarIn.nextEntry
                        while (entry != null) {
                            if (!isActive) {
                                throw IllegalStateException("Kurulum kullanıcı tarafından iptal edildi")
                            }

                            val entryName = entry.name.removePrefix("./").removePrefix("/")
                            if (entryName.isNotEmpty() && !entryName.startsWith("..")) {
                                val outputFile = File(rootfsDir, entryName)

                                if (entry.isDirectory) {
                                    outputFile.mkdirs()
                                } else if (entry.isSymbolicLink) {
                                    try {
                                        outputFile.parentFile?.mkdirs()
                                        if (outputFile.exists()) outputFile.delete()
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            val linkTarget = Paths.get(entry.linkName)
                                            Files.createSymbolicLink(outputFile.toPath(), linkTarget)
                                        }
                                    } catch (_: Exception) {
                                        // Ignore symlink failure on filesystems with restricted link creation
                                    }
                                } else {
                                    outputFile.parentFile?.mkdirs()
                                    FileOutputStream(outputFile).use { fos ->
                                        val buffer = ByteArray(32 * 1024)
                                        var len: Int
                                        while (tarIn.read(buffer).also { len = it } != -1) {
                                            fos.write(buffer, 0, len)
                                        }
                                    }

                                    // Mark binary folders, debconf, dpkg and executable scripts
                                    val isExecutable = (entry.mode and 0b001_001_001) != 0 ||
                                        entryName.startsWith("bin/") || 
                                        entryName.startsWith("usr/bin/") || 
                                        entryName.startsWith("sbin/") || 
                                        entryName.startsWith("usr/sbin/") ||
                                        entryName.startsWith("usr/lib/apt/") ||
                                        entryName.startsWith("usr/libexec/") ||
                                        entryName.startsWith("usr/lib/dpkg/") ||
                                        entryName.startsWith("usr/share/debconf/") ||
                                        entryName.startsWith("var/lib/dpkg/") ||
                                        entryName.endsWith(".sh") ||
                                        entryName.endsWith(".pl")

                                    if (isExecutable) {
                                        outputFile.setExecutable(true, false)
                                    }
                                    outputFile.setReadable(true, false)
                                }

                                fileCount++
                                if (fileCount % 30 == 0) {
                                    onProgress(fileCount, entryName)
                                }
                            }
                            entry = tarIn.nextEntry
                        }
                    }
                }
            }
        }

        onProgress(fileCount, "Arşiv başarıyla çıkarıldı (${fileCount} dosya)")

        // 2. Post-Extraction System Configurations
        onConfiguring("Ağ ve DNS (resolv.conf) ayarları yapılandırılıyor...")
        setupDns()

        onConfiguring("Sistem hosts dosyası oluşturuluyor...")
        setupHosts()

        onConfiguring("Gerekli sistem bağlama dizinleri (/dev, /proc, /sys, /tmp) hazırlanıyor...")
        setupSystemDirectories()

        onConfiguring("Kabuk ve terminal ortam değişkenleri (PATH, TERM, LANG) ayarlanıyor...")
        setupEnvironment()

        onConfiguring("APT ve DPKG Android sanallaştırma yamaları (sandbox baypas, unsafe-io) uygulanıyor...")
        setupAptDpkgFixes()

        onConfiguring("Tüm sistem ikili dosyalarının çalıştırma izinleri doğrulanıyor...")
        fixAllPermissions()

        // 3. Mark completed
        markerFile.writeText("${distro.name}\n${distro.version}\n${System.currentTimeMillis()}\n")
        onConfiguring("Ubuntu ARM64 kurulumu tamamlandı!")
    }

    private fun setupDns() {
        val etcDir = File(rootfsDir, "etc")
        if (!etcDir.exists()) etcDir.mkdirs()
        val resolvConf = File(etcDir, "resolv.conf")
        if (resolvConf.exists()) resolvConf.delete()
        resolvConf.writeText(
            """
            # DNS config for Android Ubuntu container
            nameserver 8.8.8.8
            nameserver 1.1.1.1
            nameserver 8.8.4.4
            """.trimIndent() + "\n"
        )
    }

    private fun setupHosts() {
        val etcDir = File(rootfsDir, "etc")
        val hosts = File(etcDir, "hosts")
        hosts.writeText(
            """
            127.0.0.1 localhost ubuntu-arm64
            ::1 localhost ip6-localhost ip6-loopback
            """.trimIndent() + "\n"
        )
    }

    private fun setupSystemDirectories() {
        val dirsToEnsure = listOf("dev", "proc", "sys", "tmp", "root", "home/ubuntu", "run", "var/tmp")
        for (dirName in dirsToEnsure) {
            val dir = File(rootfsDir, dirName)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            if (dirName == "tmp" || dirName == "var/tmp") {
                dir.setReadable(true, false)
                dir.setWritable(true, false)
                dir.setExecutable(true, false)
            }
        }
    }

    private fun setupEnvironment() {
        val profileDir = File(rootfsDir, "etc/profile.d")
        if (!profileDir.exists()) profileDir.mkdirs()

        val envScript = File(profileDir, "android_ubuntu.sh")
        envScript.writeText(
            """
            #!/bin/sh
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
            export HOME=/root
            export USER=root
            export TERM=xterm-256color
            export LANG=C.UTF-8
            export LC_ALL=C.UTF-8
            export SHELL=/bin/bash
            export TMPDIR=/tmp
            export DEBIAN_FRONTEND=noninteractive
            export DEBCONF_NONINTERACTIVE_SEEN=true
            cd /root
            """.trimIndent() + "\n"
        )
        envScript.setExecutable(true, false)

        val etcEnv = File(rootfsDir, "etc/environment")
        etcEnv.writeText(
            """
            PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            DEBIAN_FRONTEND="noninteractive"
            DEBCONF_NONINTERACTIVE_SEEN="true"
            LANG="C.UTF-8"
            LC_ALL="C.UTF-8"
            """.trimIndent() + "\n"
        )
    }

    private fun setupAptDpkgFixes() {
        try {
            val aptConfDir = File(rootfsDir, "etc/apt/apt.conf.d").apply { if (!exists()) mkdirs() }
            val noSandbox = File(aptConfDir, "01_no_sandbox")
            noSandbox.writeText(
                """
                APT::Sandbox::User "root";
                Dir::Etc::sourcelist "/etc/apt/sources.list";
                Acquire::Languages "none";
                Acquire::Check-Valid-Until "false";
                """.trimIndent() + "\n"
            )

            val badDebconf = File(aptConfDir, "00_debconf")
            if (badDebconf.exists()) badDebconf.delete()

            val dpkgOptions = File(aptConfDir, "00_dpkg_options")
            dpkgOptions.writeText(
                """
                DPkg::Options {
                   "--force-confdef";
                   "--force-confold";
                };
                """.trimIndent() + "\n"
            )

            val dpkgConfDir = File(rootfsDir, "etc/dpkg/dpkg.cfg.d").apply { if (!exists()) mkdirs() }
            val forceUnsafeIo = File(dpkgConfDir, "02_force_unsafe_io")
            forceUnsafeIo.writeText("force-unsafe-io\n")

            // PERMANENT NO-SNAP POLICY (Debian/Linux Mint style)
            // Completely block snapd and any transitional snap packages from APT
            val aptPrefDir = File(rootfsDir, "etc/apt/preferences.d").apply { if (!exists()) mkdirs() }
            val noSnapPref = File(aptPrefDir, "nosnap.pref")
            noSnapPref.writeText(
                """
                # PRoot/Android ortamında systemd/snapd çalışmadığı için Snap kalıcı olarak engellenmiştir.
                Package: snapd
                Pin: release *
                Pin-Priority: -10

                Package: snapd:*
                Pin: release *
                Pin-Priority: -10
                """.trimIndent() + "\n"
            )

            // Block snap autoinstall triggers
            val noSnapConf = File(aptConfDir, "00nosnap")
            noSnapConf.writeText(
                """
                # Snap paketlerini engelle
                APT::Get::AutomaticRemove "true";
                """.trimIndent() + "\n"
            )

            val sbinDir = File(rootfsDir, "usr/sbin").apply { if (!exists()) mkdirs() }
            val policyRcD = File(sbinDir, "policy-rc.d")
            policyRcD.writeText("#!/bin/sh\nexit 101\n")
            policyRcD.setExecutable(true, false)
            policyRcD.setReadable(true, false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun fixAllPermissions() {
        val binaryFolders = listOf(
            File(rootfsDir, "bin"),
            File(rootfsDir, "usr/bin"),
            File(rootfsDir, "sbin"),
            File(rootfsDir, "usr/sbin"),
            File(rootfsDir, "usr/lib/apt/methods"),
            File(rootfsDir, "usr/libexec"),
            File(rootfsDir, "usr/lib/dpkg"),
            File(rootfsDir, "usr/share/debconf"),
            File(rootfsDir, "var/lib/dpkg/info")
        )

        for (folder in binaryFolders) {
            if (folder.exists()) {
                folder.walkTopDown().maxDepth(3).forEach { file ->
                    file.setReadable(true, false)
                    if (!file.isDirectory) {
                        file.setExecutable(true, false)
                    } else {
                        file.setExecutable(true, false)
                    }
                }
            }
        }

        val debconfFrontend = File(rootfsDir, "usr/share/debconf/frontend")
        if (debconfFrontend.exists()) {
            debconfFrontend.setReadable(true, false)
            debconfFrontend.setExecutable(true, false)
        }
    }

    suspend fun uninstall() = withContext(Dispatchers.IO) {
        if (rootfsDir.exists()) {
            deleteRecursively(rootfsDir)
        }
    }

    private fun deleteRecursively(file: File) {
        val children = file.listFiles()
        if (children != null) {
            for (child in children) {
                deleteRecursively(child)
            }
        }
        file.delete()
    }
}
