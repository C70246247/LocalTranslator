package com.buildsession.localtranslator.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _settingsChangedFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val settingsChangedFlow: SharedFlow<String> = _settingsChangedFlow.asSharedFlow()

    companion object {
        private const val KEY_OPACITY = "floating_window_opacity"
        private const val KEY_TEXT_COLOR = "floating_window_text_color"
        private const val KEY_DEFAULT_MODEL = "default_model_name"
        private const val KEY_REMOVE_SPACES = "remove_spaces"
        private const val KEY_AUTO_ENABLE_ACCESSIBILITY = "auto_enable_accessibility" // 弃用，迁移到合并开关
        private const val KEY_MERGED_AUTO_ACCESSIBILITY = "merged_auto_accessibility"
        
        // Translation settings
        private const val KEY_TRANS_SOURCE_LANG = "trans_source_lang"
        private const val KEY_TRANS_TARGET_LANG = "trans_target_lang"
        private const val KEY_TRANS_MODEL_STRATEGY = "trans_model_strategy" // "ask" or modelId
        private const val KEY_TRANS_AUTO_ACCESSIBILITY = "trans_auto_accessibility" // 弃用，迁移到合并开关
        private const val KEY_IS_FIRST_LAUNCH = "is_first_launch"
        private const val KEY_TRANS_CONTEXT_LENGTH = "trans_context_length"
        private const val KEY_TRANS_USE_MMAP = "trans_use_mmap"
        private const val KEY_TRANS_ENGINE = "trans_engine"
        private const val KEY_OCR_MAX_SIDE = "ocr_max_side"
        private const val KEY_OCR_CAPTURE_MODE = "ocr_capture_mode"
        private const val KEY_OCR_TRANSLATION_MODE = "ocr_translation_mode"
        private const val KEY_OCR_MERGE_SENSITIVITY = "ocr_merge_sensitivity"
        private const val KEY_GOOGLE_OCR_GRANULARITY = "google_ocr_granularity"
        private const val KEY_OCR_FULL_SCREEN_BG_OPACITY = "ocr_full_screen_bg_opacity"
        private const val KEY_OCR_USE_ACCESSIBILITY_OVERLAY = "ocr_use_accessibility_overlay"
        private const val KEY_STT_PARTIAL_CONFIRM_THRESHOLD = "stt_partial_confirm_threshold"
        private const val KEY_STT_TRANSLATION_CONTEXT_LENGTH = "stt_translation_context_length"
        
        const val DEFAULT_OPACITY = 0.8f
        const val DEFAULT_TEXT_COLOR = "#FFFFFF"
        const val DEFAULT_CONTEXT_LENGTH = 2048
        const val DEFAULT_OCR_MAX_SIDE = 1500
        const val DEFAULT_OCR_MERGE_SENSITIVITY = 0.5f
        const val DEFAULT_OCR_FULL_SCREEN_BG_OPACITY = 0.5f
        const val DEFAULT_STT_PARTIAL_CONFIRM_THRESHOLD = 10
        const val DEFAULT_STT_TRANSLATION_CONTEXT_LENGTH = 0
        const val OCR_MODE_AUTO_FILL = "AUTO_FILL"
        const val OCR_MODE_FULL_SCREEN = "FULL_SCREEN"
        
        const val CAPTURE_MODE_MEDIA_PROJECTION = "MEDIA_PROJECTION"
        const val CAPTURE_MODE_ACCESSIBILITY = "ACCESSIBILITY"
        const val CAPTURE_MODE_ROOT = "ROOT"

        const val GOOGLE_OCR_GRANULARITY_LINE = "LINE"
        const val GOOGLE_OCR_GRANULARITY_BLOCK = "BLOCK"
    }

    var sttPartialConfirmThreshold: Int
        get() = prefs.getInt(KEY_STT_PARTIAL_CONFIRM_THRESHOLD, DEFAULT_STT_PARTIAL_CONFIRM_THRESHOLD)
        set(value) = prefs.edit().putInt(KEY_STT_PARTIAL_CONFIRM_THRESHOLD, value).apply()

    var sttTranslationContextLength: Int
        get() = prefs.getInt(KEY_STT_TRANSLATION_CONTEXT_LENGTH, DEFAULT_STT_TRANSLATION_CONTEXT_LENGTH)
        set(value) = prefs.edit().putInt(KEY_STT_TRANSLATION_CONTEXT_LENGTH, value).apply()

    var googleOcrGranularity: String
        get() = prefs.getString(KEY_GOOGLE_OCR_GRANULARITY, GOOGLE_OCR_GRANULARITY_LINE) ?: GOOGLE_OCR_GRANULARITY_LINE
        set(value) = prefs.edit().putString(KEY_GOOGLE_OCR_GRANULARITY, value).apply()

    var ocrMergeSensitivity: Float
        get() = prefs.getFloat(KEY_OCR_MERGE_SENSITIVITY, DEFAULT_OCR_MERGE_SENSITIVITY)
        set(value) = prefs.edit().putFloat(KEY_OCR_MERGE_SENSITIVITY, value).apply()

    var ocrFullScreenBgOpacity: Float
        get() = prefs.getFloat(KEY_OCR_FULL_SCREEN_BG_OPACITY, DEFAULT_OCR_FULL_SCREEN_BG_OPACITY)
        set(value) = prefs.edit().putFloat(KEY_OCR_FULL_SCREEN_BG_OPACITY, value).apply()

    var ocrUseAccessibilityOverlay: Boolean
        get() = prefs.getBoolean(KEY_OCR_USE_ACCESSIBILITY_OVERLAY, false)
        set(value) = prefs.edit().putBoolean(KEY_OCR_USE_ACCESSIBILITY_OVERLAY, value).apply()

    var ocrTranslationMode: String
        get() = prefs.getString(KEY_OCR_TRANSLATION_MODE, OCR_MODE_AUTO_FILL) ?: OCR_MODE_AUTO_FILL
        set(value) = prefs.edit().putString(KEY_OCR_TRANSLATION_MODE, value).apply()

    var ocrCaptureMode: String
        get() = prefs.getString(KEY_OCR_CAPTURE_MODE, CAPTURE_MODE_MEDIA_PROJECTION) ?: CAPTURE_MODE_MEDIA_PROJECTION
        set(value) = prefs.edit().putString(KEY_OCR_CAPTURE_MODE, value).apply()

    var transUseMmap: Boolean
        get() = prefs.getBoolean(KEY_TRANS_USE_MMAP, true)
        set(value) = prefs.edit().putBoolean(KEY_TRANS_USE_MMAP, value).apply()

    var transContextLength: Int
        get() = prefs.getInt(KEY_TRANS_CONTEXT_LENGTH, DEFAULT_CONTEXT_LENGTH)
        set(value) = prefs.edit().putInt(KEY_TRANS_CONTEXT_LENGTH, value).apply()

    var transEngine: String
        get() = prefs.getString(KEY_TRANS_ENGINE, "LLM") ?: "LLM"
        set(value) {
            prefs.edit().putString(KEY_TRANS_ENGINE, value).apply()
            _settingsChangedFlow.tryEmit(KEY_TRANS_ENGINE)
        }

    var removeSpaces: Boolean
        get() = prefs.getBoolean(KEY_REMOVE_SPACES, false)
        set(value) = prefs.edit().putBoolean(KEY_REMOVE_SPACES, value).apply()

    // 迁移逻辑：如果合并开关从未设置过，则参考旧的开关（只要有一个开着就默认开）
    var mergedAutoAccessibility: Boolean
        get() {
            if (!prefs.contains(KEY_MERGED_AUTO_ACCESSIBILITY)) {
                val oldTts = prefs.getBoolean(KEY_AUTO_ENABLE_ACCESSIBILITY, false)
                val oldTrans = prefs.getBoolean(KEY_TRANS_AUTO_ACCESSIBILITY, false)
                val initialValue = oldTts || oldTrans
                prefs.edit().putBoolean(KEY_MERGED_AUTO_ACCESSIBILITY, initialValue).apply()
                return initialValue
            }
            return prefs.getBoolean(KEY_MERGED_AUTO_ACCESSIBILITY, false)
        }
        set(value) = prefs.edit().putBoolean(KEY_MERGED_AUTO_ACCESSIBILITY, value).apply()

    @Deprecated("Use mergedAutoAccessibility", ReplaceWith("mergedAutoAccessibility"))
    var autoEnableAccessibility: Boolean
        get() = mergedAutoAccessibility
        set(value) { mergedAutoAccessibility = value }

    var defaultModelName: String?
        get() = prefs.getString(KEY_DEFAULT_MODEL, null)
        set(value) = prefs.edit().putString(KEY_DEFAULT_MODEL, value).apply()

    var opacity: Float
        get() = prefs.getFloat(KEY_OPACITY, DEFAULT_OPACITY)
        set(value) = prefs.edit().putFloat(KEY_OPACITY, value).apply()

    var ocrEngine: String
        get() = prefs.getString("ocr_engine", "GOOGLE") ?: "GOOGLE"
        set(value) = prefs.edit().putString("ocr_engine", value).apply()

    var ocrMaxSide: Int
        get() = prefs.getInt(KEY_OCR_MAX_SIDE, DEFAULT_OCR_MAX_SIDE)
        set(value) = prefs.edit().putInt(KEY_OCR_MAX_SIDE, value).apply()

    var textColorHex: String
        get() = prefs.getString(KEY_TEXT_COLOR, DEFAULT_TEXT_COLOR) ?: DEFAULT_TEXT_COLOR
        set(value) = prefs.edit().putString(KEY_TEXT_COLOR, value).apply()

    // Translation getters/setters
    var transSourceLang: String
        get() = prefs.getString(KEY_TRANS_SOURCE_LANG, "自动检测") ?: "自动检测"
        set(value) = prefs.edit().putString(KEY_TRANS_SOURCE_LANG, value).apply()

    var transTargetLang: String
        get() = prefs.getString(KEY_TRANS_TARGET_LANG, "英语") ?: "英语"
        set(value) = prefs.edit().putString(KEY_TRANS_TARGET_LANG, value).apply()

    var transModelStrategy: String
        get() = prefs.getString(KEY_TRANS_MODEL_STRATEGY, "ask") ?: "ask"
        set(value) = prefs.edit().putString(KEY_TRANS_MODEL_STRATEGY, value).apply()

    var isFirstLaunch: Boolean
        get() = prefs.getBoolean(KEY_IS_FIRST_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_IS_FIRST_LAUNCH, value).apply()

    @Deprecated("Use mergedAutoAccessibility", ReplaceWith("mergedAutoAccessibility"))
    var transAutoAccessibility: Boolean
        get() = mergedAutoAccessibility
        set(value) { mergedAutoAccessibility = value }

    fun getTextColor(): Color {
        return try {
            Color(android.graphics.Color.parseColor(textColorHex))
        } catch (e: Exception) {
            Color.White
        }
    }
}
