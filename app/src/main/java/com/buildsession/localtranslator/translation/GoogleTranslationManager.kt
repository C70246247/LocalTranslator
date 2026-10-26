package com.buildsession.localtranslator.translation

import android.content.Context
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GoogleTranslationManager(private val context: Context) {
    private val TAG = "GoogleTranslationManager"
    private val modelManager = RemoteModelManager.getInstance()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadingModels = MutableStateFlow<Set<String>>(emptySet())
    val downloadingModels: StateFlow<Set<String>> = _downloadingModels.asStateFlow()

    private val _downloadedModels = MutableStateFlow<Set<String>>(emptySet())
    val downloadedModels: StateFlow<Set<String>> = _downloadedModels.asStateFlow()

    private var currentTranslator: Translator? = null
    private var currentOptions: TranslatorOptions? = null

    init {
        refreshDownloadedModels()
    }

    fun refreshDownloadedModels() {
        modelManager.getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models ->
                _downloadedModels.value = models.map { it.language }.toSet()
            }
    }

    /**
     * 获取所有可用的语言列表 (名称 to 代码)
     */
    fun getAvailableLanguages(): List<Pair<String, String>> {
        return TranslateLanguage.getAllLanguages().map { tag ->
            val locale = java.util.Locale(tag)
            val name = locale.getDisplayName(java.util.Locale.CHINA)
            name to tag
        }.sortedBy { it.first }
    }

    /**
     * 推荐语言列表
     */
    val recommendedLanguages = listOf(
        "中文" to TranslateLanguage.CHINESE,
        "英语" to TranslateLanguage.ENGLISH,
        "日语" to TranslateLanguage.JAPANESE,
        "韩语" to TranslateLanguage.KOREAN
    )

    /**
     * 将通用语言名称转换为 ML Kit 的语言代码
     */
    private fun getLanguageCode(lang: String): String? {
        return when (lang) {
            "中文" -> TranslateLanguage.CHINESE
            "英语" -> TranslateLanguage.ENGLISH
            "日语" -> TranslateLanguage.JAPANESE
            "韩语" -> TranslateLanguage.KOREAN
            "法语" -> TranslateLanguage.FRENCH
            "德语" -> TranslateLanguage.GERMAN
            "俄语" -> TranslateLanguage.RUSSIAN
            "西班牙语" -> TranslateLanguage.SPANISH
            "意大利语" -> TranslateLanguage.ITALIAN
            "阿拉伯语" -> TranslateLanguage.ARABIC
            else -> {
                // 尝试直接匹配 tag
                val all = TranslateLanguage.getAllLanguages()
                if (all.contains(lang)) lang else null
            }
        }
    }

    suspend fun translate(text: String, sourceLang: String, targetLang: String): String {
        val sourceCode = getLanguageCode(sourceLang) ?: TranslateLanguage.ENGLISH
        val targetCode = getLanguageCode(targetLang) ?: TranslateLanguage.CHINESE

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceCode)
            .setTargetLanguage(targetCode)
            .build()

        // 只有当配置改变时才重新创建 Translator
        if (currentTranslator == null || currentOptions != options) {
            currentTranslator?.close()
            currentTranslator = Translation.getClient(options)
            currentOptions = options
        }

        val translator = currentTranslator!!

        return try {
            // 确保模型已下载
            val conditions = DownloadConditions.Builder()
                .build() // 移除 requireWifi 以便按需下载更灵活
            
            _isDownloading.value = true
            _downloadingModels.value += sourceCode
            _downloadingModels.value += targetCode
            
            translator.downloadModelIfNeeded(conditions).await()
            
            _downloadingModels.value -= sourceCode
            _downloadingModels.value -= targetCode
            refreshDownloadedModels()
            _isDownloading.value = false

            translator.translate(text).await()
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed", e)
            _isDownloading.value = false
            _downloadingModels.value = emptySet()
            "翻译失败: ${e.localizedMessage}"
        }
    }

    suspend fun downloadModel(langCode: String) {
        val model = TranslateRemoteModel.Builder(langCode).build()
        val conditions = DownloadConditions.Builder().build()
        
        try {
            _isDownloading.value = true
            _downloadingModels.value += langCode
            modelManager.download(model, conditions).await()
            refreshDownloadedModels()
            Log.i(TAG, "Model downloaded: $langCode")
        } catch (e: Exception) {
            Log.e(TAG, "Model download failed: $langCode", e)
        } finally {
            _downloadingModels.value -= langCode
            _isDownloading.value = _downloadingModels.value.isNotEmpty()
        }
    }

    suspend fun deleteModel(langCode: String) {
        val model = TranslateRemoteModel.Builder(langCode).build()
        try {
            modelManager.deleteDownloadedModel(model).await()
            refreshDownloadedModels()
            Log.i(TAG, "Model deleted: $langCode")
        } catch (e: Exception) {
            Log.e(TAG, "Model delete failed: $langCode", e)
        }
    }

    suspend fun isModelDownloaded(langCode: String): Boolean {
        val model = TranslateRemoteModel.Builder(langCode).build()
        return try {
            modelManager.isModelDownloaded(model).await()
        } catch (e: Exception) {
            false
        }
    }

    fun close() {
        currentTranslator?.close()
        currentTranslator = null
        currentOptions = null
    }
}
