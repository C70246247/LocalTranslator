package com.buildsession.localtranslator

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.buildsession.localtranslator.ocr.OcrManager
import com.buildsession.localtranslator.ocr.OcrResult
import android.graphics.Bitmap
import android.view.Display
import android.view.WindowManager
import android.graphics.PixelFormat
import android.view.Gravity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.buildsession.localtranslator.translation.OcrOverlayResult
import com.buildsession.localtranslator.translation.FullScreenTranslationOverlay
import com.buildsession.localtranslator.translation.TranslationStatus
import com.buildsession.localtranslator.ui.theme.localtranslatorTheme
import com.buildsession.localtranslator.util.SettingsManager
import java.util.concurrent.Executor

class FunctionEnhanceAccessibilityServices : AccessibilityService(), LifecycleOwner, SavedStateRegistryOwner {

    private lateinit var settingsManager: SettingsManager
    private lateinit var windowManager: WindowManager
    private var fullScreenOverlayView: ComposeView? = null
    private var fullScreenParams: WindowManager.LayoutParams? = null
    
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    companion object {
        var instance: FunctionEnhanceAccessibilityServices? = null
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        settingsManager = SettingsManager(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        
        // 某些机型不会完全按 XML 应用 flags，连接后再次加固能力配置。
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            flags = flags or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
            }
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_FOCUSED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不需要响应常规事件，只提供主动调用方法
    }

    override fun onInterrupt() {
        instance = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    /**
     * 显示全屏 OCR 翻译覆盖层（通过无障碍服务，可覆盖状态栏）
     */
    fun showOcrOverlay(
        results: List<OcrOverlayResult>,
        isOcrProcessing: Boolean,
        bgOpacity: Float,
        onTranslateBlock: (OcrOverlayResult) -> Unit,
        onTranslateAll: () -> Unit,
        onExit: () -> Unit
    ) {
        if (fullScreenOverlayView != null) return

        fullScreenParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        fullScreenOverlayView = ComposeView(this).apply {
            fitsSystemWindows = false
            setContent {
                localtranslatorTheme {
                    FullScreenTranslationOverlay(
                        results = results,
                        isOcrProcessing = isOcrProcessing,
                        bgOpacity = bgOpacity,
                        onTranslateBlock = onTranslateBlock,
                        onTranslateAll = onTranslateAll,
                        onExit = {
                            hideOcrOverlay()
                            onExit()
                        }
                    )
                }
            }
        }

        fullScreenOverlayView?.setViewTreeLifecycleOwner(this)
        fullScreenOverlayView?.setViewTreeSavedStateRegistryOwner(this)

        try {
            windowManager.addView(fullScreenOverlayView, fullScreenParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hideOcrOverlay() {
        fullScreenOverlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        fullScreenOverlayView = null
        fullScreenParams = null
    }

    // --- 注入文本功能 (原 VoskAccessibilityService 功能) ---
    fun injectText(text: String, applyRemoveSpaces: Boolean = false) {
        if (text.isEmpty()) return
        
        // 尝试在所有窗口中寻找非本应用的焦点输入框
        var focusedNode: AccessibilityNodeInfo? = null
        val myPackageName = packageName
        
        // 1. 先尝试当前活动窗口
        val activeRoot = rootInActiveWindow
        if (activeRoot != null) {
            val node = findFocusedNode(activeRoot)
            if (node != null && isEditable(node) && node.packageName != myPackageName) {
                focusedNode = node
            }
        }
        
        // 2. 如果没找到，遍历所有窗口（处理悬浮窗抢占焦点的情况）
        if (focusedNode == null) {
            for (window in windows) {
                val root = window.root ?: continue
                val node = findFocusedNode(root)
                if (node != null && isEditable(node) && node.packageName != myPackageName) {
                    focusedNode = node
                    break
                }
                root.recycle()
            }
        }

        if (focusedNode == null) return

        var textToInject = text
        if (applyRemoveSpaces) {
            textToInject = textToInject.replace(" ", "")
        }

        // 检查当前显示的文本是否仅仅是提示文本 (Hint)
        // 在 Android 8.0+ 中，如果是 Hint，isShowingHintText 会返回 true
        val isShowingHint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusedNode.isShowingHintText
        } else {
            // 低版本尝试通过比较 text 和 hintText (如果可用) 来判断，或者简单判断 text 是否为空
            false
        }

        val currentText = if (isShowingHint) "" else (focusedNode.text?.toString() ?: "")
        val newText = currentText + textToInject
        
        val arguments = Bundle()
        arguments.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            newText
        )
        
        if (focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
            val selectionArgs = Bundle()
            selectionArgs.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newText.length)
            selectionArgs.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newText.length)
            focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
        }
        focusedNode.recycle()
    }

