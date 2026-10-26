package com.buildsession.localtranslator.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.buildsession.localtranslator.util.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.asDeferred
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.os.Process
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * OCR 引擎类型
 */
enum class OcrEngine {
    PADDLE, // 默认方案 (PaddleOCR)
    GOOGLE  // 备选方案 (Google ML Kit)
}

/**
 * OCR 识别结果数据类
 */
data class OcrResult(
    val text: String,
    val confidence: Float,
    val bounds: List<Point> // 4个顶点的坐标
) {
    /**
     * 获取文本的矩形区域（用于 UI 覆盖）
     */
    val rect: Rect
        get() {
            if (bounds.isEmpty()) return Rect()
            val left = bounds.minOf { it.x }
            val top = bounds.minOf { it.y }
            val right = bounds.maxOf { it.x }
            val bottom = bounds.maxOf { it.y }
            return Rect(left, top, right, bottom)
        }
}

/**
 * OCR 管理类，负责 PaddleOCR 的初始化、模型加载及识别逻辑
 */
class OcrManager(private val context: Context) {
    private val TAG = "OcrManager"
    private val settingsManager = SettingsManager(context)
    
    // Google ML Kit 识别器实例（懒加载）
    private val googleChineseRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
    private val googleJapaneseRecognizer by lazy {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }
    private val googleKoreanRecognizer by lazy {
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }
    private val googleLatinRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    companion object {
        // 内置的 Google OCR 语言模型列表
        val GoogleLanguages = listOf(
            "中文" to "zh",
            "英语" to "en",
            "日语" to "ja",
            "韩语" to "ko"
        )

        private var sharedCtx: Long = 0
        private val _isInitialized = MutableStateFlow(false)
        val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

        init {
            System.loadLibrary("OCR_Native")
        }
    }

    val isInitialized: StateFlow<Boolean> get() = Companion.isInitialized

    private var ctx: Long
        get() = sharedCtx
        set(value) {
            sharedCtx = value
        }

    /**
     * 统一识别接口：根据设置自动选择引擎
     * @param bitmap 待识别图片
     * @param lang 语言代码，仅对 Google 引擎有效 ("zh", "ja", "en" 等)
     */
    suspend fun recognize(bitmap: Bitmap, lang: String = "zh"): List<OcrResult> = withContext(Dispatchers.IO) {
        val engine = settingsManager.ocrEngine
        val availableProcessors = Runtime.getRuntime().availableProcessors()
        Log.d(TAG, "Recognizing with engine: $engine, lang: $lang, CPU Cores: $availableProcessors")
        
        // 确保 OCR 识别在较高优先级运行，但不超过翻译
        Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT)
        
        val rawResults = if (engine == "GOOGLE") {
            detectWithGoogle(bitmap, lang)
        } else {
            // Paddle 方案
            if (!_isInitialized.value) {
                Log.e(TAG, "PaddleOCR is selected but not initialized!")
                emptyList()
            } else {
                detectBitmap(bitmap)
            }
        }
        
        // 后处理：合并物理位置相近且逻辑上属于同一段落的文本块
        val mergedResults = mergeNearbyResults(rawResults)
        
