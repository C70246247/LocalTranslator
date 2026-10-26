package com.buildsession.localtranslator.model

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.InputStream

data class VoskModel(
    val name: String,
    val lang: String,
    val size: Long,
    val size_text: String,
    val url: String,
    val type: String,
    val obsolete: String,
    val version: String,
    val md5: String? = null
)

enum class DownloadStatus {
    NOT_DOWNLOADED,
    WAITING,
    DOWNLOADING,
    PAUSED,
    DOWNLOADED,
    ERROR
}

data class ModelItem(
    val model: VoskModel,
    var status: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
    var progress: Float = 0f,
    var localPath: String? = null
)

class ModelManager(private val context: Context) {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val modelListUrl = "https://alphacephei.com/vosk/models/model-list.json"
    private val modelsDir = File(context.getExternalFilesDir(null), "models")

    init {
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
    }

    suspend fun fetchModelList(onlyRecommended: Boolean = true): List<VoskModel> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(modelListUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("Failed to fetch model list: ${response.code}")
                val json = response.body?.string() ?: ""
                val type = object : TypeToken<List<VoskModel>>() {}.type
                val allModels: List<VoskModel> = gson.fromJson(json, type)
                
                if (onlyRecommended) {
                    // Filter for small and non-obsolete models as recommended for mobile
                    allModels.filter { 
                        (it.lang == "cn" || it.lang == "zh") && 
                        it.type == "small" && 
                        it.obsolete == "false" 
                    }
                } else {
                    allModels
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("获取模型列表失败: ${e.message}")
        }
    }

    fun getLocalModels(): List<File> {
        return modelsDir.listFiles { file -> file.isDirectory }?.toList() ?: emptyList()
    }

    fun getDownloadedModels(): List<String> {
        return getLocalModels().map { it.name }
    }

    fun isModelDownloaded(modelName: String): Boolean {
        return File(modelsDir, modelName).exists()
    }

    fun enqueueDownload(model: VoskModel) {
        val data = workDataOf(
            "url" to model.url,
            "name" to model.name,
            "md5" to model.md5
        )
        
        val downloadRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(data)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("download")
            .addTag("download_${model.name}")
            .build()
            
        WorkManager.getInstance(context).enqueueUniqueWork(
            "download_${model.name}",
            ExistingWorkPolicy.REPLACE,
            downloadRequest
        )
    }

    fun getDownloadProgress(modelName: String): Flow<Triple<WorkInfo.State, Int, Long>?> {
        return WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow("download_$modelName")
            .map { workInfos ->
                val workInfo = workInfos.firstOrNull()
                if (workInfo != null) {
                    val progress = workInfo.progress.getInt("progress", 0)
                    val speed = workInfo.progress.getLong("speed", 0L)
                    Triple(workInfo.state, progress, speed)
                } else {
                    null
                }
            }
    }

    fun cancelDownload(modelName: String) {
        WorkManager.getInstance(context).cancelUniqueWork("download_$modelName")
    }

    fun cancelAllDownloads() {
        WorkManager.getInstance(context).cancelAllWorkByTag("download")
    }

    fun deleteModel(modelName: String): Boolean {
        val modelDir = File(modelsDir, modelName)
        val tempFile = File(modelsDir, "$modelName.zip.tmp")
        val zipFile = File(modelsDir, "$modelName.zip")
        
        var deleted = false
        if (modelDir.exists()) {
            deleted = modelDir.deleteRecursively()
        }
        if (tempFile.exists()) {
            tempFile.delete()
            deleted = true
        }
        if (zipFile.exists()) {
            zipFile.delete()
            deleted = true
        }
        return deleted
    }

    fun getDownloadStatus(modelName: String): DownloadStatus {
        if (isModelDownloaded(modelName)) return DownloadStatus.DOWNLOADED
        // For other statuses, we rely on WorkManager which is reactive, 
        // but this helper can check the directory
        return DownloadStatus.NOT_DOWNLOADED
    }

    suspend fun downloadAndExtractModel(
        model: VoskModel,
        onProgress: (Float) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val destinationDir = File(modelsDir, model.name)
        if (destinationDir.exists()) return@withContext destinationDir.absolutePath

        val tempZipFile = File(modelsDir, "${model.name}.zip")
        val request = Request.Builder().url(model.url).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to download model")
            val body = response.body ?: throw Exception("Empty response body")
            val contentLength = body.contentLength()
            
            body.byteStream().use { inputStream ->
                FileOutputStream(tempZipFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (contentLength > 0) {
                            onProgress(totalBytesRead.toFloat() / contentLength)
                        }
                    }
                }
            }
        }

        // Extract ZIP
        unzip(tempZipFile, modelsDir)
        tempZipFile.delete()

        destinationDir.absolutePath
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
}
