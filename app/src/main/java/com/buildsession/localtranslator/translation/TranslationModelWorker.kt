package com.buildsession.localtranslator.translation

import android.content.Context
import androidx.work.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

class TranslationModelWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun doWork(): Result {
        val url = inputData.getString("url") ?: return Result.failure()
        val modelId = inputData.getString("id") ?: return Result.failure()
        
        val modelsDir = File(applicationContext.getExternalFilesDir(null), "translation_models")
        if (!modelsDir.exists()) modelsDir.mkdirs()
        
        val tempFile = File(modelsDir, "$modelId.gguf.tmp")
        val finalFile = File(modelsDir, "$modelId.gguf")
        
        if (finalFile.exists()) return Result.success()

        try {
            downloadFileWithResume(url, tempFile)
            
            if (isStopped) return Result.retry()

            // Rename temp to final
            if (tempFile.renameTo(finalFile)) {
                return Result.success()
            } else {
                return Result.failure()
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
            if (response.code == 416) return
            if (!response.isSuccessful) throw Exception("Failed to download: ${response.code}")
            
            val body = response.body ?: throw Exception("Empty body")
            val contentLength = body.contentLength()
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
                        
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastProgressUpdate > 1000) { // 严格控制为每秒刷新一次
                            val timeDelta = currentTime - lastUpdateTime
                            if (timeDelta > 0) {
                                val bytesDelta = currentTotal - lastBytesRead
                                val speedBps = (bytesDelta * 1000) / timeDelta // 字节每秒
                                
                                val progress = if (totalLength > 0) {
                                    (currentTotal.toFloat() / totalLength * 100).toInt()
                                } else 0
                                
                                setProgress(workDataOf(
                                    "progress" to progress,
                                    "speed" to speedBps
                                ))
                                
                                lastBytesRead = currentTotal
                                lastUpdateTime = currentTime
                                lastProgressUpdate = currentTime
                            }
                        }
                    }
                }
            }
        }
    }
}