        // 识别完成后，显式降低优先级，避免干扰后续的翻译推理
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        
        return@withContext mergedResults
    }

    /**
     * 核心算法：合并物理位置相近的 OCR 结果
     * 采用更细腻的合并逻辑，避免过度合并不同段落
     */
    private fun mergeNearbyResults(results: List<OcrResult>): List<OcrResult> {
        if (results.size <= 1) return results

        // 1. 按垂直位置排序
        val sorted = results.sortedBy { it.rect.top }
        val merged = mutableListOf<OcrResult>()
        
        if (sorted.isEmpty()) return merged

        var current = sorted[0]
        val sensitivity = settingsManager.ocrMergeSensitivity

        for (i in 1 until sorted.size) {
            val next = sorted[i]
            
            val r1 = current.rect
            val r2 = next.rect
            
            val h1 = r1.height()
            val h2 = r2.height()
            val avgHeight = (h1 + h2) / 2f
            
            // 垂直间距
            val verticalGap = r2.top - r1.bottom
            
            // 1. 检查是否在同一行或紧邻的下一行
            // 间距阈值：应用用户设置的敏感度系数
            val isVerticallyClose = verticalGap < avgHeight * sensitivity
            
            // 2. 检查高度是否近似（新增强化：避免不同字号/类型的文本块误合并）
            // 如果高度差异超过 30%，通常不属于同一段落（例如正文和脚注、标题）
            val heightDiffRatio = Math.abs(h1 - h2) / Math.max(h1, h2)
            val isHeightSimilar = heightDiffRatio < 0.3
            
            // 3. 检查水平对齐和重叠
            val horizontalOverlap = Math.max(0, Math.min(r1.right, r2.right) - Math.max(r1.left, r2.left))
            val r1Width = r1.width()
            val r2Width = r2.width()
            
            // 对齐判断：
            // - 情况 A: 左右边界基本对齐（多行文本段落）
            // - 情况 B: 下一行起始位置与上一行接近（首行缩进或常规左对齐）
            // 敏感度同样影响对齐判断的宽松度
            val leftAlignDiff = Math.abs(r1.left - r2.left)
            val isHorizontallyAligned = leftAlignDiff < avgHeight * (sensitivity * 3) || 
                                       horizontalOverlap > Math.min(r1Width, r2Width) * 0.7
            
            // 4. 语义连贯性预判
            val endsWithSentenceStopper = current.text.trim().let { 
                it.endsWith(".") || it.endsWith("?") || it.endsWith("!") || 
                it.endsWith("。") || it.endsWith("？") || it.endsWith("！")
            }

            // 合并策略：只有垂直非常接近、高度近似且水平对齐，且上一行不像是一个完整的句子结尾时才合并
            if (isVerticallyClose && isHeightSimilar && isHorizontallyAligned && !endsWithSentenceStopper) {
                // 执行合并
                val newText = if (current.text.lastOrNull()?.isLetter() == true && 
                                 next.text.firstOrNull()?.isLetter() == true) {
                    current.text + " " + next.text
                } else {
                    current.text + next.text
                }
                
                val newBounds = listOf(
                    Point(Math.min(r1.left, r2.left), Math.min(r1.top, r2.top)),
                    Point(Math.max(r1.right, r2.right), Math.min(r1.top, r2.top)),
                    Point(Math.max(r1.right, r2.right), Math.max(r1.bottom, r2.bottom)),
                    Point(Math.min(r1.left, r2.left), Math.max(r1.bottom, r2.bottom))
                )
                
                current = OcrResult(newText, (current.confidence + next.confidence) / 2, newBounds)
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        
        return merged
    }

    /**
     * 从 Assets 复制模型并初始化
     */
    fun initModelFromAssets(): Boolean {
        try {
            val modelRoot = File(context.filesDir, "ocr")
            if (!modelRoot.exists()) modelRoot.mkdirs()

            // 复制模型文件
            val detPath = copyAssetToFile("ocr/models/ch_ppocr_mobile_v2.0_det_slim_opt.nb", File(modelRoot, "det.nb"))
            val recPath = copyAssetToFile("ocr/models/ch_ppocr_mobile_v2.0_rec_slim_opt.nb", File(modelRoot, "rec.nb"))
            val clsPath = copyAssetToFile("ocr/models/ch_ppocr_mobile_v2.0_cls_slim_opt.nb", File(modelRoot, "cls.nb"))
            val configPath = copyAssetToFile("ocr/config.txt", File(modelRoot, "config.txt"))
            val labelPath = copyAssetToFile("ocr/labels/ppocr_keys_v1.txt", File(modelRoot, "label.txt"))

            val success = loadModel(detPath, recPath, clsPath, configPath, labelPath)
            _isInitialized.value = success
            return success
        } catch (e: Exception) {
            Log.e(TAG, "initModelFromAssets failed", e)
            _isInitialized.value = false
            return false
        }
    }

    private fun copyAssetToFile(assetPath: String, targetFile: File): String {
        if (!targetFile.exists()) {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return targetFile.absolutePath
    }

    /**
     * 加载识别模型
     */
    fun loadModel(
        detModelPath: String,
        recModelPath: String,
        clsModelPath: String,
        configPath: String,
        labelPath: String,
        cpuThreadNum: Int = 4,
        cpuPowerMode: String = "LITE_POWER_HIGH"
    ): Boolean {
        if (ctx != 0L) {
            release()
        }
        ctx = nativeInit(
            detModelPath,
            clsModelPath,
            recModelPath,
            configPath,
            labelPath,
            cpuThreadNum,
            cpuPowerMode
        )
        val success = ctx != 0L
        _isInitialized.value = success
        return success
    }

    private external fun nativeInit(
        detModelPath: String,
        clsModelPath: String,
        recModelPath: String,
        configPath: String,
        labelPath: String,
        cpuThreadNum: Int,
        cpuPowerMode: String
    ): Long

    private external fun nativeRelease(ctx: Long): Boolean

    private external fun nativeProcess(
        ctx: Long,
        inTextureId: Int,
        outTextureId: Int,
        textureWidth: Int,
        textureHeight: Int,
        savedImagePath: String
    ): Boolean

    private external fun nativeDetectBitmap(
        ctx: Long,
        bitmap: Bitmap
    ): Array<OcrResult>?

    fun detectBitmap(bitmap: Bitmap): List<OcrResult> {
        if (ctx == 0L) {
            Log.w(TAG, "detectBitmap skipped: model is not initialized")
            return emptyList()
        }
        return nativeDetectBitmap(ctx, bitmap)?.toList() ?: emptyList()
    }

    /**
     * 使用 Google ML Kit 进行识别 (备选方案)
     * 支持语言：Chinese, Japanese, Latin
     */
    suspend fun detectWithGoogle(bitmap: Bitmap, lang: String = "zh"): List<OcrResult> {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = when (lang.lowercase()) {
                "zh" -> googleChineseRecognizer
                "ja" -> googleJapaneseRecognizer
                "ko" -> googleKoreanRecognizer
                else -> googleLatinRecognizer
            }
            
            val visionText = recognizer.process(image).asDeferred().await()
            val results = mutableListOf<OcrResult>()
            
            val blocks = visionText.textBlocks
            val useLineLevel = settingsManager.googleOcrGranularity == SettingsManager.GOOGLE_OCR_GRANULARITY_LINE
            
            for (block in blocks) {
                if (useLineLevel) {
                    val lines = block.lines
                    for (line in lines) {
                        val text = line.text
                        val confidence = 1.0f 
                        val rect = line.boundingBox ?: continue
                        val points = listOf(
                            Point(rect.left, rect.top),
                            Point(rect.right, rect.top),
                            Point(rect.right, rect.bottom),
                            Point(rect.left, rect.bottom)
                        )
                        results.add(OcrResult(text, confidence, points))
                    }
                } else {
                    val text = block.text
                    val confidence = 1.0f 
                    val rect = block.boundingBox ?: continue
                    val points = listOf(
                        Point(rect.left, rect.top),
                        Point(rect.right, rect.top),
                        Point(rect.right, rect.bottom),
                        Point(rect.left, rect.bottom)
                    )
                    results.add(OcrResult(text, confidence, points))
                }
            }
            results
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit detection failed", e)
            emptyList()
        }
    }

    fun processTexture(
        inTextureId: Int,
        outTextureId: Int,
        textureWidth: Int,
        textureHeight: Int,
        savedImagePath: String
    ): Boolean {
        if (ctx == 0L) {
            Log.w(TAG, "processTexture skipped: model is not initialized")
            return false
        }
        return nativeProcess(
            ctx,
            inTextureId,
            outTextureId,
            textureWidth,
            textureHeight,
            savedImagePath
        )
    }

    fun release(): Boolean {
        if (ctx == 0L) return false
        val ok = nativeRelease(ctx)
        if (ok) {
            ctx = 0L
            _isInitialized.value = false
        }
        return ok
    }
}
