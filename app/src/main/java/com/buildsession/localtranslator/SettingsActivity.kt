package com.buildsession.localtranslator

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.buildsession.localtranslator.model.ModelManager
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.buildsession.localtranslator.translation.TranslationModel
import com.buildsession.localtranslator.TranslationLanguages
import com.buildsession.localtranslator.translation.TranslationManager
import com.buildsession.localtranslator.ui.theme.localtranslatorTheme
import com.buildsession.localtranslator.util.AccessibilityUtils
import com.buildsession.localtranslator.util.SettingsManager
import androidx.compose.ui.res.stringResource
import rikka.shizuku.Shizuku

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Add Shizuku permission listener
        Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 1001 && grantResult == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, getString(R.string.shizuku_reclick_hint), Toast.LENGTH_SHORT).show()
            }
        }

        enableEdgeToEdge()
        setContent {
            localtranslatorTheme {
                SettingsScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val modelManager = remember { ModelManager(context) }
    val translationManager = remember { TranslationManager(context) }
    
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showProjectMediaDialog by remember { mutableStateOf(false) }
    var mergedAutoAccessibility by remember { mutableStateOf(settingsManager.mergedAutoAccessibility) }
    var showOpenSourceLicenses by remember { mutableStateOf(false) }

    if (showOpenSourceLicenses) {
        OpenSourceLicensesScreen(onBack = { showOpenSourceLicenses = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- 通用设置 ---
            Text(stringResource(R.string.general_settings), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            // 语言设置
            LanguageSettingItem(context)

            // 自动启用无障碍服务
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.auto_enable_accessibility_service), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.auto_enable_accessibility_service_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = mergedAutoAccessibility,
                    onCheckedChange = { checked ->
                        if (checked) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SECURE_SETTINGS) 
                                != PackageManager.PERMISSION_GRANTED) {
                                showPermissionDialog = true
                            } else {
                                mergedAutoAccessibility = true
                                settingsManager.mergedAutoAccessibility = true
                                AccessibilityUtils.enableAccessibilityService(context)
                            }
                        } else {
                            mergedAutoAccessibility = false
                            settingsManager.mergedAutoAccessibility = false
                        }
                    }
                )
            }

            // 绕过屏幕截图授权
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.bypass_screen_capture_auth), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.bypass_screen_capture_auth_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { showProjectMediaDialog = true }) {
                    Text(stringResource(R.string.shizuku_auth))
                }
            }

            HorizontalDivider()

            // --- 语音识别设置 ---
            Text(stringResource(R.string.asr_settings), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            TtsSettingsSection(settingsManager, modelManager)

            HorizontalDivider()

            // --- 翻译设置 ---
            Text(stringResource(R.string.trans_settings), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            TranslationSettingsSection(settingsManager, translationManager)

            HorizontalDivider()

            // --- OCR 设置 ---
            Text(stringResource(R.string.ocr_settings), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            OcrSettingsSection(settingsManager)

            HorizontalDivider()

            // --- 关于应用 ---
            Text(stringResource(R.string.about_app), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            AboutSettingsSection(
                settingsManager = settingsManager,
                onOpenSourceLicenses = { showOpenSourceLicenses = true }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showPermissionDialog) {
        PermissionDialog(
            onDismiss = { showPermissionDialog = false },
            onPermissionGranted = {
                mergedAutoAccessibility = true
                settingsManager.mergedAutoAccessibility = true
                AccessibilityUtils.enableAccessibilityService(context)
            }
        )
    }

    if (showProjectMediaDialog) {
        ProjectMediaPermissionDialog(
            onDismiss = { showProjectMediaDialog = false }
        )
    }
}

@Composable
fun LanguageSettingItem(context: Context) {
    Column {
        Text(stringResource(R.string.app_language), style = MaterialTheme.typography.bodyLarge)
        if (Build.VERSION.SDK_INT >= 35) { // 用户要求 Android 15+ 跳转系统设置
            OutlinedButton(
                onClick = {
                    try {
                        val intent = Intent(AndroidSettings.ACTION_APP_LOCALE_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.ocr_init_failed), Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.go_to_system_settings))
            }
        } else {
            // 低版本显示下拉菜单
            var expanded by remember { mutableStateOf(false) }
            val languages = listOf(
                stringResource(R.string.lang_auto), 
                stringResource(R.string.lang_zh), 
                stringResource(R.string.lang_en)
            )
            var selectedLanguage by remember { mutableStateOf(languages[0]) }

            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selectedLanguage)
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    languages.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang) },
                            onClick = {
                                selectedLanguage = lang
                                expanded = false
                                Toast.makeText(context, context.getString(R.string.ocr_hint), Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TtsSettingsSection(settingsManager: SettingsManager, modelManager: ModelManager) {
    var opacity by remember { mutableStateOf(settingsManager.opacity) }
    var textColorHex by remember { mutableStateOf(settingsManager.textColorHex) }
    var defaultModel by remember { mutableStateOf(settingsManager.defaultModelName) }
    var removeSpaces by remember { mutableStateOf(settingsManager.removeSpaces) }
    var sttThreshold by remember { mutableFloatStateOf(settingsManager.sttPartialConfirmThreshold.toFloat()) }
    var sttContextLength by remember { mutableFloatStateOf(settingsManager.sttTranslationContextLength.toFloat()) }
    
    val downloadedModels = remember { modelManager.getDownloadedModels() }
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text(stringResource(R.string.floating_window_opacity, (opacity * 100).toInt()))
            Slider(
                value = opacity,
                onValueChange = { 
                    opacity = it
                    settingsManager.opacity = it
                },
                valueRange = 0.1f..1.0f
            )
        }

        Column {
            Text(stringResource(R.string.text_color_hex))
            OutlinedTextField(
                value = textColorHex,
                onValueChange = { 
                    textColorHex = it
                    settingsManager.textColorHex = it
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("#FFFFFF") }
            )
        }

        Column {
            Text(stringResource(R.string.default_start_model))
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(defaultModel ?: stringResource(R.string.not_set_ask_every_time))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    downloadedModels.forEach { modelName ->
                        DropdownMenuItem(
                            text = { Text(modelName) },
                            onClick = {
                                defaultModel = modelName
                                settingsManager.defaultModelName = modelName
                                expanded = false
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.not_set_ask_every_time)) },
                        onClick = {
                            defaultModel = null
                            settingsManager.defaultModelName = null
                            expanded = false
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.remove_spaces_when_injecting), style = MaterialTheme.typography.bodyLarge)
            }
            Switch(
                checked = removeSpaces,
                onCheckedChange = { 
                    removeSpaces = it
                    settingsManager.removeSpaces = it
                }
            )
        }

        Column {
            Text(stringResource(R.string.stt_partial_confirm_threshold_title, sttThreshold.toInt()), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.stt_partial_confirm_threshold_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = sttThreshold,
                onValueChange = { 
                    sttThreshold = it
                    settingsManager.sttPartialConfirmThreshold = it.toInt()
                },
                valueRange = 3f..30f,
                steps = 27
            )
        }

        Column {
            Text(stringResource(R.string.stt_translation_context_length_title, sttContextLength.toInt()), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.stt_translation_context_length_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = sttContextLength,
                onValueChange = { 
                    sttContextLength = it
                    settingsManager.sttTranslationContextLength = it.toInt()
                },
                valueRange = 0f..200f,
                steps = 40 // 每 5 个字符一个 step (200/5 = 40)
            )
        }
    }
}

@Composable
fun AboutSettingsSection(settingsManager: SettingsManager, onOpenSourceLicenses: () -> Unit) {
    var showAgreementOptions by remember { mutableStateOf(false) }
    var showAgreementContent by remember { mutableStateOf(false) }
    var showWithdrawConfirmation by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = { showAgreementOptions = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.user_agreement_settings))
        }
        OutlinedButton(
            onClick = onOpenSourceLicenses,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.open_source_licenses))
        }
    }

    if (showAgreementOptions) {
        AlertDialog(
            onDismissRequest = { showAgreementOptions = false },
            title = { Text(stringResource(R.string.user_agreement_dialog_title)) },
            text = { Text("请选择查看协议内容或撤回已同意的协议。") },
            confirmButton = {
                TextButton(onClick = {
                    showAgreementOptions = false
                    showAgreementContent = true
                }) {
                    Text(stringResource(R.string.user_agreement_view))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAgreementOptions = false
                    showWithdrawConfirmation = true
                }) {
                    Text(stringResource(R.string.user_agreement_withdraw), color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    if (showAgreementContent) {
        AlertDialog(
            onDismissRequest = { showAgreementContent = false },
            title = { Text(stringResource(R.string.first_launch_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.first_launch_content),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.first_launch_disclaimer_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(R.string.first_launch_disclaimer_content),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAgreementContent = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    if (showWithdrawConfirmation) {
        AlertDialog(
            onDismissRequest = { /* 不允许随意消失 */ },
            title = { Text(stringResource(R.string.user_agreement_withdrawn_title)) },
            text = { Text(stringResource(R.string.user_agreement_withdrawn_content)) },
            confirmButton = {
                Button(
                    onClick = {
                        settingsManager.isFirstLaunch = true
                        (context as? ComponentActivity)?.finishAffinity()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceLicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val licenses = remember {
        context.assets.open("third_party_licenses.txt").bufferedReader().use { it.readText() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.open_source_licenses)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.open_source_licenses_disclaimer), style = MaterialTheme.typography.bodyMedium)
            Text(licenses, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun OcrSettingsSection(settingsManager: SettingsManager) {
    var ocrCaptureMode by remember { mutableStateOf(settingsManager.ocrCaptureMode) }
    var ocrEngine by remember { mutableStateOf(settingsManager.ocrEngine) }
    var ocrMaxSide by remember { mutableFloatStateOf(settingsManager.ocrMaxSide.toFloat()) }
    var ocrTransMode by remember { mutableStateOf(settingsManager.ocrTranslationMode) }
    var ocrMergeSensitivity by remember { mutableFloatStateOf(settingsManager.ocrMergeSensitivity) }
    var googleGranularity by remember { mutableStateOf(settingsManager.googleOcrGranularity) }
    var fullScreenBgOpacity by remember { mutableFloatStateOf(settingsManager.ocrFullScreenBgOpacity) }
    var useAccessibilityOverlay by remember { mutableStateOf(settingsManager.ocrUseAccessibilityOverlay) }
    
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            Text(stringResource(R.string.ocr_capture_mode_title), style = MaterialTheme.typography.labelMedium)
            
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { 
                    ocrCaptureMode = SettingsManager.CAPTURE_MODE_MEDIA_PROJECTION
                    settingsManager.ocrCaptureMode = SettingsManager.CAPTURE_MODE_MEDIA_PROJECTION
                }) {
                    RadioButton(selected = ocrCaptureMode == SettingsManager.CAPTURE_MODE_MEDIA_PROJECTION, onClick = { 
                        ocrCaptureMode = SettingsManager.CAPTURE_MODE_MEDIA_PROJECTION
                        settingsManager.ocrCaptureMode = SettingsManager.CAPTURE_MODE_MEDIA_PROJECTION
                    })
                    Text(stringResource(R.string.ocr_capture_mode_media_projection))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { 
                    ocrCaptureMode = SettingsManager.CAPTURE_MODE_ACCESSIBILITY
                    settingsManager.ocrCaptureMode = SettingsManager.CAPTURE_MODE_ACCESSIBILITY
                }) {
                    RadioButton(selected = ocrCaptureMode == SettingsManager.CAPTURE_MODE_ACCESSIBILITY, onClick = { 
                        ocrCaptureMode = SettingsManager.CAPTURE_MODE_ACCESSIBILITY
                        settingsManager.ocrCaptureMode = SettingsManager.CAPTURE_MODE_ACCESSIBILITY
                    })
                    Text(stringResource(R.string.ocr_capture_mode_accessibility))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { 
                    ocrCaptureMode = SettingsManager.CAPTURE_MODE_ROOT
                    settingsManager.ocrCaptureMode = SettingsManager.CAPTURE_MODE_ROOT
                }) {
                    RadioButton(selected = ocrCaptureMode == SettingsManager.CAPTURE_MODE_ROOT, onClick = { 
                        ocrCaptureMode = SettingsManager.CAPTURE_MODE_ROOT
                        settingsManager.ocrCaptureMode = SettingsManager.CAPTURE_MODE_ROOT
                    })
                    Text(stringResource(R.string.ocr_capture_mode_root))
                }
            }
            
            Text(
                text = stringResource(R.string.ocr_capture_mode_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Column {
            Text(stringResource(R.string.ocr_engine_select_title), style = MaterialTheme.typography.labelMedium)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { 
                    ocrEngine = "GOOGLE"
                    settingsManager.ocrEngine = "GOOGLE"
                }) {
                    RadioButton(selected = ocrEngine == "GOOGLE", onClick = { 
                        ocrEngine = "GOOGLE"
                        settingsManager.ocrEngine = "GOOGLE"
                    })
                    Text(stringResource(R.string.ocr_engine_google_recommend))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { 
                    ocrEngine = "PADDLE"
                    settingsManager.ocrEngine = "PADDLE"
                }) {
                    RadioButton(selected = ocrEngine == "PADDLE", onClick = { 
                        ocrEngine = "PADDLE"
                        settingsManager.ocrEngine = "PADDLE"
                    })
                    Text(stringResource(R.string.ocr_engine_paddle))
                }
            }
            
            Text(
                text = if (ocrEngine == "GOOGLE") stringResource(R.string.ocr_engine_google_desc)
                       else stringResource(R.string.ocr_engine_paddle_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        if (ocrEngine == "GOOGLE") {
            Column {
                Text(stringResource(R.string.google_ocr_granularity_title), style = MaterialTheme.typography.labelMedium)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { 
                        googleGranularity = SettingsManager.GOOGLE_OCR_GRANULARITY_LINE
                        settingsManager.googleOcrGranularity = SettingsManager.GOOGLE_OCR_GRANULARITY_LINE
                    }) {
                        RadioButton(selected = googleGranularity == SettingsManager.GOOGLE_OCR_GRANULARITY_LINE, onClick = { 
                            googleGranularity = SettingsManager.GOOGLE_OCR_GRANULARITY_LINE
                            settingsManager.googleOcrGranularity = SettingsManager.GOOGLE_OCR_GRANULARITY_LINE
                        })
                        Text(stringResource(R.string.google_ocr_granularity_line))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { 
                        googleGranularity = SettingsManager.GOOGLE_OCR_GRANULARITY_BLOCK
                        settingsManager.googleOcrGranularity = SettingsManager.GOOGLE_OCR_GRANULARITY_BLOCK
                    }) {
                        RadioButton(selected = googleGranularity == SettingsManager.GOOGLE_OCR_GRANULARITY_BLOCK, onClick = { 
                            googleGranularity = SettingsManager.GOOGLE_OCR_GRANULARITY_BLOCK
                            settingsManager.googleOcrGranularity = SettingsManager.GOOGLE_OCR_GRANULARITY_BLOCK
                        })
                        Text(stringResource(R.string.google_ocr_granularity_block))
                    }
                }
                
                Text(
                    text = stringResource(R.string.google_ocr_granularity_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }

        Column {
            Text(stringResource(R.string.ocr_merge_sensitivity_title, (ocrMergeSensitivity * 100).toInt()), style = MaterialTheme.typography.labelMedium)
            Text(
                stringResource(R.string.ocr_merge_sensitivity_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Slider(
                value = ocrMergeSensitivity,
                onValueChange = { ocrMergeSensitivity = it },
                onValueChangeFinished = {
                    settingsManager.ocrMergeSensitivity = ocrMergeSensitivity
                },
                valueRange = 0.1f..1.5f,
                steps = 14
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Column {
            Text(stringResource(R.string.ocr_trans_mode_title), style = MaterialTheme.typography.labelMedium)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { 
                    ocrTransMode = SettingsManager.OCR_MODE_AUTO_FILL
                    settingsManager.ocrTranslationMode = SettingsManager.OCR_MODE_AUTO_FILL
                }) {
                    RadioButton(selected = ocrTransMode == SettingsManager.OCR_MODE_AUTO_FILL, onClick = { 
                        ocrTransMode = SettingsManager.OCR_MODE_AUTO_FILL
                        settingsManager.ocrTranslationMode = SettingsManager.OCR_MODE_AUTO_FILL
                    })
                    Text(stringResource(R.string.ocr_trans_mode_auto_fill))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { 
                    ocrTransMode = SettingsManager.OCR_MODE_FULL_SCREEN
                    settingsManager.ocrTranslationMode = SettingsManager.OCR_MODE_FULL_SCREEN
                }) {
                    RadioButton(selected = ocrTransMode == SettingsManager.OCR_MODE_FULL_SCREEN, onClick = { 
                        ocrTransMode = SettingsManager.OCR_MODE_FULL_SCREEN
                        settingsManager.ocrTranslationMode = SettingsManager.OCR_MODE_FULL_SCREEN
                    })
                    Text(stringResource(R.string.ocr_trans_mode_full_screen))
                }
            }
            
            Text(
                text = stringResource(R.string.ocr_trans_mode_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (ocrEngine == "PADDLE" && ocrTransMode == SettingsManager.OCR_MODE_FULL_SCREEN) {
                Text(
                    text = stringResource(R.string.ocr_trans_mode_paddle_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (ocrTransMode == SettingsManager.OCR_MODE_FULL_SCREEN) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.ocr_use_accessibility_overlay_title), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.ocr_use_accessibility_overlay_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = useAccessibilityOverlay,
                        onCheckedChange = { checked ->
                            useAccessibilityOverlay = checked
                            settingsManager.ocrUseAccessibilityOverlay = checked
                        }
                    )
                }
            }
        }

        if (ocrTransMode == SettingsManager.OCR_MODE_FULL_SCREEN) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Column {
                Text(stringResource(R.string.ocr_full_screen_bg_opacity_title, (fullScreenBgOpacity * 100).toInt()), style = MaterialTheme.typography.labelMedium)
                Text(
                    stringResource(R.string.ocr_full_screen_bg_opacity_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = fullScreenBgOpacity,
                    onValueChange = { fullScreenBgOpacity = it },
                    onValueChangeFinished = {
                        settingsManager.ocrFullScreenBgOpacity = fullScreenBgOpacity
                    },
                    valueRange = 0.0f..0.9f,
                    steps = 18
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Column {
            Text(stringResource(R.string.ocr_quality_title), style = MaterialTheme.typography.labelMedium)
            Text(
                stringResource(R.string.ocr_quality_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Slider(
                value = ocrMaxSide,
                onValueChange = { ocrMaxSide = it },
                onValueChangeFinished = {
                    settingsManager.ocrMaxSide = ocrMaxSide.toInt()
                },
                valueRange = 720f..2560f,
                steps = 18 // (2560-720)/100 ≈ 18
            )
            
            val qualityLabel = when {
                ocrMaxSide < 1000 -> stringResource(R.string.ocr_quality_smooth)
                ocrMaxSide < 1600 -> stringResource(R.string.ocr_quality_balanced)
                ocrMaxSide < 2100 -> stringResource(R.string.ocr_quality_high)
                else -> stringResource(R.string.ocr_quality_ultra)
            }
            
            Text(
                text = "$qualityLabel: ${ocrMaxSide.toInt()}px",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
fun TranslationSettingsSection(settingsManager: SettingsManager, translationManager: TranslationManager) {
    var transEngine by remember { mutableStateOf(settingsManager.transEngine) }
    var sourceLang by remember { mutableStateOf(settingsManager.transSourceLang) }
    var targetLang by remember { mutableStateOf(settingsManager.transTargetLang) }
    var modelStrategy by remember { mutableStateOf(settingsManager.transModelStrategy) }
    var contextLength by remember { mutableFloatStateOf(settingsManager.transContextLength.toFloat()) }
    var useMmap by remember { mutableStateOf(settingsManager.transUseMmap) }
    val availableModels = translationManager.availableModels
    val languages = TranslationLanguages

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text(stringResource(R.string.trans_engine_select_title), style = MaterialTheme.typography.labelMedium)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { 
                    transEngine = "LLM"
                    settingsManager.transEngine = "LLM"
                }) {
                    RadioButton(selected = transEngine == "LLM", onClick = { 
                        transEngine = "LLM"
                        settingsManager.transEngine = "LLM"
                    })
                    Text(stringResource(R.string.trans_engine_llm))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { 
                    transEngine = "GOOGLE"
                    settingsManager.transEngine = "GOOGLE"
                }) {
                    RadioButton(selected = transEngine == "GOOGLE", onClick = { 
                        transEngine = "GOOGLE"
                        settingsManager.transEngine = "GOOGLE"
                    })
                    Text(stringResource(R.string.trans_engine_google))
                }
            }
            
            Text(
                text = if (transEngine == "LLM") stringResource(R.string.trans_engine_llm_desc)
                       else stringResource(R.string.trans_engine_google_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        if (transEngine == "LLM") {
            Column {
                Text(stringResource(R.string.trans_use_mmap_title), style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.trans_use_mmap_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = useMmap,
                        onCheckedChange = {
                            useMmap = it
                            settingsManager.transUseMmap = it
                        }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Column {
                Text(stringResource(R.string.trans_context_length_title), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.trans_context_length_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Slider(
                    value = contextLength,
                    onValueChange = { contextLength = it },
                    onValueChangeFinished = {
                        settingsManager.transContextLength = contextLength.toInt()
                    },
                    valueRange = 256f..4096f,
                    steps = 14 // (4096-256)/256 = 15 points
                )
                Text(
                    text = "${contextLength.toInt()} Tokens",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.End)
                )
            }

            Column {
                Text(stringResource(R.string.default_load_model), style = MaterialTheme.typography.labelMedium)
                var showModelStrategyMenu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { showModelStrategyMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(when(modelStrategy) {
                            "ask" -> stringResource(R.string.ask_every_time)
                            else -> availableModels.find { it.id == modelStrategy }?.name ?: stringResource(R.string.ask_every_time)
                        })
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = showModelStrategyMenu, onDismissRequest = { showModelStrategyMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ask_every_time)) },
                            onClick = {
                                modelStrategy = "ask"
                                settingsManager.transModelStrategy = "ask"
                                showModelStrategyMenu = false
                            }
                        )
                        availableModels.filter { translationManager.isModelDownloaded(it.id) }.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.name) },
                                onClick = {
                                    modelStrategy = model.id
                                    settingsManager.transModelStrategy = model.id
                                    showModelStrategyMenu = false
                                }
                            )
                        }
                    }
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }

        Column {
            Text(stringResource(R.string.default_source_lang), style = MaterialTheme.typography.labelMedium)
            var showSourcePicker by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { showSourcePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(sourceLang)
            }
            if (showSourcePicker) {
                LanguagePicker(languages, onDismiss = { showSourcePicker = false }) {
                    sourceLang = it.first
                    settingsManager.transSourceLang = it.first
                    showSourcePicker = false
                }
            }
        }

        Column {
            Text(stringResource(R.string.default_target_lang), style = MaterialTheme.typography.labelMedium)
            var showTargetPicker by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { showTargetPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(targetLang)
            }
            if (showTargetPicker) {
                LanguagePicker(languages.filter { it.first != "自动检测" }, onDismiss = { showTargetPicker = false }) {
                    targetLang = it.first
                    settingsManager.transTargetLang = it.first
                    showTargetPicker = false
                }
            }
        }
    }
}

@Composable
fun ProjectMediaPermissionDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.perm_project_media_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.perm_project_media_desc))
                Text(stringResource(R.string.adb_command_title), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.adb_project_media_command, context.packageName), style = MaterialTheme.typography.bodySmall)
                Text("2. " + stringResource(R.string.shizuku_auth), fontWeight = FontWeight.Bold)
                Text("3. " + stringResource(R.string.root_auth), fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (Shizuku.pingBinder()) {
                        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                            AccessibilityUtils.grantPermissionWithShizuku(context, "PROJECT_MEDIA") { success, error ->
                                if (success) {
                                    Toast.makeText(context, context.getString(R.string.permission_granted_success), Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.permission_grant_failed, error), Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            Shizuku.requestPermission(1001)
                        }
                    } else {
                        Toast.makeText(context, context.getString(R.string.shizuku_not_running), Toast.LENGTH_SHORT).show()
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.shizuku_auth)) }
                
                Button(onClick = {
                    AccessibilityUtils.grantPermissionWithRoot(context, "PROJECT_MEDIA") { success, error ->
                        if (success) {
                            Toast.makeText(context, context.getString(R.string.permission_granted_success), Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } else {
                            Toast.makeText(context, context.getString(R.string.permission_grant_failed, error), Toast.LENGTH_LONG).show()
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.root_auth)) }
                
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

@Composable
fun PermissionDialog(
    onDismiss: () -> Unit,
    onPermissionGranted: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.perm_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.perm_dialog_desc))
                Text(stringResource(R.string.adb_command_title), fontWeight = FontWeight.Bold)
                Text("adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS", style = MaterialTheme.typography.bodySmall)
                Text("2. " + stringResource(R.string.shizuku_auth), fontWeight = FontWeight.Bold)
                Text("3. " + stringResource(R.string.root_auth), fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (Shizuku.pingBinder()) {
                        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                            AccessibilityUtils.grantPermissionWithShizuku(context) { success, error ->
                                if (success) {
                                    Toast.makeText(context, context.getString(R.string.permission_granted_success), Toast.LENGTH_SHORT).show()
                                    onPermissionGranted()
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.permission_grant_failed, error), Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            Shizuku.requestPermission(1001)
                        }
                    } else {
                        Toast.makeText(context, context.getString(R.string.shizuku_not_running), Toast.LENGTH_SHORT).show()
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.shizuku_auth)) }
                
                Button(onClick = {
                    AccessibilityUtils.grantPermissionWithRoot(context) { success, error ->
                        if (success) {
                            Toast.makeText(context, context.getString(R.string.permission_granted_success), Toast.LENGTH_SHORT).show()
                            onPermissionGranted()
                            onDismiss()
                        } else {
                            Toast.makeText(context, context.getString(R.string.permission_grant_failed, error), Toast.LENGTH_LONG).show()
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.root_auth)) }
                
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}
