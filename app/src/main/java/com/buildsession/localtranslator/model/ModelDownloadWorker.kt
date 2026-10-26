package com.buildsession.localtranslator.model

import android.content.Context
import androidx.work.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.concurrent.TimeUnit

class ModelDownloadWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun doWork(): Result {
        val modelUrl = inputData.getString("url") ?: return Result.failure()
        val modelName = inputData.getString("name") ?: return Result.failure()
        val expectedMd5 = inputData.getString("md5")
        
        val modelsDir = File(applicationContext.getExternalFilesDir(null), "models")
        if (!modelsDir.exists()) modelsDir.mkdirs()
        
        val tempZipFile = File(modelsDir, "$modelName.zip.tmp")
        val destinationDir = File(modelsDir, modelName)
        
        if (destinationDir.exists()) return Result.success()

        try {
            downloadFileWithResume(modelUrl, tempZipFile)
            
            if (isStopped) return Result.retry()

            // MD5 Verification
            if (expectedMd5 != null) {
                val actualMd5 = calculateMD5(tempZipFile)
                if (actualMd5 != expectedMd5) {
                    tempZipFile.delete()
                    return Result.failure()
                }
            }

            // Rename temp to final zip
            val finalZipFile = File(modelsDir, "$modelName.zip")
            tempZipFile.renameTo(finalZipFile)
            
            // Extract
            try {
                unzip(finalZipFile, modelsDir)
            } finally {
                finalZipFile.delete()
            }
            
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // If it's a permanent error (like no space), failure() might be better, 
            // but for network errors, retry() is good.
            return Result.retry()
        }
    }

    private suspend fun downloadFileWithResume(url: String, file: File) {
        val existingLength = if (file.exists()) file.length() else 0L
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .addHeader("Range", "bytes=$existingLength-")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 416) {
                // Range not satisfiable - might be already fully downloaded or server doesn't support it
                return
            }
            if (!response.isSuccessful) throw Exception("Failed to download model: ${response.code}")
            
            val body = response.body ?: throw Exception("Empty response body")
            val contentLength = body.contentLength()
            
            // If contentLength is -1, we can't show accurate progress, but we can still download
            val totalLength = if (contentLength > 0) existingLength + contentLength else -1L
            
            val isAppend = existingLength > 0 && response.code == 206
            
            body.byteStream().use { inputStream ->
                RandomAccessFile(file, "rw").use { raf ->
                    if (isAppend) {
                        raf.seek(existingLength)
                    } else {
                        raf.setLength(0)
                    }
                    
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var currentTotal = existingLength
                    
                    var lastProgressUpdate = 0L
                    var lastBytesRead = existingLength
                    var lastUpdateTime = System.currentTimeMillis()

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (isStopped) break
                        raf.write(buffer, 0, bytesRead)
                        currentTotal += bytesRead
                        
                        // Update progress every 1000ms
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastProgressUpdate > 1000) {
                            val timeDelta = currentTime - lastUpdateTime
                            if (timeDelta > 0) {
                                val bytesDelta = currentTotal - lastBytesRead
                                val speedBps = (bytesDelta * 1000) / timeDelta

                                if (totalLength > 0) {
                                    val progress = (currentTotal.toFloat() / totalLength * 100).toInt()
                                    setProgress(workDataOf(
                                        "progress" to progress,
                                        "speed" to speedBps
                                    ))
                                } else {
                                    // Indeterminate progress
                                    setProgress(workDataOf(
                                        "progress" to -1,
                                        "speed" to speedBps
                                    ))
                                }
                                
                                lastBytesRead = currentTotal
                                lastUpdateTime = currentTime
                                lastProgressUpdate = currentTime
                            }
                        }
                    }
                    // Final progress update
                    setProgress(workDataOf("progress" to 100, "speed" to 0L))
                }
            }
        }
    }

    private fun unzip(zipFile: File, targetDir: File) {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val newFile = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun calculateMD5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val bytes = digest.digest()
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}
