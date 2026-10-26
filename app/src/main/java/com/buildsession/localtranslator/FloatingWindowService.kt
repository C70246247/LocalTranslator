package com.buildsession.localtranslator

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.buildsession.localtranslator.model.ModelManager
import kotlinx.coroutines.*
import com.buildsession.localtranslator.util.VoskHelper
import com.buildsession.localtranslator.util.VoskResultParser
import com.google.gson.Gson
import java.io.File
import org.vosk.Model
import org.vosk.Recognizer
import kotlin.math.roundToInt

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager

import android.content.ClipboardManager
import android.content.ClipData
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.sp

import com.buildsession.localtranslator.util.AccessibilityUtils
import com.buildsession.localtranslator.util.SettingsManager
import com.buildsession.localtranslator.ui.theme.localtranslatorTheme
import com.buildsession.localtranslator.util.MediaProjectionShareManager
import com.buildsession.localtranslator.translation.GoogleTranslationManager
import com.buildsession.localtranslator.translation.TranslationManager
import com.buildsession.localtranslator.translation.TranslationModel

import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import rikka.shizuku.Shizuku
import android.content.pm.ServiceInfo
import android.content.pm.PackageManager
import rikka.shizuku.ShizukuProvider

import android.media.AudioAttributes
import android.media.AudioPlaybackCaptureConfiguration
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.util.Log
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.RadioButtonChecked
import com.buildsession.localtranslator.translation.ScreenCaptureActivity

