package com.example.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import com.example.model.SystemInfo
import java.io.File
import java.io.FileInputStream

object DeviceSystemHelper {

    fun getSystemInfo(context: Context): SystemInfo {
        val supportedAbis = Build.SUPPORTED_ABIS.toList()
        val isArm64 = supportedAbis.any { 
            it.equals("arm64-v8a", ignoreCase = true) || it.equals("aarch64", ignoreCase = true) 
        }
        val primaryAbi = supportedAbis.firstOrNull() ?: "Unknown"

        // Storage check using internal files directory
        val filesDir = context.filesDir
        val statFs = StatFs(filesDir.absolutePath)
        val blockSize = statFs.blockSizeLong
        val availableBlocks = statFs.availableBlocksLong
        val totalBlocks = statFs.blockCountLong
        val freeStorageBytes = availableBlocks * blockSize
        val totalStorageBytes = totalBlocks * blockSize

        // Memory check
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        if (activityManager != null) {
            activityManager.getMemoryInfo(memoryInfo)
        }
        val totalRamBytes = memoryInfo.totalMem
        val availableRamBytes = memoryInfo.availMem

        // Kernel version check
        val kernelVersion = getKernelVersion()

        return SystemInfo(
            isArm64 = isArm64,
            primaryAbi = primaryAbi,
            allSupportedAbis = supportedAbis,
            freeStorageBytes = freeStorageBytes,
            totalStorageBytes = totalStorageBytes,
            totalRamBytes = totalRamBytes,
            availableRamBytes = availableRamBytes,
            kernelVersion = kernelVersion,
            androidVersion = Build.VERSION.RELEASE ?: "Unknown",
            apiLevel = Build.VERSION.SDK_INT
        )
    }

    private fun getKernelVersion(): String {
        return try {
            val procVersion = File("/proc/version")
            if (procVersion.exists() && procVersion.canRead()) {
                FileInputStream(procVersion).bufferedReader().use { it.readLine() ?: "" }
            } else {
                System.getProperty("os.version") ?: "Linux (Generic)"
            }
        } catch (_: Exception) {
            System.getProperty("os.version") ?: "Linux"
        }
    }
}
