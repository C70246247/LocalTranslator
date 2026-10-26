package com.buildsession.localtranslator.translation

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.work.*
import org.nehuatl.llamacpp.LlamaHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import android.os.Process
import android.util.Log
import java.io.File

private const val TAG = "TranslationManager"

data class TranslationModel(
    val id: String,
    val name: String,
    val url: String,
    val sizeText: String,
    val description: String,
    val isRecommended: Boolean = false,
    val supportedLanguages: List<String>? = null // null 表示支持所有（或使用默认列表）
)

/**
 * 推理参数封装
 */
data class InferenceOptions(
    val temperature: Float = 0.8f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val repeatPenalty: Float = 1.1f,
    val maxTokens: Int = 512
)

class TranslationManager(private val context: Context) {
    private val modelsDir = File(context.getExternalFilesDir(null), "translation_models")
    
    // 核心优化：使用专门的推理调度器，避免受 IO 线程池或系统后台限制的影响
    private val inferenceDispatcher = Dispatchers.Default.limitedParallelism(Runtime.getRuntime().availableProcessors())
    private val scope = CoroutineScope(inferenceDispatcher + SupervisorJob())
    
    companion object {
        private val _loadedModelId = MutableStateFlow<String?>(null)
        val loadedModelId: StateFlow<String?> = _loadedModelId.asStateFlow()
        
        private var sharedLlamaHelper: LlamaHelper? = null
        private val _llmFlow = MutableSharedFlow<LlamaHelper.LLMEvent>(
            replay = 0,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val llmFlow: Flow<LlamaHelper.LLMEvent> = _llmFlow.asSharedFlow()

        private val _isBusy = MutableStateFlow(false)
        val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

        private val _sessionId = MutableStateFlow<String>("")
        val sessionId: StateFlow<String> = _sessionId.asStateFlow()

        init {
            // 在全局作用域启动一个协程，监听所有 LLM 事件并更新忙碌状态
            // 这样即使 LlamaHelper 实例重建，忙碌状态监听依然有效
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                _llmFlow.collect { event ->
                    when (event) {
                        is LlamaHelper.LLMEvent.Started -> _isBusy.value = true
                        is LlamaHelper.LLMEvent.Done, is LlamaHelper.LLMEvent.Error -> _isBusy.value = false
                        else -> {}
                    }
                }
            }
        }
    }

    // Delegation properties for backward compatibility and easier access
    val loadedModelId: StateFlow<String?> get() = Companion.loadedModelId
    val llmFlow: Flow<LlamaHelper.LLMEvent> get() = Companion.llmFlow
    val isBusy: StateFlow<Boolean> get() = Companion.isBusy
    val sessionId: StateFlow<String> get() = Companion.sessionId

    private fun updateSessionId() {
        _sessionId.value = java.util.UUID.randomUUID().toString()
    }

    private fun getLlamaHelper(): LlamaHelper {
        synchronized(TranslationManager::class.java) {
            return sharedLlamaHelper ?: LlamaHelper(context.applicationContext.contentResolver, scope, _llmFlow).also {
                sharedLlamaHelper = it
            }
        }
    }

    init {
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
    }