    fun isInputFocused(): Boolean {
        val myPackageName = packageName
        
        // 1. 检查活动窗口
        val activeRoot = rootInActiveWindow
        if (activeRoot != null) {
            val node = findFocusedNode(activeRoot)
            if (node != null && isEditable(node) && node.packageName != myPackageName) {
                node.recycle()
                return true
            }
            node?.recycle()
        }
        
        // 2. 检查所有窗口
        for (window in windows) {
            val root = window.root ?: continue
            val node = findFocusedNode(root)
            if (node != null && isEditable(node) && node.packageName != myPackageName) {
                node.recycle()
                root.recycle()
                return true
            }
            node?.recycle()
            root.recycle()
        }
        
        return false
    }

    private fun isEditable(node: AccessibilityNodeInfo): Boolean {
        return node.isEditable || node.className?.contains("EditText", ignoreCase = true) == true
    }

    private fun findFocusedNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val focused = findFocusedNode(child)
            if (focused != null) {
                // 如果在子树中找到了，不需要 recycle child，因为它会被返回并在外部 recycle
                return focused
            }
            child.recycle()
        }
        return null
    }

    // --- 屏幕文本提取功能 (新增强功能) ---
    
    /**
     * 根据屏幕坐标 (x, y) 寻找最近的文本节点。
     * 相比精确命中，这更符合用户将悬浮球“靠在”文本旁边的直觉。
     * @param maxDistance 搜索半径（像素），超过此距离则不视为有效近邻。
     */
    fun findNearestTextNode(x: Int, y: Int, maxDistance: Int = 200): Pair<String, Rect>? {
        var bestNode: AccessibilityNodeInfo? = null
        var minDistance = Float.MAX_VALUE
        var bestRect = Rect()

        // 1. 获取所有可用窗口的根节点
        val roots = windows.mapNotNull { it.root } + listOfNotNull(rootInActiveWindow)
        
        for (root in roots) {
            val result = searchNearestInTree(root, x, y, maxDistance)
            if (result != null && result.third < minDistance) {
                minDistance = result.third
                bestNode?.recycle()
                bestNode = result.first
                bestRect = result.second
            } else {
                result?.first?.recycle()
            }
        }

        return bestNode?.useNode { node ->
            val text = extractBestText(node) ?: return@useNode null
            Pair(text, bestRect)
        }
    }

    private fun searchNearestInTree(
        node: AccessibilityNodeInfo?, 
        x: Int, 
        y: Int, 
        maxDistance: Int
    ): Triple<AccessibilityNodeInfo, Rect, Float>? {
        if (node == null || !node.isVisibleToUser) return null
        
        var currentBest: Triple<AccessibilityNodeInfo, Rect, Float>? = null
        
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val dist = calculateDistanceToRect(x, y, rect)

        // 性能优化：如果节点距离已经超过搜索半径，则不深入遍历其子树
        // 但如果节点包含目标点 (dist == 0)，则必须深入其子树寻找更小的元素
        if (dist > maxDistance) return null

        // 判断节点自身是否有意义的文本
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        val hasText = !text.isNullOrBlank() || !desc.isNullOrBlank()
        
        // 如果当前节点有文本，先将其作为候选
        if (hasText) {
            currentBest = Triple(AccessibilityNodeInfo.obtain(node), Rect(rect), dist)
        }

        // 递归遍历子节点，寻找更近或更具体的节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val childBest = searchNearestInTree(child, x, y, maxDistance)
            
            if (childBest != null) {
                if (currentBest == null) {
                    currentBest = childBest
                } else {
                    // 优先级判断逻辑：
                    // 1. 距离更近的优先
                    // 2. 距离相等时，面积更小的优先（更具体的节点）
                    val isNearer = childBest.third < currentBest.third
                    val isSmaller = childBest.third == currentBest.third && 
                            (childBest.second.width() * childBest.second.height() < 
                             currentBest.second.width() * currentBest.second.height())
                    
                    if (isNearer || isSmaller) {
                        currentBest.first.recycle()
                        currentBest = childBest
                    } else {
                        childBest.first.recycle()
                    }
                }
            }
            child.recycle()
        }
        
        return currentBest
    }

    private fun calculateDistanceToRect(x: Int, y: Int, rect: Rect): Float {
        val dx = Math.max(0, Math.max(rect.left - x, x - rect.right)).toFloat()
        val dy = Math.max(0, Math.max(rect.top - y, y - rect.bottom)).toFloat()
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    private fun extractBestText(node: AccessibilityNodeInfo): String? {
        val direct = listOfNotNull(
            node.text?.toString(),
            node.contentDescription?.toString(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) node.hintText?.toString() else null,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) node.paneTitle?.toString() else null,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) node.stateDescription?.toString() else null,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) node.error?.toString() else null
        ).firstOrNull { it.isNotBlank() }

        if (!direct.isNullOrBlank()) return direct

        // 某些容器节点（如 WebView 中的某些层级）自身无文本，但子树内有可见文本
        val collected = linkedSetOf<String>()
        // 对于可能包含 WebView 内容的节点，适当增加搜索深度
        val isWeb = node.className?.contains("WebView", ignoreCase = true) == true
        collectDescendantText(node, depth = 0, maxDepth = if (isWeb) 4 else 2, sink = collected)
        
        if (collected.isEmpty()) return null
        return collected.joinToString(" ").take(300)
    }

    private fun collectDescendantText(
        node: AccessibilityNodeInfo,
        depth: Int,
        maxDepth: Int,
        sink: LinkedHashSet<String>
    ) {
        if (depth > maxDepth || sink.size >= 6) return
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            child.useNode { c ->
                val t = c.text?.toString()
                if (!t.isNullOrBlank()) sink.add(t.trim())
                val d = c.contentDescription?.toString()
                if (!d.isNullOrBlank()) sink.add(d.trim())
                collectDescendantText(c, depth + 1, maxDepth, sink)
            }
            if (sink.size >= 6) return
        }
    }

    private inline fun <T> AccessibilityNodeInfo.useNode(block: (AccessibilityNodeInfo) -> T): T {
        return try {
            block(this)
        } finally {
            recycle()
        }
    }

    /**
     * 截取屏幕并返回 Bitmap
     */
    fun takeScreenshot(onResult: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshotResult: ScreenshotResult) {
                    val hardwareBuffer = screenshotResult.hardwareBuffer
                    val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshotResult.colorSpace)
                    
                    if (bitmap != null) {
                        // Hardware bitmap 无法直接访问像素，需要转换为 software bitmap
                        val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                        onResult(softwareBitmap)
                        bitmap.recycle()
                    } else {
                        onResult(null)
                    }
                    hardwareBuffer.close()
                }

                override fun onFailure(errorCode: Int) {
                    onResult(null)
                }
            })
        } else {
            // API < 30 不支持 takeScreenshot
            onResult(null)
        }
    }
}
