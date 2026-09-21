package com.example.engine

import android.content.Context
import com.example.model.UbuntuDistro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class UbuntuDownloadManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getArchiveFile(distro: UbuntuDistro): File {
        val ext = if (distro.downloadUrl.endsWith(".tar.xz")) ".tar.xz" else ".tar.gz"
        return File(context.cacheDir, "${distro.id}$ext")
    }

    suspend fun downloadDistro(
        distro: UbuntuDistro,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long, speedKbps: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        val targetFile = getArchiveFile(distro)
        val ext = if (distro.downloadUrl.endsWith(".tar.xz")) ".tar.xz" else ".tar.gz"

        val urlsToTry = distro.downloadUrls
        var lastException: Exception? = null
        val tempFile = File(cacheDir, "${distro.id}$ext.tmp")

        for ((index, currentUrl) in urlsToTry.withIndex()) {
            try {
                if (tempFile.exists()) tempFile.delete()

                val request = Request.Builder()
                    .url(currentUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android) UbuntuARM64-Installer/1.0")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val code = response.code
                    val msg = response.message
                    response.close()
                    lastException = IllegalStateException("Ayna #${index + 1} ($currentUrl) başarısız oldu: HTTP $code - $msg")
                    continue
                }

                val body = response.body ?: throw IllegalStateException("Sunucu boş yanıt döndürdü")
                val contentLength = body.contentLength()
                val totalBytes = if (contentLength > 0) contentLength else (distro.approxDownloadMb * 1024L * 1024L)

                var downloadedBytes = 0L
                var lastTime = System.currentTimeMillis()
                var lastBytes = 0L
                var speedKbps = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (!isActive) {
                                tempFile.delete()
                                throw IllegalStateException("İndirme kullanıcı tarafından iptal edildi")
                            }

                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val now = System.currentTimeMillis()
                            val timeDiff = now - lastTime
                            if (timeDiff >= 400) {
                                val bytesDiff = downloadedBytes - lastBytes
                                speedKbps = (bytesDiff * 1000L / timeDiff) / 1024L
                                lastTime = now
                                lastBytes = downloadedBytes

                                val progress = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.0f, 1.0f)
                                onProgress(progress, downloadedBytes, totalBytes, speedKbps)
                            }
                        }
                        output.flush()
                    }
                }

                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                onProgress(1.0f, downloadedBytes, totalBytes, speedKbps)
                return@withContext targetFile

            } catch (e: Exception) {
                if (!isActive) throw e
                lastException = e
            }
        }

        throw lastException ?: IllegalStateException("Ubuntu rootfs arşivi hiçbir aynadan indirilemedi.")
    }

    fun getDownloadedArchive(distro: UbuntuDistro): File? {
        val file = getArchiveFile(distro)
        return if (file.exists() && file.length() > 1_000_000) file else null
    }

    fun deleteDownloadedArchive(distro: UbuntuDistro) {
        val file = getArchiveFile(distro)
        if (file.exists()) file.delete()
    }
}
