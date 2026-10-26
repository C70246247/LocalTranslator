package com.buildsession.localtranslator.translation

import android.content.ComponentName
import android.provider.Settings as AndroidSettings
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Activity
import android.content.pm.ServiceInfo
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.positionChange
import android.graphics.Rect
import android.graphics.Point
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.media.projection.MediaProjectionManager
import android.media.projection.MediaProjection
import android.hardware.display.VirtualDisplay
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.ui.zIndex
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextOverflow
import com.buildsession.localtranslator.R
import com.buildsession.localtranslator.TranslationLanguages
import com.buildsession.localtranslator.FunctionEnhanceAccessibilityServices
import com.buildsession.localtranslator.HedgehogLoading
import com.buildsession.localtranslator.ui.theme.localtranslatorTheme
import com.buildsession.localtranslator.util.AccessibilityUtils
import com.buildsession.localtranslator.util.SettingsManager
import com.buildsession.localtranslator.util.MediaProjectionShareManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import androidx.compose.ui.res.stringResource
import com.buildsession.localtranslator.ocr.OcrManager
import com.buildsession.localtranslator.ocr.OcrResult
import org.nehuatl.llamacpp.LlamaHelper

enum class TranslationStatus {
    IDLE,
    WAITING,
    TRANSLATING,
    COMPLETED
}

data class OcrOverlayResult(
    val ocrResult: OcrResult,
    var translatedText: String? = null,
    var status: TranslationStatus = TranslationStatus.IDLE,
    val displayRect: Rect // Mapped to screen pixels
)