    val availableModels = listOf(
        TranslationModel(
            id = "hy-mt-1.8b-q4-k-m",
            name = "Hy-MT 1.5 (1.8B Q4_K_M)",
            url = "https://hf-mirror.com/tencent/HY-MT1.5-1.8B-GGUF/resolve/main/HY-MT1.5-1.8B-Q4_K_M.gguf",
            sizeText = "1.13 GB",
            description = "腾讯官方离线翻译模型，翻译质量较好，推荐优先使用。（Recommand）",
            isRecommended = true
        ),
        TranslationModel(
            id = "qwen2.5-1.5b-instruct",
            name = "Qwen2.5 1.5B (Instruct)",
            url = "https://hf-mirror.com/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            sizeText = "920 MB",
            description = "响应速度一般，翻译质量一般。",
            isRecommended = false
        ),
        TranslationModel(
            id = "sakura-1.5b-qwen2.5-fp16",
            name = "Sakura 1.5B (FP16 Full)",
            url = "https://hf-mirror.com/SakuraLLM/Sakura-1.5B-Qwen2.5-v1.0-GGUF/resolve/main/sakura-1.5b-qwen2.5-v1.0-fp16.gguf",
            sizeText = "3.56 GB",
            description = "Sakura 1.5B 全精度版，翻译质量极高但体积较大，建议 8GB RAM 以上设备使用。",
            isRecommended = false
        ),
        TranslationModel(
            id = "sakura-7b-q4",
            name = "Sakura 7B (Q4_K_M)",
            url = "https://hf-mirror.com/SakuraLLM/Sakura-7B-Qwen2.5-v1.0-GGUF/resolve/main/sakura-7b-qwen2.5-v1.0-q4_k_m.gguf",
            sizeText = "4.40 GB",
            description = "Sakura 7B 量化版，目前最强日语翻译模型，建议 12GB RAM 以上设备使用。",
            isRecommended = false
        ),
        TranslationModel(
            id = "internlm2.5-1.8b-chat",
            name = "InternLM2.5 1.8B (Chat)",
            url = "https://hf-mirror.com/internlm/internlm2_5-1_8b-chat-gguf/resolve/main/internlm2_5-1_8b-chat-q4_k_m.gguf",
            sizeText = "1.1 GB",
            description = "上海 AI 实验室出品，中文语感细腻，逻辑理解强。",
            isRecommended = false
        ),
        TranslationModel(
            id = "hy-mt-1.8b-1.25bit",
            name = "Hy-MT 1.5 (1.8B 1.25bit)",
            url = "https://hf-mirror.com/AngelSlim/Hy-MT1.5-1.8B-1.25bit-GGUF/resolve/main/Hy-MT1.5-1.8B-1.25bit.gguf",
            sizeText = "440 MB",
            description = "腾讯混元 1.25-bit 极致压缩版，适合低内存设备。",
            isRecommended = false
        ),
        TranslationModel(
            id = "alma-7b-q4",
            name = "ALMA 7B (Q4_K_M)",
            url = "https://hf-mirror.com/TheBloke/ALMA-7B-GGUF/resolve/main/alma-7b.Q4_K_M.gguf",
            sizeText = "4.58 GB",
            description = "顶级离线翻译模型，翻译质量接近 GPT-4。需要 12GB RAM 以上设备。",
            isRecommended = false,
            supportedLanguages = listOf("中文", "英语", "德语", "法语", "日语", "俄语", "捷克语", "冰岛语", "越南语", "葡萄牙语")
        )
    )

    fun getModelFile(modelId: String): File {
        return File(modelsDir, "$modelId.gguf")
    }

    fun isModelDownloaded(modelId: String): Boolean {
        return getModelFile(modelId).exists()
    }