class FloatingWindowService : LifecycleService(), SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private lateinit var modelManager: ModelManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var googleTranslationManager: GoogleTranslationManager
    private lateinit var translationManager: TranslationManager
    private val gson = Gson()
    
    private var voskModel: Model? = null
    private var voskRecognizer: Recognizer? = null
    private var micAudioRecord: AudioRecord? = null
    private var sysAudioRecord: AudioRecord? = null
    private var recognitionJob: Job? = null
    private var finalTranslationJob: Job? = null
    private var partialTranslationJob: Job? = null
    
    // 移除私有的 mediaProjection 和 screenCaptureResultData，改用 MediaProjectionShareManager

    private val screenCaptureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.buildsession.localtranslator.SCREEN_CAPTURE_PERMISSION_GRANTED") {
                val requester = intent.getStringExtra("requester")
                if (requester == "STT") {
                    val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra("result_data", Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra<Intent>("result_data")
                    }
                    if (data != null) {
                        MediaProjectionShareManager.setResultData(data)
                        startInternalAudioRecognition()
                    }
                }
            }
        }
    }

    private val _recognizedText = mutableStateOf("")
    private val _partialText = mutableStateOf("")
    private val _isMicActive = mutableStateOf(false)
    private val _isSysActive = mutableStateOf(false)
    private val _isModelLoaded = mutableStateOf(false)
    private val _currentModelName = mutableStateOf("未加载")
    private val _downloadedModels = mutableStateListOf<String>()
    private val _isAutoSend = mutableStateOf(false)
    private val _isMinimized = mutableStateOf(false)

    // 实时翻译相关状态
    private val _isTranslationEnabled = mutableStateOf(false)
    private val _sourceLanguage = mutableStateOf("en") // 默认源语言为英文
    private val _targetLanguage = mutableStateOf("zh") // 默认翻译到中文
    private val _translatedText = mutableStateOf("")
    private val _partialTranslatedText = mutableStateOf("")
    
    // STT 专属翻译引擎选择：当全局 transEngine == "LLM" 时生效
    private val _sttTranslationEngine = mutableStateOf("GOOGLE") // "GOOGLE" or "LLM"
    private val _isLlmModelLoaded = mutableStateOf(false)
    private val _loadedLlmModelId = mutableStateOf<String?>(null)

    private val _windowWidth = mutableStateOf(300)
    private val _windowHeight = mutableStateOf(200)

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        modelManager = ModelManager(this)
        settingsManager = SettingsManager(this)
        googleTranslationManager = GoogleTranslationManager(this)
        translationManager = TranslationManager(this)
        
        // 监听 LLM 模型加载状态
        lifecycleScope.launch {
            TranslationManager.loadedModelId.collect { modelId ->
                _isLlmModelLoaded.value = modelId != null
                _loadedLlmModelId.value = modelId
            }
        }
        
        savedStateRegistryController.performRestore(null)
        createNotificationChannel()
        createNotification()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val filter = IntentFilter("com.buildsession.localtranslator.SCREEN_CAPTURE_PERMISSION_GRANTED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenCaptureReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenCaptureReceiver, filter)
        }

        refreshDownloadedModels()
        showFloatingWindow()
        
        // 自动启用无障碍服务逻辑
        if (settingsManager.mergedAutoAccessibility) {
            AccessibilityUtils.enableAccessibilityService(this)
        }
    }

    private fun refreshDownloadedModels() {
        lifecycleScope.launch(Dispatchers.IO) {
            val models = modelManager.getDownloadedModels()
            withContext(Dispatchers.Main) {
                _downloadedModels.clear()
                _downloadedModels.addAll(models)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val modelName = intent?.getStringExtra("model_name")
        if (modelName != null) {
            loadSpecificModel(modelName)
        } else {
            loadModel()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun loadSpecificModel(modelName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val modelFile = File(modelManager.getLocalModels().find { it.name == modelName }?.absolutePath ?: return@launch)
                _currentModelName.value = "正在加载..."
                _isModelLoaded.value = false
                
                // Close old model if exists
                voskModel?.close()
                voskRecognizer = null
                
                voskModel = Model(modelFile.absolutePath)
                voskRecognizer = Recognizer(voskModel, 16000.0f).apply {
                    // 强制开启部分词汇返回，提升实时性
                    // 注意：Vosk Recognizer 本身支持 setPartialWords 等方法，
                    // 但由于当前项目中使用了反射工具类 VoskHelper，我们需要确保模型加载后配置正确
                }
                _isModelLoaded.value = true
                _currentModelName.value = modelFile.name
                refreshDownloadedModels()
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FloatingWindowService, "模型加载失败: $modelName", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "AiTTS Service Channel"
            val descriptionText = "Notifications for AiTTS Floating Window Service"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun loadModel() {
        lifecycleScope.launch(Dispatchers.IO) {
            val localModels = modelManager.getLocalModels()
            if (localModels.isNotEmpty()) {
                try {
                    val modelFile = localModels[0]
                    voskModel = Model(modelFile.absolutePath)
                    voskRecognizer = Recognizer(voskModel, 16000.0f).apply {
                        // 启用实时部分识别
                    }
                    _isModelLoaded.value = true
                    _currentModelName.value = modelFile.name
                    refreshDownloadedModels()
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FloatingWindowService, "模型加载失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FloatingWindowService, "请先在主界面下载模型", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startMicRecognition() {
        if (voskRecognizer == null) {
            Toast.makeText(this, "模型未就绪", Toast.LENGTH_SHORT).show()
            return
        }
        if (_isMicActive.value) return

        val bufferSize = AudioRecord.getMinBufferSize(
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 2

        try {
            micAudioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (micAudioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Toast.makeText(this, "麦克风初始化失败", Toast.LENGTH_SHORT).show()
                micAudioRecord?.release()
                micAudioRecord = null
                return
            }

            micAudioRecord?.startRecording()
            _isMicActive.value = true
            ensureRecognitionJobRunning()
        } catch (e: SecurityException) {
            e.printStackTrace()
            Toast.makeText(this, "缺少麦克风权限: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "启动麦克风识别失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startInternalAudioRecognition() {
        if (voskRecognizer == null) {
            Toast.makeText(this, "模型未就绪", Toast.LENGTH_SHORT).show()
            return
        }
        if (_isSysActive.value) return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(this, "系统声音捕捉需要 Android 10+", Toast.LENGTH_SHORT).show()
            return
        }

        if (!MediaProjectionShareManager.hasResultData()) {
            val intent = Intent(this, ScreenCaptureActivity::class.java).apply {
                putExtra("requester", "STT")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            startActivity(intent)
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 2

        try {
            updateNotificationForMediaProjection()

            val projection = MediaProjectionShareManager.acquireMediaProjection(this, "STT")
            if (projection == null) {
                MediaProjectionShareManager.clearResultData()
                val intent = Intent(this, ScreenCaptureActivity::class.java).apply {
                    putExtra("requester", "STT")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
                startActivity(intent)
                return
            }

            val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            sysAudioRecord = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(16000)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build())
                .setBufferSizeInBytes(bufferSize)
                .build()

            if (sysAudioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Toast.makeText(this, "系统声音初始化失败", Toast.LENGTH_SHORT).show()
                sysAudioRecord?.release()
                sysAudioRecord = null
                return
            }

            sysAudioRecord?.startRecording()
            _isSysActive.value = true
            ensureRecognitionJobRunning()
        } catch (e: SecurityException) {
            e.printStackTrace()
            Toast.makeText(this, "系统声音录制权限异常: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "启动系统声音识别失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ensureRecognitionJobRunning() {
        if (recognitionJob?.isActive == true) return

        recognitionJob = lifecycleScope.launch(Dispatchers.IO) {
            val buffer = ShortArray(1600)
            Log.d("VoskService", "Recognition job started")
            while (isActive && (_isMicActive.value || _isSysActive.value)) {
                val rec = voskRecognizer ?: break
                
                // 处理麦克风数据
                micAudioRecord?.let { mic ->
                    if (mic.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        val read = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            mic.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                        } else {
                            mic.read(buffer, 0, buffer.size)
                        }
                        if (read > 0) {
                            processAudioBuffer(rec, buffer, read)
                        }
                    }
                }
                
                // 处理系统音频数据
                sysAudioRecord?.let { sys ->
                    if (sys.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        val read = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            sys.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                        } else {
                            sys.read(buffer, 0, buffer.size)
                        }
                        if (read > 0) {
                            processAudioBuffer(rec, buffer, read)
                        }
                    }
                }
                
                // 如果两个都没在录音，稍微休息下避免空转
                if (micAudioRecord == null && sysAudioRecord == null) {
                    delay(100)
                }
            }
            Log.d("VoskService", "Recognition job stopped")
        }
    }

    private suspend fun processAudioBuffer(rec: Recognizer, buffer: ShortArray, read: Int) {
        val isFinal = VoskHelper.acceptWaveForm(rec, buffer, read)
        if (isFinal) {
            val resultStr = VoskHelper.getResult(rec)
            val finalText = VoskResultParser.parseResult(resultStr)
            if (finalText.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    _recognizedText.value += finalText + " "
                    _partialText.value = "" // 最终结果出来后清空部分识别结果
                    
                    // 如果开启了实时翻译，执行翻译逻辑
                    if (_isTranslationEnabled.value) {
                        finalTranslationJob?.cancel()
                        partialTranslationJob?.cancel() // 最终结果出来了，取消之前的所有部分翻译推理
                        
                        val contextText = _recognizedText.value.dropLast(finalText.length + 1)
                        finalTranslationJob = lifecycleScope.launch(Dispatchers.IO) {
                            val translated = translateWithContext(
                                finalText,
                                contextText,
                                _sourceLanguage.value,
                                _targetLanguage.value
                            )
                            withContext(Dispatchers.Main) {
                                _translatedText.value += translated + " "
                                _partialTranslatedText.value = ""
                            }
                        }
                    }

                    if (_isAutoSend.value) {
                        FunctionEnhanceAccessibilityServices.instance?.let { service ->
                            if (service.isInputFocused()) {
                                service.injectText(finalText, settingsManager.removeSpaces)
                            }
                        }
                    }
                }
            }
        } else {
            val partialStr = VoskHelper.getPartialResult(rec)
            val partialText = VoskResultParser.parsePartialResult(partialStr)
            if (partialText.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    val threshold = settingsManager.sttPartialConfirmThreshold
                    if (partialText.length > threshold) {
                        // 优化：如果超过阈值个字符，视为已经识别并移入最终文本，防止中间状态过长
                        _recognizedText.value += partialText + " "
                        _partialText.value = ""
                        
                        if (_isTranslationEnabled.value) {
                            // 确定时额外执行一次完整翻译，确保连贯性
                            finalTranslationJob?.cancel()
                            partialTranslationJob?.cancel() // 确认部分结果时，取消正在进行的中间态推理
                            
                            val contextText = _recognizedText.value.dropLast(partialText.length + 1)
                            finalTranslationJob = lifecycleScope.launch(Dispatchers.IO) {
                                val translated = translateWithContext(
                                    partialText,
                                    contextText,
                                    _sourceLanguage.value,
                                    _targetLanguage.value
                                )
                                withContext(Dispatchers.Main) {
                                    _translatedText.value += translated + " "
                                    _partialTranslatedText.value = ""
                                }
                            }
                        }
                        
                        // 重置识别器，开始下一段识别
                        withContext(Dispatchers.IO) {
                            rec.reset()
                        }
                    } else {
                        _partialText.value = partialText
                        
                        // 如果开启了实时翻译，对部分结果也进行翻译
                        if (_isTranslationEnabled.value) {
                            partialTranslationJob?.cancel()
                            // 如果正在进行最终翻译，且当前是 AI 模式，则跳过部分翻译，优先保证最终翻译的负载
                            val isAiMode = settingsManager.transEngine == "LLM" && _sttTranslationEngine.value == "LLM"
                            if (isAiMode && finalTranslationJob?.isActive == true) {
                                // Skip partial translation to save AI resources
                            } else {
                                val contextText = _recognizedText.value
                                partialTranslationJob = lifecycleScope.launch(Dispatchers.IO) {
                                    val translated = translateWithContext(
                                        partialText,
                                        contextText,
                                        _sourceLanguage.value,
                                        _targetLanguage.value
                                    )
                                    withContext(Dispatchers.Main) {
                                        _partialTranslatedText.value = translated
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 带上下文的翻译逻辑
     */
    private suspend fun translateWithContext(
        text: String,
        fullContext: String,
        sourceLang: String,
        targetLang: String
    ): String {
        // 只有当全局引擎设为 LLM，且 STT 局部引擎也设为 LLM 时，才尝试使用 AI 翻译
        val useAi = settingsManager.transEngine == "LLM" && _sttTranslationEngine.value == "LLM"
        
        if (useAi && _isLlmModelLoaded.value) {
            // 使用 LLM 翻译前，必须显式中止之前的推理，防止负载爆炸
            translationManager.abort()
            delay(100) // 给 Native 层一点喘息时间
            
            // 使用 LLM 翻译
            // 注意：TranslationManager 的 translateOneOff 目前不支持传入 sourceLang，但支持 targetLang
            // 我们将 sourceLang 转换成中文名称传入
            val targetLangName = when(targetLang) {
                "zh" -> "中文"
                "en" -> "英语"
                "ja" -> "日语"
                "ko" -> "韩语"
                else -> "中文"
            }
            val sourceLangName = when(sourceLang) {
                "en" -> "英语"
                "zh" -> "中文"
                "ja" -> "日语"
                "ko" -> "韩语"
                else -> "自动检测"
            }

            val contextLen = settingsManager.sttTranslationContextLength
            val promptText = if (contextLen > 0 && fullContext.isNotBlank()) {
                val context = fullContext.trim().let {
                    if (it.length > contextLen) it.takeLast(contextLen) else it
                }
                "$context\n$text"
            } else {
                text
            }
            
            val fullTranslated = translationManager.translateOneOff(promptText, targetLangName, sourceLangName)
            
            // 处理 LLM 返回的带上下文的结果
            return if (contextLen > 0 && fullContext.isNotBlank()) {
                val parts = fullTranslated.split("\n")
                if (parts.size >= 2) parts.last().trim() else fullTranslated.trim()
            } else {
                fullTranslated.trim()
            }
        }

        // 回退到 Google 翻译 (机翻)
        val contextLen = settingsManager.sttTranslationContextLength
        if (contextLen <= 0 || fullContext.isBlank()) {
            return googleTranslationManager.translate(text, sourceLang, targetLang)
        }

        // 提取最后的 N 个字符作为上下文
        val context = fullContext.trim().let {
            if (it.length > contextLen) it.takeLast(contextLen) else it
        }

        // 使用换行符分隔上下文和当前文本，利用 NMT 模型对换行符的保留特性
        val combinedText = "$context\n$text"
        val fullTranslated = googleTranslationManager.translate(combinedText, sourceLang, targetLang)
        
        // 尝试按换行符拆分，取最后一部分作为当前文本的翻译
        val parts = fullTranslated.split("\n")
        return if (parts.size >= 2) {
            parts.last().trim()
        } else {
            // 如果模型把换行符吞了（罕见），则尝试简单的后向匹配（效果可能一般，但作为 fallback）
            fullTranslated.trim()
        }
    }

    private fun stopMicRecognition() {
        _isMicActive.value = false
        micAudioRecord?.stop()
        micAudioRecord?.release()
        micAudioRecord = null
        checkIfAllStopped()
    }

    private fun stopInternalAudioRecognition() {
        _isSysActive.value = false
        sysAudioRecord?.stop()
        sysAudioRecord?.release()
        sysAudioRecord = null
        
        MediaProjectionShareManager.releaseMediaProjection("STT")
        checkIfAllStopped()
    }

    private fun checkIfAllStopped() {
        if (!_isMicActive.value && !_isSysActive.value) {
            recognitionJob?.cancel()
            createNotification()
        }
    }

    private fun stopRecognition() {
        stopMicRecognition()
        stopInternalAudioRecognition()
    }

    private fun updateNotificationForMediaProjection() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AiTTS 悬浮窗服务")
            .setContentText("正在录制系统声音...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
            
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ (API 34+)
            try {
                startForeground(1, notification, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } catch (e: Exception) {
                Log.e("FloatingWindowService", "Failed to update FGS type to mediaProjection", e)
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-13 (API 29-33)
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        }
    }

    private fun createNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AiTTS 悬浮窗服务")
            .setContentText("语音识别服务运行中")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
            
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ (API 34+)
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-13 (API 29-33)
            // 修复：在 Android 13 上，初始就声明可能用到的所有类型（麦克风 + 媒体投影），
            // 避免后续升级类型被部分系统拦截。
            startForeground(1, notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return FunctionEnhanceAccessibilityServices.instance != null
    }

    private fun showFloatingWindow() {
        val params = WindowManager.LayoutParams(
            _windowWidth.value.dpToPx(this),
            _windowHeight.value.dpToPx(this),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        val composeView = ComposeView(this).apply {
            setContent {
                localtranslatorTheme {
                    val opacity = settingsManager.opacity
                    val textColor = settingsManager.getTextColor()
                    
                    // 使用 LaunchedEffect 监听状态变化并处理窗口尺寸，与翻译悬浮窗保持一致的逻辑和延时
                    LaunchedEffect(_isMinimized.value, _isTranslationEnabled.value) {
                        if (_isMinimized.value) {
                            // 缩小过程：
                            delay(150) 
                            params.width = 40.dpToPx(this@FloatingWindowService)
                            params.height = 40.dpToPx(this@FloatingWindowService)
                            // 缩小为球时，必须不可获取焦点，且不拦截外部触摸
                            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                                          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                          WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                            floatingView?.let { windowManager.updateViewLayout(it, params) }
                        } else {
                            // 展开过程：
                            delay(150)
                            params.width = _windowWidth.value.dpToPx(this@FloatingWindowService)
                            // 如果开启了翻译，增加高度以显示翻译结果
                            val baseHeight = _windowHeight.value
                            val targetHeight = if (_isTranslationEnabled.value) baseHeight + 150 else baseHeight
                            params.height = targetHeight.dpToPx(this@FloatingWindowService)
                            
                            // 展开为大窗口时
                            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                                          WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                                          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                          WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                            floatingView?.let { windowManager.updateViewLayout(it, params) }
                        }
                    }

                    // 动画逻辑，与翻译悬浮窗保持一致
                    AnimatedContent(
                        targetState = _isMinimized.value,
                        transitionSpec = {
                            if (targetState) {
                                // 缩小动画
                                (fadeIn(animationSpec = tween(150, delayMillis = 150)) + scaleIn(initialScale = 0.8f, animationSpec = tween(150, delayMillis = 150)))
                                    .togetherWith(fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.8f, animationSpec = tween(150)))
                            } else {
                                // 展开动画
                                (fadeIn(animationSpec = tween(150, delayMillis = 150)) + scaleIn(initialScale = 0.8f, animationSpec = tween(150, delayMillis = 150)))
                                    .togetherWith(fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.8f, animationSpec = tween(150)))
                            }
                        },
                        label = "STTFloatingWindowMode"
                    ) { minimized ->
                        if (minimized) {
                            // 缩小后的 STT 球
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = opacity))
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                params.x += dragAmount.x.toInt()
                                                params.y += dragAmount.y.toInt()
                                                floatingView?.let { windowManager.updateViewLayout(it, params) }
                                            }
                                        )
                                    }
                                    .clickable { _isMinimized.value = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.stt_icon),
                                    contentDescription = "STT Ball",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else {
                            FloatingControl(
                                modelName = _currentModelName.value,
                                downloadedModels = _downloadedModels,
                                recognizedText = _recognizedText.value,
                                partialText = _partialText.value,
                                isMicActive = _isMicActive.value,
                                isSysActive = _isSysActive.value,
                                isModelLoaded = _isModelLoaded.value,
                                isAutoSend = _isAutoSend.value,
                                isTranslationEnabled = _isTranslationEnabled.value,
                                sourceLanguage = _sourceLanguage.value,
                                targetLanguage = _targetLanguage.value,
                                translatedText = _translatedText.value,
                                partialTranslatedText = _partialTranslatedText.value,
                                googleTranslationManager = googleTranslationManager,
                                isLlmModelLoaded = _isLlmModelLoaded.value,
                                sttTranslationEngine = _sttTranslationEngine.value,
                                globalTransEngine = settingsManager.transEngine,
                                onToggleSttTransEngine = {
                                    _sttTranslationEngine.value = if (_sttTranslationEngine.value == "GOOGLE") "LLM" else "GOOGLE"
                                },
                                onLoadLlmModel = {
                                    val defaultModel = settingsManager.defaultModelName
                                    if (defaultModel != null && translationManager.isModelDownloaded(defaultModel)) {
                                        translationManager.loadModel(defaultModel, settingsManager.transContextLength, settingsManager.transUseMmap) { id ->
                                            if (id < 0) {
                                                Toast.makeText(this@FloatingWindowService, "AI 模型加载失败，请检查配置", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        // 未设置默认模型或模型未下载，跳转到主界面翻译页面
                                        Toast.makeText(this@FloatingWindowService, "请先下载并设置默认模型", Toast.LENGTH_SHORT).show()
                                        val intent = Intent(this@FloatingWindowService, MainActivity::class.java).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                            putExtra("navigate_to", "translation")
                                        }
                                        startActivity(intent)
                                    }
                                },
                                opacity = opacity,
                                textColor = textColor,
                                onMinimize = { _isMinimized.value = true },
                                onTextChanged = { _recognizedText.value = it },
                                onToggleMic = {
                                    if (_isMicActive.value) {
                                        stopMicRecognition()
                                    } else {
                                        startMicRecognition()
                                    }
                                },
                                onToggleSysAudio = {
                                    if (_isSysActive.value) {
                                        stopInternalAudioRecognition()
                                    } else {
                                        startInternalAudioRecognition()
                                    }
                                },
                                onToggleAutoSend = {
                                    if (isAccessibilityServiceEnabled()) {
                                        _isAutoSend.value = !_isAutoSend.value
                                    } else {
                                        // Will be handled by showAccessibilityDialog in FloatingControl
                                    }
                                },
                                onToggleTranslation = { _isTranslationEnabled.value = !_isTranslationEnabled.value },
                                onSelectSourceLanguage = { _sourceLanguage.value = it },
                                onSelectTargetLanguage = { _targetLanguage.value = it },
                                onSelectModel = { selectedModel ->
                                    loadSpecificModel(selectedModel)
                                },
                                onSendToMainTranslation = { text, src, target ->
                                    val intent = Intent(this@FloatingWindowService, MainActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                        putExtra("source_text", text)
                                        putExtra("source_lang", src)
                                        putExtra("target_lang", target)
                                        putExtra("navigate_to", "translation")
                                    }
                                    startActivity(intent)
                                },
                                onClear = { 
                                    _recognizedText.value = "" 
                                    _translatedText.value = ""
                                    _partialText.value = ""
                                    _partialTranslatedText.value = ""
                                    finalTranslationJob?.cancel()
                                    partialTranslationJob?.cancel()
                                },
                                onCopy = {
                                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Recognized Text", _recognizedText.value)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(this@FloatingWindowService, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                },
                                onSendToInput = {
                                    if (isAccessibilityServiceEnabled()) {
                                        FunctionEnhanceAccessibilityServices.instance?.injectText(_recognizedText.value, settingsManager.removeSpaces)
                                    } else {
                                        // Will be handled by showAccessibilityDialog in FloatingControl
                                    }
                                },
                                isAccessibilityEnabled = isAccessibilityServiceEnabled(),
                                onShare = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, _recognizedText.value)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    startActivity(Intent.createChooser(intent, "分享识别结果").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                },
                                onDrag = { dx, dy ->
                                    params.x += dx.toInt()
                                    params.y += dy.toInt()
                                    floatingView?.let { windowManager.updateViewLayout(it, params) }
                                },
                                onResize = { dw, dh ->
                                    val density = resources.displayMetrics.density
                                    val newWidthPx = (params.width + dw.toInt()).coerceIn(
                                        (200 * density).toInt(), 
                                        (600 * density).toInt()
                                    )
                                    val newHeightPx = (params.height + dh.toInt()).coerceIn(
                                        (150 * density).toInt(), 
                                        (800 * density).toInt()
                                    )
                                    
                                    params.width = newWidthPx
                                    params.height = newHeightPx
                                    _windowWidth.value = (newWidthPx / density).toInt()
                                    _windowHeight.value = (newHeightPx / density).toInt()
                                    
                                    floatingView?.let { windowManager.updateViewLayout(it, params) }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Setup lifecycle for ComposeView
        composeView.setViewTreeLifecycleOwner(this)
        composeView.setViewTreeSavedStateRegistryOwner(this)
        
        windowManager.addView(composeView, params)
        floatingView = composeView
    }

    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenCaptureReceiver)
        stopRecognition()
        floatingView?.let { windowManager.removeView(it) }
        voskModel?.close()
        MediaProjectionShareManager.releaseMediaProjection("STT", forceStop = true)
        finalTranslationJob?.cancel()
        partialTranslationJob?.cancel()
        
        // 发送停止广播，通知 UI 同步状态
        val intent = Intent("com.buildsession.localtranslator.VOICE_SERVICE_STOPPED")
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    companion object {
        const val CHANNEL_ID = "FloatingWindowServiceChannel"
    }
}

class PartialTextTransformation(private val partialText: String, private val partialColor: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (partialText.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val builder = AnnotatedString.Builder()
        builder.append(text.text)
        builder.pushStyle(SpanStyle(color = partialColor))
        builder.append(partialText)
        builder.pop()

        val transformedText = builder.toAnnotatedString()

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = offset
            override fun transformedToOriginal(offset: Int): Int = 
                if (offset > text.length) text.length else offset
        }

        return TransformedText(transformedText, offsetMapping)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingControl(
    modelName: String,
    downloadedModels: List<String>,
    recognizedText: String,
    partialText: String,
    isMicActive: Boolean,
    isSysActive: Boolean,
    isModelLoaded: Boolean,
    isAutoSend: Boolean,
    isTranslationEnabled: Boolean,
    sourceLanguage: String,
    targetLanguage: String,
    translatedText: String,
    partialTranslatedText: String,
    googleTranslationManager: GoogleTranslationManager,
    isLlmModelLoaded: Boolean,
    sttTranslationEngine: String,
    globalTransEngine: String,
    onToggleSttTransEngine: () -> Unit,
    onLoadLlmModel: () -> Unit,
    isAccessibilityEnabled: Boolean,
    opacity: Float,
    textColor: Color,
    onTextChanged: (String) -> Unit,
    onMinimize: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleSysAudio: () -> Unit,
    onToggleAutoSend: () -> Unit,
    onToggleTranslation: () -> Unit,
    onSelectSourceLanguage: (String) -> Unit,
    onSelectTargetLanguage: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onSendToMainTranslation: (String, String, String) -> Unit,
    onClear: () -> Unit,
    onCopy: () -> Unit,
    onSendToInput: () -> Unit,
    onShare: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit
) {
    var showModelMenu by remember { mutableStateOf(false) }
    var showSourceLangMenu by remember { mutableStateOf(false) }
    var showLangMenu by remember { mutableStateOf(false) }
    var showSendMenu by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val googleDownloadedModels by googleTranslationManager.downloadedModels.collectAsState()
    
    // 使用 TextFieldValue 管理光标，以便在 STT 更新时自动滚动到末尾
    var textFieldValue by remember { 
        mutableStateOf(TextFieldValue(recognizedText, TextRange(recognizedText.length))) 
    }
    
    // 翻译结果的 TextFieldValue
    var translatedFieldValue by remember {
        mutableStateOf(TextFieldValue(translatedText, TextRange(translatedText.length)))
    }
    
    // 当外部 STT 识别结果更新时，同步到内部 TextFieldValue 并将光标移至末尾
    LaunchedEffect(recognizedText) {
        if (textFieldValue.text != recognizedText) {
            textFieldValue = textFieldValue.copy(
                text = recognizedText,
                selection = TextRange(recognizedText.length)
            )
        }
    }

    // 当翻译结果更新时，同步到内部 TextFieldValue 并将光标移至末尾
    LaunchedEffect(translatedText) {
        if (translatedFieldValue.text != translatedText) {
            translatedFieldValue = translatedFieldValue.copy(
                text = translatedText,
                selection = TextRange(translatedText.length)
            )
        }
    }

    // 记住转换器以优化性能
    val partialTransformation = remember(partialText, textColor) {
        PartialTextTransformation(partialText, textColor.copy(alpha = 0.5f))
    }

    // 翻译结果的转换器
    val translatedTransformation = remember(partialTranslatedText, textColor) {
        PartialTextTransformation(partialTranslatedText, textColor.copy(alpha = 0.5f))
    }

    Surface(
        modifier = Modifier
            .fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = opacity),
        contentColor = textColor
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxSize()
            ) {
                // Top: Model Name and Mic Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.x, dragAmount.y)
                            }
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onMinimize, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    painter = painterResource(id = R.drawable.stt_icon),
                                    contentDescription = "Minimize",
                                    tint = textColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(4.dp))

                            Text(
                                text = "模型: $modelName",
                                style = MaterialTheme.typography.labelMedium,
                                color = textColor.copy(alpha = 0.7f),
                                maxLines = 1,
                                modifier = Modifier
                                    .weight(1f)
                                    .basicMarquee()
                                    .clickable { showModelMenu = true }
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showModelMenu,
                            onDismissRequest = { showModelMenu = false },
                            modifier = Modifier.background(Color.DarkGray)
                        ) {
                            downloadedModels.forEach { name ->
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            text = name, 
                                            color = if (name == modelName) MaterialTheme.colorScheme.primary else Color.White,
                                            fontSize = 12.sp
                                        ) 
                                    },
                                    onClick = {
                                        onSelectModel(name)
                                        showModelMenu = false
                                    }
                                )
                            }
                            if (downloadedModels.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("无可用模型", color = Color.Gray, fontSize = 12.sp) },
                                    onClick = { showModelMenu = false }
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onMinimize,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloseFullscreen,
                                contentDescription = "Minimize",
                                tint = textColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = onToggleSysAudio,
                            enabled = isModelLoaded,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RadioButtonChecked,
                                contentDescription = "System Audio",
                                tint = if (isSysActive) Color.Red else textColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = onToggleMic,
                            enabled = isModelLoaded,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Mic",
                                tint = if (isMicActive) Color.Red else textColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Middle: Recognition Result
                TextField(
                    value = textFieldValue,
                    onValueChange = {
                        textFieldValue = it
                        if (it.text != recognizedText) {
                            onTextChanged(it.text)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        color = textColor
                    ),
                    visualTransformation = partialTransformation,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = textColor,
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor
                    ),
                    placeholder = { 
                        if (partialText.isEmpty()) {
                            Text("识别结果...", color = textColor.copy(alpha = 0.5f), fontSize = 12.sp)
                        }
                    }
                )

                // Translation Result Box (Expandable)
                AnimatedVisibility(
                    visible = isTranslationEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .padding(top = 8.dp)
                    ) {
                        HorizontalDivider(color = textColor.copy(alpha = 0.2f), thickness = 0.5.dp)
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "实时翻译",
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor.copy(alpha = 0.5f)
                            )
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.weight(1f)
                            ) {
                                // STT 专用引擎选择 (仅当全局引擎为 LLM 时显示)
                                if (globalTransEngine == "LLM") {
                                    val engineText = if (sttTranslationEngine == "LLM") 
                                        stringResource(id = R.string.stt_trans_engine_ai) 
                                    else 
                                        stringResource(id = R.string.stt_trans_engine_machine)
                                    
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                            .clickable { onToggleSttTransEngine() }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = engineText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = textColor,
                                            fontSize = 9.sp
                                        )
                                    }
                                }

                                // 源语言选择器
                                Box {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                            .clickable { showSourceLangMenu = true }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        val langName = googleTranslationManager.getAvailableLanguages()
                                            .find { it.second == sourceLanguage }?.first ?: sourceLanguage
                                        Text(
                                            text = langName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ExpandMore,
                                            contentDescription = "Select Source Language",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    
                                    DropdownMenu(
                                        expanded = showSourceLangMenu,
                                        onDismissRequest = { showSourceLangMenu = false },
                                        modifier = Modifier.background(Color.DarkGray)
                                    ) {
                                        googleDownloadedModels.forEach { langCode ->
                                            val name = googleTranslationManager.getAvailableLanguages()
                                                .find { it.second == langCode }?.first ?: langCode
                                            DropdownMenuItem(
                                                text = { 
                                                    Text(
                                                        text = name, 
                                                        color = if (langCode == sourceLanguage) MaterialTheme.colorScheme.primary else Color.White,
                                                        fontSize = 12.sp
                                                    ) 
                                                },
                                                onClick = {
                                                    onSelectSourceLanguage(langCode)
                                                    showSourceLangMenu = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Arrow",
                                    tint = textColor.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .padding(horizontal = 2.dp)
                                )

                                // 目标语言选择器
                                Box {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                            .clickable { showLangMenu = true }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        val langName = googleTranslationManager.getAvailableLanguages()
                                            .find { it.second == targetLanguage }?.first ?: targetLanguage
                                        Text(
                                            text = langName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ExpandMore,
                                            contentDescription = "Select Target Language",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    
                                    DropdownMenu(
                                        expanded = showLangMenu,
                                        onDismissRequest = { showLangMenu = false },
                                        modifier = Modifier.background(Color.DarkGray)
                                    ) {
                                        googleDownloadedModels.forEach { langCode ->
                                            val name = googleTranslationManager.getAvailableLanguages()
                                                .find { it.second == langCode }?.first ?: langCode
                                            DropdownMenuItem(
                                                text = { 
                                                    Text(
                                                        text = name, 
                                                        color = if (langCode == targetLanguage) MaterialTheme.colorScheme.primary else Color.White,
                                                        fontSize = 12.sp
                                                    ) 
                                                },
                                                onClick = {
                                                    onSelectTargetLanguage(langCode)
                                                    showLangMenu = false
                                                }
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        onSendToMainTranslation(
                                            recognizedText,
                                            googleTranslationManager.getAvailableLanguages().find { it.second == sourceLanguage }?.first ?: sourceLanguage,
                                            googleTranslationManager.getAvailableLanguages().find { it.second == targetLanguage }?.first ?: targetLanguage
                                        )
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = "Open in Main App",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        // 翻译内容区域：如果选择了 AI 翻译但模型未加载，显示加载按钮
                        val showLoadButton = globalTransEngine == "LLM" && sttTranslationEngine == "LLM" && !isLlmModelLoaded
                        
                        if (showLoadButton) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Button(
                                    onClick = onLoadLlmModel,
                                    modifier = Modifier.padding(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                    )
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(id = R.string.stt_trans_load_ai_model), fontSize = 12.sp)
                                }
                            }
                        } else {
                            TextField(
                                value = translatedFieldValue,
                                onValueChange = {
                                    translatedFieldValue = it
                                }, // Read-only but need onValueChange for TextFieldValue
                                readOnly = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    color = textColor
                                ),
                                visualTransformation = translatedTransformation,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = Color.Transparent, // Hide cursor for read-only
                                    focusedTextColor = textColor,
                                    unfocusedTextColor = textColor
                                ),
                                placeholder = { 
                                    if (partialTranslatedText.isEmpty()) {
                                        val hint = if (globalTransEngine == "LLM" && sttTranslationEngine == "LLM")
                                            stringResource(id = R.string.status_ready)
                                        else
                                            "翻译结果..."
                                        Text(hint, color = textColor.copy(alpha = 0.5f), fontSize = 12.sp)
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Bottom: Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 实时翻译开关
                        IconButton(
                            onClick = onToggleTranslation,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = "Translation Toggle",
                                tint = if (isTranslationEnabled) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = textColor.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Clear",
                                tint = textColor.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onToggleAutoSend,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isAutoSend) Icons.AutoMirrored.Filled.Send else Icons.Default.FiberManualRecord,
                                contentDescription = "Auto Send",
                                tint = if (isAutoSend) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        IconButton(onClick = onSendToInput, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = textColor.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Resize handle at bottom right
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Resize",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .graphicsLayer {
                        rotationZ = -45f
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onResize(dragAmount.x, dragAmount.y)
                        }
                    },
                tint = textColor.copy(alpha = 0.5f)
            )

            // Custom Accessibility Permission Dialog Overlay
            if (showAccessibilityDialog) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .background(Color.DarkGray, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "需要无障碍权限",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "发送文本和 AUTO 模式需要此权限",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(onClick = { showAccessibilityDialog = false }) {
                                Text("取消", color = Color.Gray, fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    showAccessibilityDialog = false
                                    val intent = Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("去开启", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