class TranslationFloatingService : LifecycleService(), SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var highlightOverlayView: View? = null
    private var fullScreenOverlayView: View? = null
    private var fullScreenParams: WindowManager.LayoutParams? = null
    private var highlightParams: WindowManager.LayoutParams? = null
    private lateinit var translationManager: TranslationManager
    private lateinit var googleTranslationManager: GoogleTranslationManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var ocrManager: OcrManager
    
    private var translateWatchdogJob: Job? = null
    
    // 移除私有的 mediaProjection 和 screenCaptureResultData，改用 MediaProjectionShareManager

    private val screenCaptureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.buildsession.localtranslator.SCREEN_CAPTURE_PERMISSION_GRANTED") {
                val requester = intent.getStringExtra("requester")
                if (requester == "OCR") {
                    val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra("result_data", Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra<Intent>("result_data")
                    }
                    if (data != null) {
                        MediaProjectionShareManager.setResultData(data)
                        performScreenOcr()
                    }
                }
            }
        }
    }

    private val _highlightBounds = mutableStateOf<Rect?>(null)
    private val _highlightText = mutableStateOf<String?>(null)

    private val _sourceText = mutableStateOf("")
    private val _translatedText = mutableStateOf("")
    private val _sourceLang = mutableStateOf("")
    private val _targetLang = mutableStateOf("")
    private val _isTranslating = mutableStateOf(false)
    private val _isLoadingModel = mutableStateOf(false)
    private val _isMinimized = mutableStateOf(false)
    private var _pendingReverseTranslate = false

    private val _windowWidth = mutableStateOf(300)
    private val _windowHeight = mutableStateOf(250)

    // Full Screen Translation States
    private val _isFullScreenMode = mutableStateOf(false)
    private val _ocrOverlayResults = mutableStateListOf<OcrOverlayResult>()
    private val _isOcrProcessing = mutableStateOf(false)
    private var _ocrScaleFactor = 1.0f
    
    // Manual Merging States
    private val _isManualMergingMode = mutableStateOf(false)
    private val _selectedMergeIndices = mutableStateListOf<Int>()
    
    private val translationQueue = kotlinx.coroutines.channels.Channel<OcrOverlayResult>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    private var translationQueueJob: Job? = null

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        translationManager = TranslationManager(applicationContext)
        googleTranslationManager = GoogleTranslationManager(applicationContext)
        settingsManager = SettingsManager(this)
        ocrManager = OcrManager(this)
        _sourceLang.value = settingsManager.transSourceLang
        _targetLang.value = settingsManager.transTargetLang
        savedStateRegistryController.performRestore(null)
        createNotificationChannel()
        createNotification()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val filter = IntentFilter("com.buildsession.localtranslator.SCREEN_CAPTURE_PERMISSION_GRANTED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenCaptureReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenCaptureReceiver, filter)
        }

        initHighlightOverlay()
        initFullScreenOverlay()
        showFloatingWindow()
        startTranslationQueue()

        // 自动启用无障碍服务逻辑
        if (settingsManager.mergedAutoAccessibility) {
            AccessibilityUtils.enableAccessibilityService(this)
        }

        // Observe LLM events
        lifecycleScope.launch {
            translationManager.llmFlow.collect { event ->
                // 如果当前正在使用 Google 引擎，忽略 LLM 事件以避免状态冲突
                if (settingsManager.transEngine == "GOOGLE") return@collect
                
                when (event) {
                    is LlamaHelper.LLMEvent.Started -> {
                        _isTranslating.value = true
                        _translatedText.value = ""
                    }
                    is LlamaHelper.LLMEvent.Ongoing -> {
                        _translatedText.value += event.word
                    }
                    is LlamaHelper.LLMEvent.Done -> {
                        _isTranslating.value = false
                        translateWatchdogJob?.cancel()
                    }
                    is LlamaHelper.LLMEvent.Error -> {
                        _isTranslating.value = false
                        translateWatchdogJob?.cancel()
                        Log.e("FloatingService", "Translation Error: ${event.message}")
                        Toast.makeText(this@TranslationFloatingService, "翻译出错: ${event.message}", Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Translation Service Channel"
            val descriptionText = "Notifications for Translation Floating Window Service"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("离线翻译悬浮窗")
            .setContentText("翻译服务运行中")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
            
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ (API 34+) 显式指定类型，初始只开启 specialUse，避免 mediaProjection 报错
            startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-13 (API 29-33)
            // 修复：在 Android 13 上，必须初始就声明 mediaProjection 类型，
            // 否则后续在 updateNotificationForMediaProjection 中升级类型可能被部分系统（如 OriginOS/HyperOS）拦截。
            startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(2, notification)
        }
    }

    private fun updateNotificationForMediaProjection() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("离线翻译悬浮窗")
            .setContentText("正在识别屏幕...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
            
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ (API 34+)
            try {
                startForeground(2, notification, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } catch (e: Exception) {
                Log.e("FloatingService", "Failed to update FGS type to mediaProjection", e)
                // 如果升级失败，至少保持 specialUse 以防服务被杀
                startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-13 (API 29-33)
            // 直接指定 mediaProjection 类型即可
            startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        _isMinimized.value = false // 每次显式启动都确保不处于最小化状态
        return super.onStartCommand(intent, flags, startId)
    }

    private fun initFullScreenOverlay() {
        fullScreenParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            // 关键：适配刘海屏/切口屏，确保内容可以延伸到切口区域，避免坐标偏移
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        fullScreenOverlayView = ComposeView(this).apply {
            // 强制不适配系统窗口，确保 (0,0) 始终是屏幕绝对顶点，与 MediaProjection 捕获的原始图像对齐
            fitsSystemWindows = false
            
            setOnKeyListener { v, keyCode, event ->
                if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                    if (event.action == android.view.KeyEvent.ACTION_UP) {
                        exitFullScreenMode()
                    }
                    true
                } else {
                    false
                }
            }
            isFocusableInTouchMode = true
            requestFocus()
            
            setContent {
                localtranslatorTheme {
                    FullScreenTranslationOverlay(
                        results = _ocrOverlayResults,
                        isOcrProcessing = _isOcrProcessing.value,
                        bgOpacity = settingsManager.ocrFullScreenBgOpacity,
                        selectedMergeIndices = _selectedMergeIndices,
                        isManualMergingMode = _isManualMergingMode.value,
                        onTranslateBlock = { block -> translateOcrBlock(block) },
                        onTranslateAll = { translateAllBlocks() },
                        onExit = { exitFullScreenMode() },
                        onStartMerge = { index -> 
                            _isManualMergingMode.value = true
                            if (!_selectedMergeIndices.contains(index)) {
                                _selectedMergeIndices.add(index)
                            }
                        },
                        onAddMergeIndex = { index ->
                            if (_isManualMergingMode.value && !_selectedMergeIndices.contains(index)) {
                                _selectedMergeIndices.add(index)
                            }
                        },
                        onFinishMerge = { mergeSelectedBlocks() }
                    )
                }
            }
        }
        
        (fullScreenOverlayView as ComposeView).setViewTreeLifecycleOwner(this)
        (fullScreenOverlayView as ComposeView).setViewTreeSavedStateRegistryOwner(this)
    }

    private fun getRealScreenSize(): Point {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val point = Point()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.currentWindowMetrics
            val bounds = metrics.bounds
            point.x = bounds.width()
            point.y = bounds.height()
        } else {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealSize(point)
        }
        return point
    }

    private fun enterFullScreenMode() {
        _isFullScreenMode.value = true
        floatingView?.visibility = View.GONE
        
        // 如果开启了无障碍绘制，且无障碍服务已启动，则委托给无障碍服务绘制
        if (settingsManager.ocrUseAccessibilityOverlay) {
            val accessibilityService = FunctionEnhanceAccessibilityServices.instance
            if (accessibilityService != null) {
                accessibilityService.showOcrOverlay(
                    results = _ocrOverlayResults,
                    isOcrProcessing = _isOcrProcessing.value,
                    bgOpacity = settingsManager.ocrFullScreenBgOpacity,
                    onTranslateBlock = { block -> translateOcrBlock(block) },
                    onTranslateAll = { translateAllBlocks() },
                    onExit = { exitFullScreenMode() }
                )
                return
            } else {
                Toast.makeText(this, "无障碍服务未启动，将使用普通悬浮窗绘制", Toast.LENGTH_SHORT).show()
            }
        }

        // 保持 initFullScreenOverlay 中设置的标志位，确保全屏覆盖和坐标对齐
        try {
            windowManager.addView(fullScreenOverlayView, fullScreenParams)
            fullScreenOverlayView?.requestFocus()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun exitFullScreenMode() {
        _isFullScreenMode.value = false
        
        // Stop ongoing inference and clear the translation queue
        translationManager.abort()
        while (translationQueue.tryReceive().isSuccess) {
            // Drain the channel
        }
        
        _ocrOverlayResults.clear()

        // 无论何种模式，都尝试关闭无障碍覆盖层
        FunctionEnhanceAccessibilityServices.instance?.hideOcrOverlay()

        try {
            windowManager.removeView(fullScreenOverlayView)
        } catch (e: Exception) {
            // ignore
        }
        floatingView?.visibility = View.VISIBLE
        createNotification() // Restore original notification
    }

    private fun mergeSelectedBlocks() {
        if (_selectedMergeIndices.isEmpty()) {
            _isManualMergingMode.value = false
            return
        }

        val sortedIndices = _selectedMergeIndices.sortedBy { _ocrOverlayResults[it].displayRect.top }
        val selectedBlocks = sortedIndices.map { _ocrOverlayResults[it] }

        if (selectedBlocks.size < 2) {
            _selectedMergeIndices.clear()
            _isManualMergingMode.value = false
            return
        }

        // Combine text
        val mergedText = selectedBlocks.joinToString(separator = " ") { it.ocrResult.text }
        
        // Calculate bounding box
        var minLeft = Int.MAX_VALUE
        var minTop = Int.MAX_VALUE
        var maxRight = Int.MIN_VALUE
        var maxBottom = Int.MIN_VALUE
        
        selectedBlocks.forEach { 
            minLeft = minOf(minLeft, it.displayRect.left)
            minTop = minOf(minTop, it.displayRect.top)
            maxRight = maxOf(maxRight, it.displayRect.right)
            maxBottom = maxOf(maxBottom, it.displayRect.bottom)
        }
        
        val mergedDisplayRect = Rect(minLeft, minTop, maxRight, maxBottom)
        
        // Create new OcrResult (Points are scaled back for consistency if needed, but displayRect is enough for UI)
        val mergedPoints = listOf<Point>(
            Point(minLeft, minTop), Point(maxRight, minTop),
            Point(maxRight, maxBottom), Point(minLeft, maxBottom)
        )
        val mergedOcrResult = OcrResult(mergedText, selectedBlocks.map { it.ocrResult.confidence }.average().toFloat(), mergedPoints)
        val newOverlayResult = OcrOverlayResult(mergedOcrResult, displayRect = mergedDisplayRect)

        // Update the list: remove old, add new
        val indicesToRemove = _selectedMergeIndices.sortedDescending()
        indicesToRemove.forEach { _ocrOverlayResults.removeAt(it) }
        _ocrOverlayResults.add(newOverlayResult)

        // Reset state
        _selectedMergeIndices.clear()
        _isManualMergingMode.value = false
        
        // Vibrate to confirm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(50)
        }
    }

    private fun startTranslationQueue() {
        translationQueueJob?.cancel()
        translationQueueJob = lifecycleScope.launch {
            for (block in translationQueue) {
                // Find current block in list to update its status
                val index = _ocrOverlayResults.indexOfFirst { it.ocrResult == block.ocrResult }
                if (index != -1) {
                    _ocrOverlayResults[index] = _ocrOverlayResults[index].copy(
                        status = TranslationStatus.TRANSLATING,
                        translatedText = "" // Initialize empty for streaming
                    )
                    
                    val currentBlockText = block.ocrResult.text
                    val targetLang = _targetLang.value
                    val sourceLang = _sourceLang.value
                    
                    if (settingsManager.transEngine == "GOOGLE") {
                        // Google ML Kit Translation (Non-streaming)
                        val result = googleTranslationManager.translate(currentBlockText, sourceLang, targetLang)
                        val finalIndex = _ocrOverlayResults.indexOfFirst { it.ocrResult == block.ocrResult }
                        if (finalIndex != -1) {
                            _ocrOverlayResults[finalIndex] = _ocrOverlayResults[finalIndex].copy(
                                translatedText = result,
                                status = TranslationStatus.COMPLETED
                            )
                        }
                    } else {
                        // LLM Translation (Streaming)
                        // Collect from flow while translating
                        val collectorJob = launch {
                            translationManager.llmFlow.collect { event ->
                                if (event is LlamaHelper.LLMEvent.Ongoing) {
                                    val updatedIndex = _ocrOverlayResults.indexOfFirst { it.ocrResult == block.ocrResult }
                                    if (updatedIndex != -1) {
                                        val currentText = _ocrOverlayResults[updatedIndex].translatedText ?: ""
                                        _ocrOverlayResults[updatedIndex] = _ocrOverlayResults[updatedIndex].copy(
                                            translatedText = currentText + event.word
                                        )
                                    }
                                }
                            }
                        }

                        // Start the translation process
                        translationManager.translate(currentBlockText, targetLang, sourceLang)
                        
                        // Wait for completion or error
                        translationManager.llmFlow.first { it is LlamaHelper.LLMEvent.Done || it is LlamaHelper.LLMEvent.Error }
                        
                        collectorJob.cancel()
                        
                        val finalIndex = _ocrOverlayResults.indexOfFirst { it.ocrResult == block.ocrResult }
                        if (finalIndex != -1) {
                            _ocrOverlayResults[finalIndex] = _ocrOverlayResults[finalIndex].copy(
                                status = TranslationStatus.COMPLETED
                            )
                        }
                    }
                }
            }
        }
    }

    private fun translateOcrBlock(block: OcrOverlayResult) {
        if (block.status != TranslationStatus.IDLE) return
        
        // Update status to WAITING
        val index = _ocrOverlayResults.indexOf(block)
        if (index != -1) {
            val updatedBlock = block.copy(status = TranslationStatus.WAITING)
            _ocrOverlayResults[index] = updatedBlock
            translationQueue.trySend(updatedBlock)
        }
    }

    private fun translateAllBlocks() {
        // Sort by vertical position (top) to translate from top to bottom
        // If tops are similar, sort by left to follow natural reading order
        _ocrOverlayResults
            .filter { it.status == TranslationStatus.IDLE && it.translatedText == null }
            .sortedWith(compareBy({ it.displayRect.top }, { it.displayRect.left }))
            .forEach { block ->
                translateOcrBlock(block)
            }
    }

    private fun initHighlightOverlay() {
        highlightParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, // 显式增加，确保不拦截区域外事件
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            // 关键：不设置任何可能导致焦点锁定的 alpha 或 dimAmount
        }

        highlightOverlayView = ComposeView(this).apply {
            setContent {
                val rect = _highlightBounds.value
                val density = LocalDensity.current
                val textColor = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color.Black
                
                if (rect != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            // 使用更醒目的颜色和更粗的边框
                            drawRoundRect(
                                color = Color(0xFF00B0FF), // 亮蓝色
                                topLeft = Offset(rect.left.toFloat(), rect.top.toFloat()),
                                size = androidx.compose.ui.geometry.Size(rect.width().toFloat(), rect.height().toFloat()),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                                style = Stroke(width = 5.dp.toPx())
                            )
                        }
                        // Optional: show a small text preview near the box
                        val text = _highlightText.value
                        if (!text.isNullOrEmpty()) {
                            val previewText = remember(text) { if (text.length > 25) text.take(25) + "..." else text }
                            // 确保预览文字不会超出屏幕顶部
                            val yOffset = remember(rect) { if (rect.top > 100) (rect.top - 40) else (rect.bottom + 10) }
                            
                            Box(
                                modifier = Modifier
                                    .absoluteOffset(
                                        x = with(density) { rect.left.toDp() },
                                        y = with(density) { yOffset.toDp() }
                                    )
                                    .background(Color(0xFF00B0FF).copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    previewText, 
                                    color = Color.White, 
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Setup lifecycle
        (highlightOverlayView as ComposeView).setViewTreeLifecycleOwner(this)
        (highlightOverlayView as ComposeView).setViewTreeSavedStateRegistryOwner(this)
        
        // 注意：这里不再立即 addView，而是按需添加
    }

    private fun showFloatingWindow() {
        val params = WindowManager.LayoutParams(
            _windowWidth.value.dpToPx(this),
            _windowHeight.value.dpToPx(this),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or 
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START // Use TOP START for absolute coordinate management
            x = 100
            y = 500 // Start near bottom
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        val composeView = ComposeView(this).apply {
            setContent {
                localtranslatorTheme {
                    val opacity = settingsManager.opacity
                val textColor = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color.Black
                val loadedModelId by translationManager.loadedModelId.collectAsState()
                val sessionId by translationManager.sessionId.collectAsState()
                
                val opacityValue = remember(settingsManager.opacity) { settingsManager.opacity }
                val languages = remember { TranslationLanguages }

                // 使用 LaunchedEffect 监听会话变化并重置状态，防止旧模型的推理结果干扰
                LaunchedEffect(sessionId) {
                    _sourceText.value = ""
                    _translatedText.value = ""
                    _isTranslating.value = false
                }

                // 使用 LaunchedEffect 监听状态变化并处理窗口尺寸
                LaunchedEffect(_isMinimized.value) {
                    if (_isMinimized.value) {
                        // 缩小过程：
                        delay(150) 
                        params.width = 40.dpToPx(this@TranslationFloatingService)
                        params.height = 40.dpToPx(this@TranslationFloatingService)
                        // 缩小为球时，必须不可获取焦点，且不拦截外部触摸
                        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                                      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or 
                                      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                      WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        floatingView?.let { windowManager.updateViewLayout(it, params) }
                    } else {
                        // 展开过程：
                        delay(150)
                        params.width = _windowWidth.value.dpToPx(this@TranslationFloatingService)
                        params.height = _windowHeight.value.dpToPx(this@TranslationFloatingService)
                        // 展开为大窗口时，默认允许获取焦点，以便唤起输入法
                        // 关键：不设置 FLAG_NOT_FOCUSABLE 且不设置 FLAG_ALT_FOCUSABLE_IM
                        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or 
                                      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                      WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        floatingView?.let { windowManager.updateViewLayout(it, params) }
                    }
                }

                AnimatedContent(
                    targetState = _isMinimized.value,
                    transitionSpec = {
                        if (targetState) {
                            // 缩小动画：主界面缩放淡出 -> 悬浮球缩放淡入
                            (fadeIn(animationSpec = tween(150, delayMillis = 150)) + scaleIn(initialScale = 0.8f, animationSpec = tween(150, delayMillis = 150)))
                                .togetherWith(fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.8f, animationSpec = tween(150)))
                        } else {
                            // 展开动画：悬浮球缩放淡出 -> 主界面缩放淡入
                            (fadeIn(animationSpec = tween(150, delayMillis = 150)) + scaleIn(initialScale = 0.8f, animationSpec = tween(150, delayMillis = 150)))
                                .togetherWith(fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.8f, animationSpec = tween(150)))
                        }
                    },
                    label = "FloatingWindowMode"
                ) { minimized ->
                    if (minimized) {
                        // 迷你悬浮球：圆形
                        var isReadMode by remember { mutableStateOf(false) }
                            var longPressJob: Job? by remember { mutableStateOf(null) }
                            val coroutineScope = rememberCoroutineScope()
                            
                            // 记录开始拖拽前的位置，用于读取模式结束后归位
                            var startX by remember { mutableStateOf(0) }
                            var startY by remember { mutableStateOf(0) }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(if (isReadMode) Color(0xFF61D4FF).copy(alpha = opacity) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = opacity))
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown()
                                            
                                            // 记录初始位置
                                            startX = params.x
                                            startY = params.y

                                            longPressJob = coroutineScope.launch {
                                                delay(500)
                                                
                                                // 检查无障碍服务是否运行
                                                if (FunctionEnhanceAccessibilityServices.instance == null) {
                                                    Toast.makeText(this@TranslationFloatingService, "无障碍服务未启动，请手动开启以使用读屏功能", Toast.LENGTH_LONG).show()
                                                    isReadMode = false
                                                    return@launch
                                                }

                                                isReadMode = true
                                                
                                                // 动态添加全屏高亮遮罩，避免平时阻断交互
                                                try {
                                                    highlightOverlayView?.let { windowManager.addView(it, highlightParams) }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }

                                                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                                                } else {
                                                    vibrator.vibrate(50)
                                                }

                                                // 长按进入读屏模式后立即探测一次
                                                val cx = params.x + 20.dpToPx(context)
                                                val cy = params.y + 20.dpToPx(context)
                                                val res = FunctionEnhanceAccessibilityServices.instance?.findNearestTextNode(cx, cy)
                                                _highlightBounds.value = res?.second
                                                _highlightText.value = res?.first
                                            }
                                            
                                            var isDragging = false
                                            var lastSearchX = -1
                                            var lastSearchY = -1

                                            do {
                                                val event = awaitPointerEvent()
                                                val move = event.changes.firstOrNull()
                                                
                                                if (move != null && move.positionChange().getDistance() > 3f) {
                                                    if (!isReadMode && !isDragging) {
                                                        longPressJob?.cancel()
                                                        isDragging = true
                                                    }
                                                    
                                                    // Handle drag
                                                    val delta = move.positionChange()
                                                    params.x += delta.x.toInt()
                                                    params.y += delta.y.toInt()
                                                    floatingView?.let { windowManager.updateViewLayout(it, params) }
                                                    
                                                    move.consume()
                                                }

                                                if (isReadMode) {
                                                    val cx = params.x + 20.dpToPx(context)
                                                    val cy = params.y + 20.dpToPx(context)
                                                    
                                                    // 优化：只有位移超过一定阈值，或者静止时，才触发“寻找最近”
                                                    val distSinceLastSearch = Math.sqrt(Math.pow((cx - lastSearchX).toDouble(), 2.0) + Math.pow((cy - lastSearchY).toDouble(), 2.0))
                                                    
                                                    if (distSinceLastSearch > 15 || (move == null && distSinceLastSearch > 2)) {
                                                        val res = FunctionEnhanceAccessibilityServices.instance?.findNearestTextNode(cx, cy)
                                                        _highlightBounds.value = res?.second
                                                        _highlightText.value = res?.first
                                                        lastSearchX = cx
                                                        lastSearchY = cy
                                                    }
                                                }
                                            } while (event.changes.any { it.pressed })
                                            
                                            // UP event
                                            longPressJob?.cancel()
                                            if (isReadMode) {
                                                // 移除全屏遮罩，恢复系统交互
                                                try {
                                                    highlightOverlayView?.let { windowManager.removeView(it) }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }

                                                if (_highlightText.value != null) {
                                                    _sourceText.value = _highlightText.value!!
                                                    _isMinimized.value = false // Expand window
                                                } else {
                                                    Toast.makeText(this@TranslationFloatingService, "附近没有发现文本", Toast.LENGTH_SHORT).show()
                                                }
                                                
                                                // 优化：读取模式结束后自动回到原位
                                                params.x = startX
                                                params.y = startY
                                                floatingView?.let { windowManager.updateViewLayout(it, params) }
                                                
                                                isReadMode = false
                                                _highlightBounds.value = null
                                                _highlightText.value = null
                                            } else if (!isDragging) {
                                                // It was a tap!
                                                _isMinimized.value = false
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                            Image(
                                painter = painterResource(id = R.drawable.translationball),
                                contentDescription = "恢复",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        TranslationFloatingControl(
                            sourceText = _sourceText.value,
                            translatedText = _translatedText.value,
                            sourceLang = _sourceLang.value,
                            targetLang = _targetLang.value,
                            isTranslating = _isTranslating.value,
                            isLoadingModel = _isLoadingModel.value,
                            loadedModelId = loadedModelId,
                            opacity = opacityValue,
                            textColor = textColor,
                            onSourceTextChanged = { _sourceText.value = it },
                            onTranslatedTextChanged = { _translatedText.value = it },
                            onSourceLangChanged = { 
                                if (it == _targetLang.value && it != "自动检测") {
                                    Toast.makeText(this@TranslationFloatingService, "原语言和目标语言不能相同", Toast.LENGTH_SHORT).show()
                                } else {
                                    _sourceLang.value = it
                                    if (it != "自动检测") {
                                        if (_pendingReverseTranslate) {
                                            _pendingReverseTranslate = false
                                            val tempLang = _sourceLang.value
                                            _sourceLang.value = _targetLang.value
                                            _targetLang.value = tempLang
                                            _sourceText.value = _translatedText.value
                                            performTranslation()
                                        } else {
                                            if (it == "中文") {
                                                _targetLang.value = "英语"
                                            } else {
                                                _targetLang.value = "中文"
                                            }
                                        }
                                    }
                                }
                            },
                            onTargetLangChanged = { 
                                if (it == _sourceLang.value) {
                                    Toast.makeText(this@TranslationFloatingService, "目标语言和原语言不能相同", Toast.LENGTH_SHORT).show()
                                } else {
                                    _targetLang.value = it
                                }
                            },
                            onSwapLang = {
                                if (_sourceLang.value == "自动检测") {
                                    // Smart swap for "Auto"
                                    if (_targetLang.value == "中文") {
                                        _sourceLang.value = "中文"
                                        _targetLang.value = "英语"
                                    } else {
                                        // Default fallback to English -> Chinese if target was something else or English
                                        _sourceLang.value = "英语"
                                        _targetLang.value = "中文"
                                    }
                                } else {
                                    val temp = _sourceLang.value
                                    _sourceLang.value = _targetLang.value
                                    _targetLang.value = temp
                                }
                            },
                            onTranslate = {
                                if (_isTranslating.value) {
                                    translationManager.abort()
                                    _isTranslating.value = false
                                } else {
                                    performTranslation()
                                }
                            },
                            onClear = { _sourceText.value = "" },
                            onCopy = {
                                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Translated Text", _translatedText.value)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(this@TranslationFloatingService, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            },
                            onSendToInput = {
                                val instance = FunctionEnhanceAccessibilityServices.instance
                                if (instance != null) {
                                    instance.injectText(_translatedText.value, settingsManager.removeSpaces)
                                } else {
                                    Toast.makeText(this@TranslationFloatingService, "无障碍服务未启动，无法注入文本", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onShare = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, _translatedText.value)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                startActivity(Intent.createChooser(intent, "分享翻译结果").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            },
                            onClose = {
                                stopSelf()
                            },
                            onMinimize = {
                                _isMinimized.value = true
                            },
                            onReverseTranslate = {
                                if (_translatedText.value.isNotEmpty()) {
                                    if (_sourceLang.value == "自动检测") {
                                        _pendingReverseTranslate = true
                                        Toast.makeText(this@TranslationFloatingService, "自动检测模式无法反向，请先选择原语言", Toast.LENGTH_SHORT).show()
                                        // 自动触发菜单弹出逻辑移到了 UI 组件的点击事件中
                                    } else {
                                        val tempLang = _sourceLang.value
                                        _sourceLang.value = _targetLang.value
                                        _targetLang.value = tempLang
                                        _sourceText.value = _translatedText.value
                                        performTranslation()
                                    }
                                }
                            },
                            onOcr = {
                                if (!MediaProjectionShareManager.hasResultData()) {
                                    val intent = Intent(this@TranslationFloatingService, ScreenCaptureActivity::class.java).apply {
                                        putExtra("requester", "OCR")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                    }
                                    startActivity(intent)
                                } else {
                                    performScreenOcr()
                                }
                            },
                            onDrag = { dx, dy ->
                                params.x += dx.toInt()
                                params.y += dy.toInt() // Direct map dx/dy to window coordinate changes
                                floatingView?.let { windowManager.updateViewLayout(it, params) }
                            },
                            onResize = { dw, dh ->
                                val density = resources.displayMetrics.density
                                // 修正：直接操作像素值以确保绝对跟手，避免 DP 转换带来的精度损失
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

        composeView.setViewTreeLifecycleOwner(this)
        composeView.setViewTreeSavedStateRegistryOwner(this)
        
        windowManager.addView(composeView, params)
        floatingView = composeView
    }

    private fun performTranslation() {
        if (settingsManager.transEngine == "GOOGLE") {
            startTranslation()
            return
        }
        val loadedId = translationManager.loadedModelId.value
        if (loadedId == null) {
            val strategy = settingsManager.transModelStrategy
            if (strategy != "ask") {
                val modelToLoad = translationManager.availableModels.find { it.id == strategy }
                if (modelToLoad != null && translationManager.isModelDownloaded(modelToLoad.id)) {
                    _isLoadingModel.value = true
                    translationManager.loadModel(modelToLoad.id) { id ->
                        _isLoadingModel.value = false
                        if (id >= 0) {
                            startTranslation()
                        } else {
                            Toast.makeText(this, "自动加载模型失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return
                }
            }
            Toast.makeText(this, "请先在主应用加载模型", Toast.LENGTH_SHORT).show()
            return
        }
        startTranslation()
    }

    private fun startTranslation() {
        if (_sourceText.value.isBlank()) return
        _translatedText.value = ""
        
        if (settingsManager.transEngine == "GOOGLE") {
            _isTranslating.value = true
            lifecycleScope.launch {
                val result = googleTranslationManager.translate(_sourceText.value, _sourceLang.value, _targetLang.value)
                _translatedText.value = result
                _isTranslating.value = false
            }
        } else {
            val success = translationManager.translate(_sourceText.value, _targetLang.value, _sourceLang.value)
            if (success) {
                _isTranslating.value = true
                startWatchdog()
            } else {
                Toast.makeText(this, "翻译启动失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startWatchdog() {
        translateWatchdogJob?.cancel()
        translateWatchdogJob = lifecycleScope.launch {
            // 延长至 60s 鲁棒性更高
            delay(60000)
            if (_isTranslating.value) {
                // 超时必须强制中止后台任务
                translationManager.abort()
                _isTranslating.value = false
                Toast.makeText(this@TranslationFloatingService, "推理超时，已中止任务", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    private fun performScreenOcr() {
        when (settingsManager.ocrCaptureMode) {
            SettingsManager.CAPTURE_MODE_ACCESSIBILITY -> {
                val service = FunctionEnhanceAccessibilityServices.instance
                if (service == null) {
                    Toast.makeText(this, "无障碍服务未启动，请先开启", Toast.LENGTH_SHORT).show()
                    return
                }
                lifecycleScope.launch(Dispatchers.Main) {
                    floatingView?.visibility = View.GONE
                    delay(500)
                    service.takeScreenshot { bitmap ->
                        if (bitmap != null) {
                            processCapturedBitmap(bitmap)
                        } else {
                            floatingView?.visibility = View.VISIBLE
                            Toast.makeText(this@TranslationFloatingService, "无障碍截屏失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            SettingsManager.CAPTURE_MODE_ROOT -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    withContext(Dispatchers.Main) {
                        floatingView?.visibility = View.GONE
                        delay(500)
                    }
                    val bitmap = try {
                        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "screencap -p"))
                        val inputStream = process.inputStream
                        android.graphics.BitmapFactory.decodeStream(inputStream)
                    } catch (e: Exception) {
                        Log.e("FloatingService", "Root screencap failed", e)
                        null
                    }
                    withContext(Dispatchers.Main) {
                        if (bitmap != null) {
                            processCapturedBitmap(bitmap)
                        } else {
                            floatingView?.visibility = View.VISIBLE
                            Toast.makeText(this@TranslationFloatingService, "ROOT 截屏失败，请检查授权", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            else -> performMediaProjectionOcr()
        }
    }

    private fun performMediaProjectionOcr() {
        if (!MediaProjectionShareManager.hasResultData()) {
            val intent = Intent(this, ScreenCaptureActivity::class.java).apply {
                putExtra("requester", "OCR")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            startActivity(intent)
            return
        }
        
        if (!ocrManager.isInitialized.value) {
            Toast.makeText(this, "正在初始化 OCR 引擎...", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch(Dispatchers.IO) {
                val success = ocrManager.initModelFromAssets()
                if (success) {
                    withContext(Dispatchers.Main) { performScreenOcr() }
                } else {
                    withContext(Dispatchers.Main) { Toast.makeText(this@TranslationFloatingService, "OCR 引擎初始化失败", Toast.LENGTH_SHORT).show() }
                }
            }
            return
        }

        // Android 14+ 要求在获取 MediaProjection 前，Service 必须已切换到 mediaProjection 类型
        try {
            updateNotificationForMediaProjection()
        } catch (e: SecurityException) {
            // 如果因为某种原因权限失效，清除旧数据并重新请求
            MediaProjectionShareManager.clearResultData()
            val intent = Intent(this, ScreenCaptureActivity::class.java).apply {
                putExtra("requester", "OCR")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            startActivity(intent)
            return
        }

        // 核心优化：延迟捕获并控制悬浮窗可见性
        lifecycleScope.launch(Dispatchers.Main) {
            // 1. 暂时隐藏悬浮窗，避免遮挡识别内容
            floatingView?.visibility = View.GONE
            
            // 2. 等待 1 秒延迟，让权限弹窗完全消失或 UI 回到稳定状态
            delay(1000)

            Toast.makeText(this@TranslationFloatingService, "正在识别屏幕...", Toast.LENGTH_SHORT).show()

            // 关键：使用 getRealScreenSize 确保获取包含状态栏和导航栏的真实物理分辨率
            val screenSize = getRealScreenSize()
            val width = screenSize.x
            val height = screenSize.y
            val density = resources.displayMetrics.densityDpi

            val projection = MediaProjectionShareManager.acquireMediaProjection(this@TranslationFloatingService, "OCR")

            if (projection == null) {
                MediaProjectionShareManager.clearResultData()
                createNotification()
                floatingView?.visibility = View.VISIBLE // 恢复显示
                Toast.makeText(this@TranslationFloatingService, "录屏授权失效，请重试", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 5)
            var virtualDisplay: VirtualDisplay? = null
            
            fun cleanup() {
                imageReader.setOnImageAvailableListener(null, null)
                virtualDisplay?.release()
                virtualDisplay = null
                MediaProjectionShareManager.releaseMediaProjection("OCR")
                imageReader.close()
                createNotification()
                floatingView?.visibility = View.VISIBLE
            }

            virtualDisplay = try {
                projection.createVirtualDisplay(
                    "ScreenCapture", width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.surface, null, null
                )
            } catch (e: Exception) {
                Log.e("FloatingService", "Failed to create virtual display", e)
                null
            }

            if (virtualDisplay == null) {
                cleanup()
                return@launch
            }

            var isProcessed = false
            var frameCount = 0
            imageReader.setOnImageAvailableListener({ reader ->
                if (isProcessed) return@setOnImageAvailableListener
                
                val image = try { reader.acquireLatestImage() } catch (e: Exception) { null }
                if (image != null) {
                    frameCount++
                    
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width
                    
                    val bitmap = android.graphics.Bitmap.createBitmap(
                        width + rowPadding / pixelStride, height,
                        android.graphics.Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    
                    // 裁剪掉 padding
                    val cleanBitmap = if (rowPadding > 0) {
                        android.graphics.Bitmap.createBitmap(bitmap, 0, 0, width, height)
                    } else {
                        bitmap
                    }
                    
                    // 核心鲁棒性优化：
                    // 1. 跳过前几帧
                    // 2. 检查图片是否全黑（部分设备首帧会返回全黑图片）
                    val isBlack = isBitmapBlack(cleanBitmap)
                    Log.d("FloatingService", "Frame $frameCount captured, isBlack: $isBlack")

                    if (frameCount < 3 || isBlack) {
                        if (frameCount < 10) { // 最多尝试 10 帧
                            image.close()
                            if (cleanBitmap != bitmap) cleanBitmap.recycle()
                            bitmap.recycle()
                            return@setOnImageAvailableListener
                        }
                        // 如果 10 帧都全黑，那就只能认了，交给 OCR 报错
                    }

                    isProcessed = true // 确保只处理一帧
                    image.close()
                    cleanup()
                    processCapturedBitmap(cleanBitmap)
                }
            }, Handler(Looper.getMainLooper()))

            // 增加超时保护：如果 5 秒内没拿图像（比如单应用录制时用户没操作），自动清理
            lifecycleScope.launch {
                delay(5000)
                if (!isProcessed) {
                    cleanup()
                    if (MediaProjectionShareManager.hasResultData()) {
                        Toast.makeText(this@TranslationFloatingService, getString(R.string.trans_timeout), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun processCapturedBitmap(capturedBitmap: android.graphics.Bitmap) {
        if (!ocrManager.isInitialized.value) {
            Toast.makeText(this, "正在初始化 OCR 引擎...", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch(Dispatchers.IO) {
                val success = ocrManager.initModelFromAssets()
                if (success) {
                    withContext(Dispatchers.Main) { processCapturedBitmap(capturedBitmap) }
                } else {
                    withContext(Dispatchers.Main) { Toast.makeText(this@TranslationFloatingService, "OCR 引擎初始化失败", Toast.LENGTH_SHORT).show() }
                }
            }
            return
        }

        var cleanBitmap = capturedBitmap
        
        // 质量与性能隔离逻辑：
        // Google OCR: 官方建议通过调整 Bitmap 分辨率来平衡速度与精度，
        // 这里应用用户在设置中自定义的 maxTargetSize。
        // PaddleOCR: 拥有复杂的 Native 管道（检测->裁剪->识别），且对输入尺寸有“32倍数”等严格要求，
        // 因此必须跳过此处的通用缩放，由 Native 层根据其 config.txt 自行处理。
        _ocrScaleFactor = 1.0f
        if (settingsManager.ocrEngine == "GOOGLE") {
            val maxTargetSize = settingsManager.ocrMaxSide
            if (cleanBitmap.width > maxTargetSize || cleanBitmap.height > maxTargetSize) {
                _ocrScaleFactor = maxTargetSize.toFloat() / Math.max(cleanBitmap.width, cleanBitmap.height)
                val newWidth = (cleanBitmap.width * _ocrScaleFactor).toInt()
                val newHeight = (cleanBitmap.height * _ocrScaleFactor).toInt()
                val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(cleanBitmap, newWidth, newHeight, true)
                // 如果是新创建的 scaledBitmap，则回收旧的（如果是从 projection 来的 cleanBitmap 需要回收）
                // 注意：capturedBitmap 如果是 root 或 accessibility 来的，也需要妥善处理回收
                cleanBitmap = scaledBitmap
            }
        }

        val isFullScreenRequested = settingsManager.ocrTranslationMode == SettingsManager.OCR_MODE_FULL_SCREEN && 
                                  settingsManager.ocrEngine == "GOOGLE"

        if (settingsManager.ocrTranslationMode == SettingsManager.OCR_MODE_FULL_SCREEN && 
            settingsManager.ocrEngine == "PADDLE") {
            Toast.makeText(this@TranslationFloatingService, getString(R.string.ocr_trans_mode_paddle_warning), Toast.LENGTH_SHORT).show()
        }

        if (isFullScreenRequested) {
            _isOcrProcessing.value = true
            _ocrOverlayResults.clear()
            enterFullScreenMode()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            // 使用统一 OCR 接口，引擎选择逻辑已封装 in OcrManager
            val langCode = TranslationLanguages.find { it.first == _sourceLang.value }?.second ?: "zh"
            val results = ocrManager.recognize(cleanBitmap, langCode)
            
            if (isFullScreenRequested) {
                val overlayResults = results.map { ocrResult ->
                    // Map coordinates back to screen pixels
                    val rect = ocrResult.rect
                    
                    // 略微放大框：增加 5% 的内边距，提升视觉包围感
                    val horizontalPadding = (rect.width() * 0.05f).toInt()
                    val verticalPadding = (rect.height() * 0.05f).toInt()
                    
                    val mappedRect = Rect(
                        ((rect.left - horizontalPadding) / _ocrScaleFactor).toInt(),
                        ((rect.top - verticalPadding) / _ocrScaleFactor).toInt(),
                        ((rect.right + horizontalPadding) / _ocrScaleFactor).toInt(),
                        ((rect.bottom + verticalPadding) / _ocrScaleFactor).toInt()
                    )
                    OcrOverlayResult(ocrResult, displayRect = mappedRect)
                }
                withContext(Dispatchers.Main) {
                    _ocrOverlayResults.addAll(overlayResults)
                    _isOcrProcessing.value = false
                    
                    // 如果正在使用无障碍绘制，需要通知它更新数据
                    if (settingsManager.ocrUseAccessibilityOverlay) {
                        FunctionEnhanceAccessibilityServices.instance?.let { service ->
                            service.hideOcrOverlay()
                            service.showOcrOverlay(
                                results = _ocrOverlayResults,
                                isOcrProcessing = _isOcrProcessing.value,
                                bgOpacity = settingsManager.ocrFullScreenBgOpacity,
                                onTranslateBlock = { block -> translateOcrBlock(block) },
                                onTranslateAll = { translateAllBlocks() },
                                onExit = { exitFullScreenMode() }
                            )
                        }
                    }
                }
            } else {
                // 在 IO 线程进行字符串拼接，避免大文本卡顿主线程
                val recognizedText = results.joinToString("\n") { it.text }
                
                withContext(Dispatchers.Main) {
                    if (results.isNotEmpty()) {
                        _sourceText.value = recognizedText
                    } else {
                        Toast.makeText(this@TranslationFloatingService, getString(R.string.ocr_no_text_detected), Toast.LENGTH_SHORT).show()
                    }
                    // 回收 Bitmap
                    if (cleanBitmap != capturedBitmap) cleanBitmap.recycle()
                    capturedBitmap.recycle()
                }
            }
        }
    }

    private fun isBitmapBlack(bitmap: android.graphics.Bitmap): Boolean {
        // 采样检查图片是否全黑
        val width = bitmap.width
        val height = bitmap.height
        val samples = 10
        for (i in 1..samples) {
            for (j in 1..samples) {
                val x = (width * i / (samples + 1))
                val y = (height * j / (samples + 1))
                val pixel = bitmap.getPixel(x, y)
                if (pixel != android.graphics.Color.BLACK && pixel != 0) {
                    return false
                }
            }
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenCaptureReceiver)
        MediaProjectionShareManager.releaseMediaProjection("OCR", forceStop = true)
        translateWatchdogJob?.cancel()
        translationQueueJob?.cancel()
        translationQueue.close()
        floatingView?.let { windowManager.removeView(it) }
        try {
            highlightOverlayView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {}
        
        // 发送停止广播，通知 UI 同步状态
        val intent = Intent("com.buildsession.localtranslator.TRANSLATION_SERVICE_STOPPED")
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    companion object {
        const val CHANNEL_ID = "TranslationFloatingServiceChannel"
    }
}

@Composable
fun FullScreenTranslationOverlay(
    results: List<OcrOverlayResult>,
    isOcrProcessing: Boolean,
    bgOpacity: Float = 0.5f,
    selectedMergeIndices: List<Int> = emptyList(),
    isManualMergingMode: Boolean = false,
    onTranslateBlock: (OcrOverlayResult) -> Unit,
    onTranslateAll: () -> Unit,
    onExit: () -> Unit,
    onStartMerge: (Int) -> Unit = {},
    onAddMergeIndex: (Int) -> Unit = {},
    onFinishMerge: () -> Unit = {}
) {
    var selectedBlock by remember { mutableStateOf<OcrOverlayResult?>(null) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = bgOpacity))
            .pointerInput(results) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val xPx = offset.x
                        val yPx = offset.y
                        val index = results.indexOfFirst { it.displayRect.contains(xPx.toInt(), yPx.toInt()) }
                        if (index != -1) {
                            onStartMerge(index)
                        }
                    },
                    onDrag = { change, _ ->
                        val xPx = change.position.x
                        val yPx = change.position.y
                        val index = results.indexOfFirst { it.displayRect.contains(xPx.toInt(), yPx.toInt()) }
                        if (index != -1) {
                            onAddMergeIndex(index)
                        }
                    },
                    onDragEnd = {
                        onFinishMerge()
                    },
                    onDragCancel = {
                        onFinishMerge()
                    }
                )
            }
    ) {
        // Top Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(
                onClick = onExit,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
            }
        }

        if (isOcrProcessing) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HedgehogLoading(loadingSize = 80.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.ocr_full_screen_analyzing), 
                        color = Color.White, 
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        } else {
            // Text Blocks with a slight delay entry animation
            Box(modifier = Modifier.fillMaxSize()) {
                results.forEachIndexed { index, result ->
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(index * 20L) // Cascade entry
                        visible = true
                    }
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn() + scaleIn(initialScale = 0.9f)
                    ) {
                        FullScreenTextBlock(
                            result = result,
                            isFocused = selectedBlock?.ocrResult == result.ocrResult,
                            isSelectedForMerge = selectedMergeIndices.contains(index),
                            onClick = { 
                                if (isManualMergingMode) return@FullScreenTextBlock
                                if (result.status == TranslationStatus.COMPLETED) {
                                    selectedBlock = if (selectedBlock?.ocrResult == result.ocrResult) null else result
                                } else {
                                    onTranslateBlock(result)
                                }
                            }
                        )
                    }
                }
            }

            // Detail Card (Bottom Sheet style)
            AnimatedVisibility(
                visible = selectedBlock != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                if (selectedBlock != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .padding(bottom = 80.dp), // Space for action buttons
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(20.dp)
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(R.string.ocr_detail_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { selectedBlock = null }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                                }
                            }
                            
                            Spacer(Modifier.height(12.dp))
                            
                            Text(
                                text = stringResource(R.string.ocr_original_text),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = selectedBlock!!.ocrResult.text,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            
                            Text(
                                text = stringResource(R.string.ocr_translated_text),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = selectedBlock!!.translatedText ?: "",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Actions - Floating Card Style
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onTranslateAll,
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Translate, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.ocr_translate_all))
                        }
                        
                        Spacer(Modifier.width(12.dp))
                        
                        Surface(
                            modifier = Modifier.width(1.dp).height(24.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        ) {}
                        
                        Spacer(Modifier.width(12.dp))
                        
                        TextButton(
                            onClick = onExit,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(R.string.close))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FullScreenTextBlock(
    result: OcrOverlayResult,
    isFocused: Boolean = false,
    isSelectedForMerge: Boolean = false,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    val rect = result.displayRect
    
    val left = with(density) { rect.left.toDp() }
    val top = with(density) { rect.top.toDp() }
    val width = with(density) { rect.width().toDp() }
    val height = with(density) { rect.height().toDp() }

    val isTranslated = result.status == TranslationStatus.COMPLETED
    val borderColor by animateColorAsState(
        targetValue = when {
            isSelectedForMerge -> Color(0xFF9C27B0) // Purple for merging
            isFocused -> MaterialTheme.colorScheme.primary
            isTranslated -> Color(0xFF00E676)
            else -> Color(0xFF00B0FF)
        },
        label = "BorderColor"
    )
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelectedForMerge -> Color(0xFF9C27B0).copy(alpha = 0.3f)
            isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            isTranslated -> Color(0xFF00E676).copy(alpha = 0.15f) 
            else -> Color(0xFF00B0FF).copy(alpha = 0.1f)
        },
        label = "BackgroundColor"
    )

    // 提升滚动状态，以便实现平滑自动滚动
    val scrollState = rememberScrollState()
    
    // 自动平滑滚动逻辑：当内容增多（maxValue 变大）且处于翻译中或已完成时，自动滚到最新内容
    LaunchedEffect(scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            scrollState.animateScrollTo(
                value = scrollState.maxValue,
                animationSpec = spring(stiffness = Spring.StiffnessLow)
            )
        }
    }

    Box(
        modifier = Modifier
            .offset(x = left, y = top)
            .size(width, height)
            .graphicsLayer {
                shadowElevation = if (isTranslated || isFocused || isSelectedForMerge) 4.dp.toPx() else 0f
                shape = RoundedCornerShape(4.dp)
                clip = true
            }
            .border(
                width = if (isFocused || isSelectedForMerge) 2.dp else 1.5.dp,
                brush = SolidColor(borderColor),
                shape = RoundedCornerShape(4.dp)
            )
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when (result.status) {
            TranslationStatus.WAITING -> {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF00B0FF).copy(alpha = 0.5f)
                    )
                }
            }
            TranslationStatus.TRANSLATING -> {
                // 动态内边距：高度越小边距越薄，确保小框也能显示内容
                val dynamicPadding = when {
                    height < 20.dp -> 1.dp
                    height < 40.dp -> 2.dp
                    else -> 4.dp
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f))
                        .padding(dynamicPadding)
                        .verticalScroll(scrollState),
                    contentAlignment = Alignment.Center
                ) {
                    val inlineContentId = "loading_ball"
                    val text = result.translatedText ?: ""
                    
                    val annotatedString = buildAnnotatedString {
                        append(text)
                        append(" ")
                        appendInlineContent(inlineContentId, "[loading]")
                    }

                    val inlineContent = mapOf(
                        inlineContentId to InlineTextContent(
                            Placeholder(
                                width = 14.sp,
                                height = 14.sp,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                            )
                        ) {
                            HedgehogLoading(loadingSize = 12.dp, color = MaterialTheme.colorScheme.primary)
                        }
                    )

                    // 改进的字体自适应：对于窄框更加激进地缩小字体
                    val adaptiveFontSize = remember(height, result.translatedText) {
                        val boxHeight = height.value
                        if (boxHeight < 25f) {
                            // 窄框：强制按高度的 65% 计算，确保单行能放下
                            (boxHeight * 0.65f).coerceIn(6f, 14f).sp
                        } else {
                            // 大框：综合高度和长度
                            val textLength = result.translatedText?.length ?: 1
                            val baseSize = boxHeight * 0.35f
                            val sizeAdjustedByLength = if (textLength > 10) {
                                baseSize * (1f - (textLength / 250f).coerceAtMost(0.5f))
                            } else baseSize
                            sizeAdjustedByLength.coerceIn(8f, 16f).sp
                        }
                    }

                    Text(
                        text = annotatedString,
                        inlineContent = inlineContent,
                        color = Color.White,
                        fontSize = adaptiveFontSize,
                        lineHeight = if (height < 25.dp) adaptiveFontSize else adaptiveFontSize * 1.2f,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            TranslationStatus.COMPLETED -> {
                if (result.translatedText != null) {
                    // 动态内边距
                    val dynamicPadding = when {
                        height < 20.dp -> 1.dp
                        height < 40.dp -> 2.dp
                        else -> 4.dp
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.85f))
                            .padding(dynamicPadding)
                            .verticalScroll(scrollState),
                        contentAlignment = Alignment.Center
                    ) {
                        // 改进的字体自适应
                        val adaptiveFontSizeInfo = remember(height, result.translatedText) {
                            val boxHeight = height.value
                            val textLength = result.translatedText?.length ?: 1
                            
                            if (boxHeight < 25f) {
                                val size = (boxHeight * 0.65f).coerceIn(6f, 14f)
                                Pair(size.sp, false)
                            } else {
                                val baseSize = boxHeight * 0.35f
                                val sizeAdjustedByLength = if (textLength > 10) {
                                    baseSize * (1f - (textLength / 250f).coerceAtMost(0.5f))
                                } else baseSize
                                
                                val finalSize = sizeAdjustedByLength.coerceAtLeast(7f)
                                val isTooSmall = sizeAdjustedByLength < 7f
                                Pair(finalSize.sp, isTooSmall)
                            }
                        }
                        val adaptiveFontSize = adaptiveFontSizeInfo.first
                        val isTextTruncated = adaptiveFontSizeInfo.second

                        Text(
                            text = result.translatedText!!,
                            color = Color.White,
                            fontSize = adaptiveFontSize,
                            lineHeight = if (height < 25.dp) adaptiveFontSize else adaptiveFontSize * 1.15f,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = if (isTextTruncated || height < 25.dp) 1 else Int.MAX_VALUE,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        if (isTextTruncated) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Text(
                                    "...",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            TranslationStatus.IDLE -> {
                // No overlay content for idle state
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationFloatingControl(
    sourceText: String,
    translatedText: String,
    sourceLang: String,
    targetLang: String,
    isTranslating: Boolean,
    isLoadingModel: Boolean,
    loadedModelId: String?,
    opacity: Float,
    textColor: Color,
    onSourceTextChanged: (String) -> Unit,
    onTranslatedTextChanged: (String) -> Unit,
    onSourceLangChanged: (String) -> Unit,
    onTargetLangChanged: (String) -> Unit,
    onSwapLang: () -> Unit,
    onTranslate: () -> Unit,
    onClear: () -> Unit,
    onCopy: () -> Unit,
    onSendToInput: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onReverseTranslate: () -> Unit,
    onOcr: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit
) {
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
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    }
            ) {
            var showSourceMenu by remember { mutableStateOf(false) }
            var showTargetMenu by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.translationball),
                        contentDescription = "Translation Ball",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    if (isLoadingModel) {
                        HedgehogLoading(loadingSize = 14.dp, color = Color.White.copy(alpha = 0.9f))
                    } else if (loadedModelId == null) {
                        Icon(Icons.Default.Warning, stringResource(R.string.trans_not_loaded), tint = Color.Yellow, modifier = Modifier.size(12.dp))
                    } else {
                        Icon(Icons.Default.CheckCircle, stringResource(R.string.trans_ready), tint = Color.Green, modifier = Modifier.size(12.dp))
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMinimize, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.CloseFullscreen, stringResource(R.string.trans_minimize), modifier = Modifier.size(16.dp), tint = textColor.copy(alpha = 0.7f))
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, stringResource(R.string.close), modifier = Modifier.size(16.dp), tint = textColor.copy(alpha = 0.7f))
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth().height(24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box {
                    Text(
                        text = sourceLang,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.clickable { showSourceMenu = true }.padding(horizontal = 4.dp)
                    )
                    DropdownMenu(expanded = showSourceMenu, onDismissRequest = { showSourceMenu = false }) {
                        TranslationLanguages.forEach { (name, _) ->
                            DropdownMenuItem(
                                text = { Text(name, fontSize = 12.sp) },
                                onClick = {
                                    onSourceLangChanged(name)
                                    showSourceMenu = false
                                }
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onSwapLang,
                    modifier = Modifier.size(20.dp),
                    enabled = true
                ) {
                    Icon(
                        Icons.Default.SwapHoriz, 
                        null, 
                        modifier = Modifier.size(12.dp), 
                        tint = textColor.copy(alpha = 0.7f)
                    )
                }

                Box {
                    Text(
                        text = targetLang,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.clickable { showTargetMenu = true }.padding(horizontal = 4.dp)
                    )
                    DropdownMenu(expanded = showTargetMenu, onDismissRequest = { showTargetMenu = false }) {
                        TranslationLanguages.filter { it.second != "auto" }.forEach { (name, _) ->
                            DropdownMenuItem(
                                text = { Text(name, fontSize = 12.sp) },
                                onClick = {
                                    onTargetLangChanged(name)
                                    showTargetMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Input
            OutlinedTextField(
                value = sourceText,
                onValueChange = onSourceTextChanged,
                modifier = Modifier.fillMaxWidth().weight(1f),
                placeholder = { Text(stringResource(R.string.trans_input_hint), fontSize = 12.sp) },
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                trailingIcon = {
                    if (sourceText.isNotEmpty()) {
                        IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = textColor.copy(alpha = 0.5f),
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onTranslate,
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = if (isTranslating) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                             else ButtonDefaults.buttonColors()
                ) {
                    Text(if (isTranslating) stringResource(R.string.trans_stop) else stringResource(R.string.trans_translate), fontSize = 12.sp)
                }
            }

            // Output
            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = textColor.copy(alpha = 0.3f)
                    ),
                    color = Color.Transparent
                ) {
                    val scrollState = rememberScrollState()
                    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                    var containerSize by remember { mutableStateOf(IntSize.Zero) }
                    
                    // 使用 TextFieldValue 管理光标和选择状态
                    var textFieldValue by remember { 
                        mutableStateOf(TextFieldValue(translatedText)) 
                    }
                    
                    // 当外部 translatedText 变化时（如推理中），同步更新本地 state
                    LaunchedEffect(translatedText) {
                        if (translatedText != textFieldValue.text) {
                            textFieldValue = textFieldValue.copy(text = translatedText)
                        }
                    }
                    
                    // 处理自动滚动到底部：当最大滚动值变化时（说明有新文字），平滑滚动到底部
                    LaunchedEffect(scrollState.maxValue) {
                        if (isTranslating && scrollState.maxValue > 0) {
                            scrollState.animateScrollTo(
                                value = scrollState.maxValue,
                                animationSpec = spring(stiffness = Spring.StiffnessLow)
                            )
                        }
                    }

                    // 当光标位置变化时，确保光标可见
                    LaunchedEffect(textFieldValue.selection) {
                        val layout = textLayoutResult ?: return@LaunchedEffect
                        val cursorRect = layout.getCursorRect(textFieldValue.selection.start)
                        val cursorTop = cursorRect.top
                        val cursorBottom = cursorRect.bottom
                        
                        val scrollOffset = scrollState.value
                        val viewportHeight = containerSize.height
                        
                        if (cursorTop < scrollOffset) {
                            // 光标在上方不可见，向上滚动
                            scrollState.animateScrollTo(cursorTop.roundToInt())
                        } else if (cursorBottom > scrollOffset + viewportHeight) {
                            // 光标在下方不可见，向下滚动
                            scrollState.animateScrollTo((cursorBottom - viewportHeight).roundToInt())
                        }
                    }

                    // 使用 BasicTextField 作为主滚动容器
                    androidx.compose.foundation.text.BasicTextField(
                        value = textFieldValue,
                        onValueChange = {
                            textFieldValue = it
                            if (it.text != translatedText) {
                                onTranslatedTextChanged(it.text)
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .onGloballyPositioned { containerSize = it.size }
                            .verticalScroll(scrollState),
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color.Transparent),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(textColor),
                        onTextLayout = { textLayoutResult = it },
                        decorationBox = { innerTextField ->
                            Box {
                                val inlineContentId = "loading_ball"
                                val annotatedString = buildAnnotatedString {
                                    append(translatedText)
                                    if (isTranslating) {
                                        append(" ")
                                        appendInlineContent(inlineContentId, "[loading]")
                                    }
                                }

                                val inlineContent = mapOf(
                                    inlineContentId to InlineTextContent(
                                        Placeholder(
                                            width = 14.sp,
                                            height = 14.sp,
                                            placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                                        )
                                    ) {
                                        HedgehogLoading(loadingSize = 12.dp, color = MaterialTheme.colorScheme.primary)
                                    }
                                )

                                // 底层显示层：支持 InlineContent
                                Text(
                                    text = annotatedString,
                                    inlineContent = inlineContent,
                                    style = LocalTextStyle.current.copy(fontSize = 12.sp, color = textColor),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // 实际的编辑层（文字透明，仅显示光标）
                                innerTextField()
                            }
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(14.dp))
                    }
                    if (translatedText.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                if (sourceLang == "自动检测") {
                                    onReverseTranslate()
                                    showSourceMenu = true
                                } else {
                                    onReverseTranslate()
                                }
                            },
                            modifier = Modifier.height(24.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.CompareArrows, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(2.dp))
                            Text(stringResource(R.string.trans_reverse_btn), fontSize = 10.sp)
                        }
                    }
                }

                IconButton(onClick = onOcr, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Screenshot, stringResource(R.string.trans_ocr_btn), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                }

                IconButton(onClick = onSendToInput, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        
        // Resize handle at bottom right.
        // Keep gesture detection on an unrotated container to avoid rotated local coordinates.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(16.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onResize(dragAmount.x, dragAmount.y)
                    }
                }
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Resize",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = -45f
                    },
                tint = textColor.copy(alpha = 0.5f)
            )
        }
    }
}
}