    fun loadModel(modelId: String, contextLength: Int = 2048, useMmap: Boolean = true, onLoaded: (Long) -> Unit) {
        // 立即清除当前 ID，防止 UI 在加载过程中仍认为旧模型可用
        _loadedModelId.value = null
        // 模型加载时立即更新会话 ID，防止旧模型的推理结果干扰新模型
        updateSessionId()
        
        scope.launch {
            // 重要：切换模型前，彻底释放旧 Helper 实例及其关联的 Native 资源
            // 这是解决 "Model was not loaded yet" 的关键，确保新模型加载在干净的上下文中
            synchronized(TranslationManager::class.java) {
                sharedLlamaHelper?.let {
                    Log.d(TAG, "Releasing existing LlamaHelper before loading new model...")
                    it.abort()
                    it.release()
                    sharedLlamaHelper = null
                }
            }
            delay(300) // 给予充足时间让 Native 层清理
            
            val file = getModelFile(modelId)
            if (!file.exists()) {
                withContext(Dispatchers.Main) {
                    onLoaded(-1L)
                }
                return@launch
            }

            val modelUri = android.net.Uri.fromFile(file).toString()
            
            try {
                 Log.d(TAG, "Starting to load model: $modelId from $modelUri")
                 getLlamaHelper().load(
                     path = modelUri,
                     contextLength = contextLength
                 ) { id ->
                     scope.launch(Dispatchers.Main) {
                         if (id >= 0) {
                             Log.i(TAG, "Model loaded successfully: $modelId, id: $id")
                             _loadedModelId.value = modelId
                         } else {
                             Log.e(TAG, "Failed to load model: $modelId, native id returned: $id")
                             _loadedModelId.value = null
                         }
                         onLoaded(id)
                     }
                 }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during model loading: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onLoaded(-1L)
                }
            }
        }
    }

    /**
     * 执行单次翻译并返回结果（挂起函数）
     */
    suspend fun translateOneOff(text: String, targetLang: String, sourceLang: String = "自动检测"): String = withContext(inferenceDispatcher) {
        if (text.isBlank() || _loadedModelId.value == null) return@withContext ""

        val currentModelId = _loadedModelId.value
        val prompt = buildPrompt(text, targetLang, sourceLang, currentModelId)
        
        val result = StringBuilder()
        val helper = getLlamaHelper()
        
        val deferred = CompletableDeferred<String>()
        
        val collectorJob = CoroutineScope(Dispatchers.IO).launch {
            llmFlow.collect { event ->
                when (event) {
                    is LlamaHelper.LLMEvent.Ongoing -> result.append(event.word)
                    is LlamaHelper.LLMEvent.Done -> deferred.complete(result.toString())
                    is LlamaHelper.LLMEvent.Error -> deferred.completeExceptionally(Exception(event.message))
                    else -> {}
                }
            }
        }
        
        try {
            runInferenceInternal(prompt)
            return@withContext deferred.await()
        } catch (e: Exception) {
            Log.e(TAG, "translateOneOff failed", e)
            return@withContext "翻译失败: ${e.message}"
        } finally {
            collectorJob.cancel()
        }
    }

    private fun buildPrompt(text: String, targetLang: String, sourceLang: String, currentModelId: String?): String {
        return when {
            currentModelId == "alma-7b-q4" -> {
                val langMap = mapOf(
                    "中文" to "Chinese",
                    "英语" to "English",
                    "日语" to "Japanese",
                    "德语" to "German",
                    "法语" to "French",
                    "俄语" to "Russian",
                    "捷克语" to "Czech",
                    "冰岛语" to "Icelandic",
                    "越南语" to "Vietnamese",
                    "葡萄牙语" to "Portuguese",
                    "自动检测" to "detect"
                )
                val src = langMap[sourceLang] ?: "Chinese"
                val tgt = langMap[targetLang] ?: "English"
                if (src == "detect") {
                    "Translate this to $tgt:\n$text\n$tgt:"
                } else {
                    "Translate this from $src to $tgt:\n$src: $text\n$tgt:"
                }
            }
            currentModelId?.contains("sakura") == true -> {
                val systemPrompt = if (targetLang == "中文") "你是一个专业的日语翻译，请将以下日语翻译成中文。" else "你是一个专业的中文翻译，请将以下中文翻译成日语。"
                "<|im_start|>system\n$systemPrompt<|im_end|>\n<|im_start|>user\n$text<|im_end|>\n<|im_start|>assistant\n"
            }
            currentModelId == "qwen2.5-1.5b-instruct" -> {
                val systemPrompt = "You are a helpful assistant."
                val userPrompt = "把这段文本翻译成${targetLang}，不要输出多余内容：\n\n$text"
                "<|im_start|>system\n$systemPrompt<|im_end|>\n<|im_start|>user\n$userPrompt<|im_end|>\n<|im_start|>assistant\n"
            }
            currentModelId == "internlm2.5-1.8b-chat" -> {
                val systemPrompt = "你是一个专业的离线翻译助手。"
                val srcText = if (sourceLang == "自动检测") "" else "从${sourceLang}"
                val userPrompt = "把这段文本${srcText}翻译成${targetLang}，只输出翻译结果：\n\n$text"
                "<|im_start|>system\n$systemPrompt<|im_end|>\n<|im_start|>user\n$userPrompt<|im_end|>\n<|im_start|>assistant\n"
            }
            else -> {
                val userPrompt = "把下面的文本翻译成$targetLang，不要额外解释。\n\n$text"
                val systemPrompt = "你是一个专业的离线翻译助手。"
                "<｜hy_begin▁of▁sentence｜>$systemPrompt<｜hy_place▁holder▁no▁3｜><｜hy_User｜>$userPrompt<｜hy_Assistant｜>"
            }
        }
    }

    fun translate(text: String, targetLang: String, sourceLang: String = "自动检测"): Boolean {
        if (text.isBlank() || _loadedModelId.value == null) return false
        val currentModelId = _loadedModelId.value
        scope.launch {
            val prompt = buildPrompt(text, targetLang, sourceLang, currentModelId)
            runInferenceInternal(prompt)
        }
        return true
    }

    /**
     * 直接发送原始 Prompt 进行预测，不经过翻译模板包装
     */
    fun rawPredict(
        prompt: String,
        temperature: Float = 0.8f,
        topK: Int = 40,
        topP: Float = 0.95f,
        repeatPenalty: Float = 1.1f
    ) {
        scope.launch {
            runInferenceInternal(
                prompt, 
                InferenceOptions(
                    temperature = temperature,
                    topK = topK,
                    topP = topP,
                    repeatPenalty = repeatPenalty
                )
            )
        }
    }

    /**
     * 统一的推理接口，自动处理中止、延迟和 session 更新
     */
    fun runInference(prompt: String, options: InferenceOptions = InferenceOptions()) {
        scope.launch {
            runInferenceInternal(prompt, options)
        }
    }

    private suspend fun runInferenceInternal(prompt: String, options: InferenceOptions = InferenceOptions()) {
        try {
            // 在重负载推理前手动触发一次轻量级 GC，回收 OCR 产生的大量 Bitmap 碎片
            System.gc()
            
            // 提升当前线程优先级
            Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)
            
            val availableProcessors = Runtime.getRuntime().availableProcessors()
            Log.d(TAG, "runInferenceInternal: prompt length=${prompt.length}, busy=${_isBusy.value}, CPU Cores: $availableProcessors")
            val helper = getLlamaHelper()
            // 如果当前正在忙碌，强制中止之前的任务
            if (_isBusy.value) {
                Log.d(TAG, "System busy, aborting previous inference...")
                helper.abort()
                delay(300) // 确保 Native 完全退出
            } else {
                // 即使不忙碌，也进行一次 abort 以防万一
                helper.abort()
                delay(150)
            }
            
            Log.i(TAG, "Starting inference...")
            helper.predict(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "Inference launch failed! Prompt: ${prompt.take(100)}...", e)
            _llmFlow.emit(LlamaHelper.LLMEvent.Error("推理启动失败: ${e.localizedMessage}"))
        }
    }

    fun abort() {
        synchronized(TranslationManager::class.java) {
            sharedLlamaHelper?.abort()
            // 确保忙碌状态在手动中止时能正确同步
            _isBusy.value = false
        }
    }

    /**
     * 获取模型默认的系统提示词
     */
    fun getDefaultSystemPrompt(modelId: String?): String {
        return when {
            modelId?.contains("sakura") == true -> "你是一个专业的日语翻译，请将以下内容翻译成中文。"
            modelId == "qwen2.5-1.5b-instruct" -> "You are a helpful assistant."
            modelId == "internlm2.5-1.8b-chat" -> "你是一个专业的离线翻译助手。"
            modelId == "alma-7b-q4" -> "Translate the following text accurately."
            modelId?.contains("hy-mt") == true -> "你是一个专业的离线翻译助手。"
            else -> "你是一个乐于助人的 AI 助手。"
        }
    }

    fun release() {
        synchronized(TranslationManager::class.java) {
            sharedLlamaHelper?.let {
                it.abort()
                it.release()
                sharedLlamaHelper = null
            }
        }
        _loadedModelId.value = null
    }

    fun enqueueDownload(model: TranslationModel) {
        val data = workDataOf(
            "url" to model.url,
            "id" to model.id
        )

        val request = OneTimeWorkRequestBuilder<TranslationModelWorker>()
            .setInputData(data)
            .addTag("download_${model.id}")
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "download_${model.id}",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun getDownloadProgress(modelId: String): Flow<Triple<WorkInfo.State, Int, Long>> {
        return WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow("download_$modelId")
            .map { workInfos ->
                val info = workInfos.firstOrNull()
                val state = info?.state ?: WorkInfo.State.SUCCEEDED
                val progress = info?.progress?.getInt("progress", 0) ?: 0
                val speed = info?.progress?.getLong("speed", 0L) ?: 0L
                Triple(state, progress, speed)
            }
    }

    fun deleteModel(modelId: String): Boolean {
        return getModelFile(modelId).delete()
    }

    fun cancelDownload(modelId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("download_$modelId")
    }
}
