package com.buildsession.localtranslator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.work.WorkInfo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import com.buildsession.localtranslator.model.DownloadStatus
import com.buildsession.localtranslator.model.ModelItem
import com.buildsession.localtranslator.model.ModelManager
import com.buildsession.localtranslator.translation.TranslationManager
import com.buildsession.localtranslator.translation.GoogleTranslationManager
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.foundation.clickable
import com.buildsession.localtranslator.translation.TranslationModel
import com.buildsession.localtranslator.translation.LLMPlaygroundActivity
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.ChevronRight
import org.nehuatl.llamacpp.LlamaHelper
import com.buildsession.localtranslator.ui.theme.localtranslatorTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.util.Log
import com.buildsession.localtranslator.util.AccessibilityUtils
import com.buildsession.localtranslator.util.SettingsManager
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Settings
import android.content.ClipboardManager
import android.content.ClipData
import android.os.Handler
import android.os.Looper
import android.os.Build
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowDropDown

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.painterResource
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.shape.CircleShape
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import android.app.ActivityManager
import com.buildsession.localtranslator.translation.TranslationFloatingService
import com.buildsession.localtranslator.ocr.OcrManager
import com.buildsession.localtranslator.ocr.OcrResult
import java.io.BufferedReader
import java.io.InputStreamReader
import androidx.compose.ui.res.stringResource
import com.buildsession.localtranslator.R
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler

val TranslationLanguages = listOf(
    "自动检测" to "auto", "中文" to "zh", "英语" to "en", "日语" to "ja", "韩语" to "ko",
    "法语" to "fr", "德语" to "de", "俄语" to "ru", "西班牙语" to "es",
    "葡萄牙语" to "pt", "意大利语" to "it", "阿拉伯语" to "ar", "越南语" to "vi",
    "泰语" to "th", "印尼语" to "id", "马来语" to "ms", "土耳其语" to "tr",
    "印地语" to "hi", "波兰语" to "pl", "荷兰语" to "nl", "瑞典语" to "sv",
    "芬兰语" to "fi", "丹麦语" to "da", "挪威语" to "no", "希腊语" to "el",
    "捷克语" to "cs", "匈牙利语" to "hu", "罗马尼亚语" to "ro", "藏语" to "bo",
    "维吾尔语" to "ug", "蒙古语" to "mn", "哈萨克语" to "kk", "粤语" to "yue",
    "闽南语" to "nan"
)

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private lateinit var modelManager: ModelManager
    private lateinit var translationManager: TranslationManager
    private lateinit var googleTranslationManager: GoogleTranslationManager
    private lateinit var ocrManager: OcrManager
    
    // 使用 MutableState 追踪 Intent
    private var currentIntentState = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        currentIntentState.value = intent
        
        var keepSplashScreen = true
        splashScreen.setKeepOnScreenCondition { keepSplashScreen }
        Handler(Looper.getMainLooper()).postDelayed({
            keepSplashScreen = false
        }, 1500)

        modelManager = ModelManager(this)
        translationManager = TranslationManager(this)
        googleTranslationManager = GoogleTranslationManager(this)
        ocrManager = OcrManager(this)
        
        // Add Shizuku permission listener
        Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 1001 && grantResult == PackageManager.PERMISSION_GRANTED) {
                AccessibilityUtils.grantPermissionWithShizuku(this) { success, error ->
                    if (success) {
                        Toast.makeText(this, "权限授权成功！", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "授权失败: $error", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        
        enableEdgeToEdge()
        setContent {
            localtranslatorTheme {
                val context = LocalContext.current
                val settingsManager = remember { SettingsManager(context) }
                var showFirstLaunchDialog by rememberSaveable { mutableStateOf(settingsManager.isFirstLaunch) }
                
                val tabs = listOf(
                    stringResource(R.string.tab_asr),
                    stringResource(R.string.tab_translation),
                    stringResource(R.string.tab_ocr)
                )
                val pagerState = rememberPagerState(pageCount = { tabs.size })
                val coroutineScope = rememberCoroutineScope()
                val transVm: TranslationScreenViewModel = viewModel()
                
                // 监听 Intent 变化
                val currentIntent by currentIntentState

                // 处理跳转逻辑
                LaunchedEffect(currentIntent) {
                    val navigateTo = currentIntent?.getStringExtra("navigate_to")
                    if (navigateTo == "translation") {
                        val sourceText = currentIntent?.getStringExtra("source_text") ?: ""
                        val sourceLang = currentIntent?.getStringExtra("source_lang")
                        val targetLang = currentIntent?.getStringExtra("target_lang")
                        
                        if (sourceText.isNotEmpty()) {
                            transVm.sourceText = sourceText
                        }
                        if (sourceLang != null) {
                            transVm.sourceLang = sourceLang
                        }
                        if (targetLang != null) {
                            transVm.targetLang = targetLang
                        }
                        
                        pagerState.scrollToPage(1)
                        // 处理完后清除 Intent 数据，防止旋转屏幕等操作导致重复触发
                        currentIntentState.value = null
                    }
                }

                if (showFirstLaunchDialog) {
                    BackHandler(enabled = true) {
                        // 拦截返回键，不执行任何操作，防止用户绕过协议
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        Column {
                            CenterAlignedTopAppBar(
                                title = { Text(stringResource(R.string.app_name)) },
                                actions = {
                                    IconButton(onClick = { 
                                        context.startActivity(Intent(context, SettingsActivity::class.java))
                                    }) {
                                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                                    }
                                }
                            )
                            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                                tabs.forEachIndexed { index, title ->
                                    Tab(
                                        selected = pagerState.currentPage == index,
                                        onClick = { 
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(index)
                                            }
                                        },
                                        text = { Text(title) }
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            when (page) {
                                0 -> MainScreen(
                                    modelManager = modelManager,
                                    settingsManager = settingsManager
                                )
                                1 -> TranslationScreen(
                                    translationManager = translationManager,
                                    googleTranslationManager = googleTranslationManager,
                                    settingsManager = settingsManager,
                                    vm = transVm
                                )
                                2 -> OcrScreen(
                                    ocrManager = ocrManager,
                                    onTranslate = { text ->
                                        transVm.sourceText = text
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(1)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                if (showFirstLaunchDialog) {
                    FirstLaunchDialog(
                        onDismiss = {
                            settingsManager.isFirstLaunch = false
                            showFirstLaunchDialog = false
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntentState.value = intent
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            translationManager.release()
            ocrManager.release()
        }
    }
}

class TranslationScreenViewModel : ViewModel() {
    var sourceText by mutableStateOf("")
    var translatedText by mutableStateOf("")
    var isTranslating by mutableStateOf(false)
    var isLoadingModel by mutableStateOf(false)
    var showLoadModelDialog by mutableStateOf(false)
    var pendingReverseTranslate by mutableStateOf(false)
    var modelRefreshToken by mutableIntStateOf(0)
    var sourceLang by mutableStateOf("自动检测")
    var targetLang by mutableStateOf("英语")

    private var initialized = false

    fun initIfNeeded(defaultSourceLang: String, defaultTargetLang: String) {
        if (initialized) return
        sourceLang = defaultSourceLang
        targetLang = defaultTargetLang
        initialized = true
    }
}

@Composable
fun TranslationScreen(
    translationManager: TranslationManager, 
    googleTranslationManager: GoogleTranslationManager,
    settingsManager: SettingsManager,
    vm: TranslationScreenViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // vm already passed in

    val languages = TranslationLanguages

    vm.initIfNeeded(languages[0].first, languages[2].first)

    val loadedModelId by translationManager.loadedModelId.collectAsState()
    var translateWatchdogJob by remember { mutableStateOf<Job?>(null) }
    val listState = rememberLazyListState()

    val availableModels = remember { translationManager.availableModels }
    val loadedModel = remember(loadedModelId) { availableModels.firstOrNull { it.id == loadedModelId } }
    val downloadedModels = remember(vm.modelRefreshToken) {
        availableModels.filter { translationManager.isModelDownloaded(it.id) }
    }
    val isModelLoaded = remember(loadedModelId) { loadedModelId != null }
    var transEngine by remember { mutableStateOf(settingsManager.transEngine) }
    
    // 监听设置变化以同步引擎状态
    LaunchedEffect(Unit) {
        settingsManager.settingsChangedFlow.collect { key ->
            if (key == "trans_engine") {
                transEngine = settingsManager.transEngine
            }
        }
    }

    var isFloatingServiceRunning by rememberSaveable {
        mutableStateOf(false)
    }

    val canAutoLoadModel = remember { settingsManager.transModelStrategy != "ask" }
    var showAllModels by rememberSaveable { mutableStateOf(false) }

    // 页面每次可见时刷新一次状态，处理从后台回来或 Service 被系统回收的情况
    LaunchedEffect(Unit) {
        isFloatingServiceRunning = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == TranslationFloatingService::class.java.name }
    }
    
    val filteredModels = remember(showAllModels, vm.modelRefreshToken) {
        if (showAllModels) availableModels else availableModels.filter { it.isRecommended }
    }

    val googleDownloadedModels by googleTranslationManager.downloadedModels.collectAsState()
    val googleRecommendedModels = remember { googleTranslationManager.recommendedLanguages }
    val googleAllLanguages = remember { googleTranslationManager.getAvailableLanguages() }
    
    val filteredGoogleModels = remember(showAllModels, googleDownloadedModels) {
        if (showAllModels) googleAllLanguages 
        else googleRecommendedModels
    }

    // 监听服务停止广播以同步开关状态
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.buildsession.localtranslator.TRANSLATION_SERVICE_STOPPED") {
                    isFloatingServiceRunning = false
                }
            }
        }
        val filter = android.content.IntentFilter("com.buildsession.localtranslator.TRANSLATION_SERVICE_STOPPED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    fun startWatchdog() {
        translateWatchdogJob?.cancel()
        translateWatchdogJob = scope.launch {
            // 将超时时间延长至 60s，因为 1.5B+ 模型在低端设备上冷启动或处理长文本确实较慢
            delay(60000)
            if (vm.isTranslating) {
                // 超时必须显式调用中止，否则后台 native 线程会继续运行导致卡顿
                translationManager.abort()
                vm.isTranslating = false
                Toast.makeText(context, context.getString(R.string.trans_timeout), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun requestTranslate() {
        if (settingsManager.transEngine == "GOOGLE") {
            vm.translatedText = ""
            vm.isTranslating = true
            scope.launch {
                vm.translatedText = googleTranslationManager.translate(vm.sourceText, vm.sourceLang, vm.targetLang)
                vm.isTranslating = false
            }
            return
        }
        
        if (!isModelLoaded) {
            val strategy = settingsManager.transModelStrategy
            if (strategy != "ask") {
                val modelToLoad = availableModels.find { it.id == strategy }
                if (modelToLoad != null && translationManager.isModelDownloaded(modelToLoad.id)) {
                    vm.isLoadingModel = true
                    translationManager.loadModel(
                        modelToLoad.id, 
                        settingsManager.transContextLength,
                        settingsManager.transUseMmap
                    ) { loadId ->
                        vm.isLoadingModel = false
                        if (loadId >= 0) {
                            vm.translatedText = ""
                            vm.isTranslating = translationManager.translate(vm.sourceText, vm.targetLang, vm.sourceLang)
                            if (vm.isTranslating) startWatchdog()
                        } else {
                            Toast.makeText(context, "自动加载模型失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return
                }
            }
            Toast.makeText(context, context.getString(R.string.trans_no_model_loaded), Toast.LENGTH_SHORT).show()
            return
        }
        vm.translatedText = ""
        vm.isTranslating = translationManager.translate(vm.sourceText, vm.targetLang, vm.sourceLang)
        if (!vm.isTranslating) {
            Toast.makeText(context, "模型未就绪或输入为空", Toast.LENGTH_SHORT).show()
        } else {
            startWatchdog()
        }
    }

    LaunchedEffect(Unit) {
        translationManager.llmFlow.collect { event ->
            if (settingsManager.transEngine == "GOOGLE") return@collect
            
            when (event) {
                is LlamaHelper.LLMEvent.Started -> {
                    vm.isTranslating = true
                    vm.translatedText = ""
                }
                is LlamaHelper.LLMEvent.Ongoing -> {
                    vm.translatedText += event.word
                }
                is LlamaHelper.LLMEvent.Done -> {
                    vm.isTranslating = false
                    translateWatchdogJob?.cancel()
                    translateWatchdogJob = null
                }
                is LlamaHelper.LLMEvent.Error -> {
                    vm.isTranslating = false
                    translateWatchdogJob?.cancel()
                    translateWatchdogJob = null
                    Toast.makeText(context, context.getString(R.string.trans_error, event.message), Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item(key = "translation_header", contentType = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        if (settingsManager.transEngine == "GOOGLE") stringResource(R.string.trans_engine_google)
                        else stringResource(R.string.trans_offline_title), 
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        if (settingsManager.transEngine == "GOOGLE") stringResource(R.string.trans_engine_google_desc)
                        else if (showAllModels) stringResource(R.string.trans_show_all_models) 
                        else stringResource(R.string.trans_show_recommended_models),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item(key = "model_control", contentType = "control") {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (settingsManager.transEngine == "GOOGLE") {
                        Text(stringResource(R.string.status_ready), color = MaterialTheme.colorScheme.primary)
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(stringResource(R.string.trans_floating_window), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(R.string.trans_floating_window_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isFloatingServiceRunning,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (AndroidSettings.canDrawOverlays(context)) {
                                            context.startForegroundService(Intent(context, TranslationFloatingService::class.java))
                                            isFloatingServiceRunning = true
                                        } else {
                                            val intent = Intent(
                                                AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                            context.startActivity(intent)
                                        }
                                    } else {
                                        context.stopService(Intent(context, TranslationFloatingService::class.java))
                                        isFloatingServiceRunning = false
                                    }
                                }
                            )
                        }
                    } else {
                        Text(
                            loadedModel?.let { stringResource(R.string.trans_model_loaded, it.name) } ?: stringResource(R.string.trans_no_model_loaded),
                            color = if (isModelLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        AnimatedVisibility(
                            visible = isModelLoaded,
                            enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioLowBouncy)),
                            exit = shrinkVertically()
                        ) {
                            Column {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                Text(stringResource(R.string.trans_control_title), style = MaterialTheme.typography.titleMedium)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(stringResource(R.string.trans_floating_window), style = MaterialTheme.typography.bodyLarge)
                                        Text(stringResource(R.string.trans_floating_window_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = isFloatingServiceRunning,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                if (AndroidSettings.canDrawOverlays(context)) {
                                                    context.startForegroundService(Intent(context, TranslationFloatingService::class.java))
                                                    isFloatingServiceRunning = true
                                                } else {
                                                    val intent = Intent(
                                                        AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                        Uri.parse("package:${context.packageName}")
                                                    )
                                                    context.startActivity(intent)
                                                }
                                            } else {
                                                context.stopService(Intent(context, TranslationFloatingService::class.java))
                                                isFloatingServiceRunning = false
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = {
                                if (isModelLoaded) {
                                    vm.isTranslating = false
                                    translateWatchdogJob?.cancel()
                                    translateWatchdogJob = null
                                    translationManager.release()
                                } else {
                                    // Smart load: if a default model is set and downloaded, load it directly
                                    val strategy = settingsManager.transModelStrategy
                                    val defaultModel = availableModels.find { it.id == strategy }
                                    if (strategy != "ask" && defaultModel != null && translationManager.isModelDownloaded(defaultModel.id)) {
                                        vm.isLoadingModel = true
                                        translationManager.loadModel(
                                            defaultModel.id, 
                                            settingsManager.transContextLength,
                                            settingsManager.transUseMmap
                                        ) { loadId ->
                                            vm.isLoadingModel = false
                                            if (loadId >= 0) {
                                                Toast.makeText(context, context.getString(R.string.trans_auto_loaded_default), Toast.LENGTH_SHORT).show()
                                            } else {
                                                vm.showLoadModelDialog = true
                                            }
                                        }
                                    } else {
                                        vm.showLoadModelDialog = true
                                    }
                                }
                            },
                            enabled = (downloadedModels.isNotEmpty() || isModelLoaded) && !vm.isLoadingModel,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isModelLoaded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                disabledContainerColor = if (vm.isLoadingModel) {
                                    // 加载时让按钮看起来依然处于某种激活状态
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                },
                                disabledContentColor = if (vm.isLoadingModel) {
                                    // 强制加载动画和文字在禁用状态下依然保持高亮
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                }
                            )
                        ) {
                            if (vm.isLoadingModel) {
                                HedgehogLoading(loadingSize = 18.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.status_loading))
                            } else {
                                Text(if (isModelLoaded) stringResource(R.string.trans_unload_model) else stringResource(R.string.trans_load_model))
                            }
                        }
                    }
                }
            }
        }

        if (settingsManager.transEngine == "GOOGLE") {
            items(filteredGoogleModels, key = { it.second }, contentType = { "google_model_item" }) { (name, code) ->
                GoogleTranslationModelItem(
                    googleTranslationManager = googleTranslationManager,
                    langName = name,
                    langCode = code,
                    isDownloaded = googleDownloadedModels.contains(code),
                    onAnyModelStateChanged = { googleTranslationManager.refreshDownloadedModels() }
                )
            }

            item(key = "toggle_google_models", contentType = "action") {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = { showAllModels = !showAllModels }) {
                        Text(if (showAllModels) stringResource(R.string.trans_view_recommended_models) else stringResource(R.string.trans_view_all_models))
                    }
                }
            }
        }

        if (settingsManager.transEngine == "LLM") {
            items(filteredModels, key = { it.id }, contentType = { "model_item" }) { model ->
                TranslationModelItem(
                    translationManager = translationManager,
                    model = model,
                    isLoaded = loadedModelId == model.id,
                    onAnyModelStateChanged = { vm.modelRefreshToken++ },
                    onDeleteLoadedModel = {
                        vm.isTranslating = false
                        translateWatchdogJob?.cancel()
                        translateWatchdogJob = null
                        translationManager.release()
                    }
                )
            }

            item(key = "toggle_models", contentType = "action") {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = { showAllModels = !showAllModels }) {
                        Text(if (showAllModels) stringResource(R.string.trans_view_recommended_models) else stringResource(R.string.trans_view_all_models))
                    }
                }
            }
        }

        if (transEngine == "GOOGLE" || downloadedModels.isNotEmpty()) {
            item(key = "translation_editor", contentType = "editor") {
                TranslationInteractionCard(
                    vm = vm,
                    languages = languages,
                    isModelLoaded = isModelLoaded || transEngine == "GOOGLE",
                    canAutoLoadModel = canAutoLoadModel,
                    loadedModelId = loadedModelId,
                    availableModels = availableModels,
                    settingsManager = settingsManager,
                    googleTranslationManager = googleTranslationManager,
                    transEngine = transEngine,
                    onTranslateClick = {
                        if (vm.isTranslating) {
                            if (transEngine == "LLM") {
                                translationManager.abort()
                            }
                            vm.isTranslating = false
                            translateWatchdogJob?.cancel()
                            translateWatchdogJob = null
                        } else {
                            requestTranslate()
                        }
                    }
                )
            }
        }

        item(key = "llm_playground_entry") {
            val isModelLoaded = loadedModelId != null
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = isModelLoaded) {
                        context.startActivity(Intent(context, LLMPlaygroundActivity::class.java))
                    },
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (isModelLoaded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f) 
                                   else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Science, 
                        contentDescription = null,
                        tint = if (isModelLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.trans_llm_playground_title), 
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isModelLoaded) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.trans_experimental_tag),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                        Text(
                            if (isModelLoaded) stringResource(R.string.trans_llm_playground_desc_ready) else stringResource(R.string.trans_llm_playground_desc_locked),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isModelLoaded) {
                        Icon(
                            Icons.Default.ChevronRight, 
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        item(key = "footer") {
            AppFooter()
        }
    }

    if (vm.showLoadModelDialog) {
        val downloadedModelsOnly = availableModels.filter { translationManager.isModelDownloaded(it.id) }
        if (downloadedModelsOnly.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.trans_no_downloaded_models), Toast.LENGTH_SHORT).show()
            vm.showLoadModelDialog = false
        } else {
            TranslationModelLoadDialog(
                models = downloadedModelsOnly,
                loadedModelId = loadedModelId,
                isDownloaded = { id -> translationManager.isModelDownloaded(id) },
                onDismiss = { vm.showLoadModelDialog = false },
                onSelect = { selectedModel ->
                    vm.showLoadModelDialog = false
                    vm.isLoadingModel = true
                    translationManager.loadModel(
                        selectedModel.id, 
                        settingsManager.transContextLength,
                        settingsManager.transUseMmap
                    ) { loadId ->
                        vm.isLoadingModel = false
                        val success = loadId >= 0
                        if (success) {
                            Toast.makeText(context, context.getString(R.string.trans_model_loaded, selectedModel.name), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "模型加载失败，请优先选择 Q4_K_M 等官方兼容量化", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun OcrScreen(ocrManager: OcrManager, onTranslate: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isInitialized by ocrManager.isInitialized.collectAsState()
    val settingsManager = remember { SettingsManager(context) }
    
    var isInitializing by rememberSaveable { mutableStateOf(false) }
    var initStatus by rememberSaveable { mutableStateOf(if (isInitialized) context.getString(R.string.status_ready) else context.getString(R.string.status_not_initialized)) }
    
    // OCR 识别结果文本
    var ocrResultText by rememberSaveable { mutableStateOf("") }
    var isProcessingImage by rememberSaveable { mutableStateOf(false) }
    
    var ocrEngine by rememberSaveable { mutableStateOf(settingsManager.ocrEngine) }
    var googleOcrTestLang by rememberSaveable { mutableStateOf("zh") }
    
    // 当回到页面时，同步最新的引擎设置
    androidx.compose.runtime.DisposableEffect(androidx.lifecycle.compose.LocalLifecycleOwner.current) {
        ocrEngine = settingsManager.ocrEngine
        onDispose { }
    }

    // Media Picker Launcher
    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isProcessingImage = true
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    var bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    
                    if (bitmap != null) {
                        // 质量与性能隔离逻辑：仅对 Google 引擎应用动态缩放
                        if (settingsManager.ocrEngine == "GOOGLE") {
                            val maxTargetSize = settingsManager.ocrMaxSide
                            if (bitmap.width > maxTargetSize || bitmap.height > maxTargetSize) {
                                val scale = maxTargetSize.toFloat() / Math.max(bitmap.width, bitmap.height)
                                val newWidth = (bitmap.width * scale).toInt()
                                val newHeight = (bitmap.height * scale).toInt()
                                val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                                bitmap.recycle()
                                bitmap = scaledBitmap
                            }
                        }

                        val langCode = if (settingsManager.ocrEngine == "GOOGLE") {
                            googleOcrTestLang
                        } else {
                            TranslationLanguages.find { it.first == settingsManager.transSourceLang }?.second ?: "zh"
                        }
                        val results = ocrManager.recognize(bitmap, langCode)

                        withContext(Dispatchers.Main) {
                            ocrResultText = results.joinToString("\n") { it.text }
                            if (results.isEmpty()) {
                                Toast.makeText(context, context.getString(R.string.ocr_no_text_detected), Toast.LENGTH_SHORT).show()
                            }
                            isProcessingImage = false
                        }
                        bitmap.recycle()
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "解析图片失败", Toast.LENGTH_SHORT).show()
                            isProcessingImage = false
                        }
                    }
                } catch (e: Exception) {
                    Log.e("OcrScreen", "Process image failed", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "处理图片出错: ${e.message}", Toast.LENGTH_SHORT).show()
                        isProcessingImage = false
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (ocrEngine == "PADDLE") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.ocr_engine_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.ocr_engine_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    if (isInitialized) Color.Green else Color.Gray,
                                    CircleShape
                                )
                        )
                        Text(stringResource(R.string.ocr_status_label, initStatus))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                isInitializing = true
                                initStatus = context.getString(R.string.ocr_deploying)
                                scope.launch(Dispatchers.IO) {
                                    val success = ocrManager.initModelFromAssets()
                                    withContext(Dispatchers.Main) {
                                        isInitializing = false
                                        if (success) {
                                            initStatus = context.getString(R.string.status_ready)
                                            Toast.makeText(context, context.getString(R.string.ocr_init_success), Toast.LENGTH_SHORT).show()
                                        } else {
                                            initStatus = context.getString(R.string.ocr_init_failed)
                                            Toast.makeText(context, context.getString(R.string.ocr_init_failed), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isInitializing
                        ) {
                            if (isInitializing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.status_loading))
                            } else {
                                Text(if (isInitialized) stringResource(R.string.ocr_reinit) else stringResource(R.string.trans_load_model))
                            }
                        }
                        
                        if (isInitialized) {
                            OutlinedButton(
                                onClick = {
                                    ocrManager.release()
                                    initStatus = context.getString(R.string.status_not_initialized)
                                    Toast.makeText(context, context.getString(R.string.ocr_model_unloaded), Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(0.6f)
                            ) {
                                Text(stringResource(R.string.trans_unload_model))
                            }
                        }
                    }
                }
            }
        } else {
            // Google ML Kit 不需要手动初始化，显示简单的提示
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Google OCR 引擎", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "当前已启用 Google ML Kit 方案，支持中英日韩离线识别，识别速度快且无需手动部署模型。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (ocrEngine == "GOOGLE" || isInitialized) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.ocr_test_title), style = MaterialTheme.typography.titleMedium)
                    
                    if (ocrEngine == "GOOGLE") {
                        var showGoogleLangMenu by remember { mutableStateOf(false) }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("识别语言", style = MaterialTheme.typography.bodyMedium)
                            
                            Box {
                                TextButton(onClick = { showGoogleLangMenu = true }) {
                                    val langName = OcrManager.GoogleLanguages.find { it.second == googleOcrTestLang }?.first ?: "未知"
                                    Text(langName)
                                    Icon(Icons.Default.ArrowDropDown, null)
                                }
                                
                                DropdownMenu(
                                    expanded = showGoogleLangMenu,
                                    onDismissRequest = { showGoogleLangMenu = false }
                                ) {
                                    OcrManager.GoogleLanguages.forEach { (name, code) ->
                                        DropdownMenuItem(
                                            text = { Text(name) },
                                            onClick = {
                                                googleOcrTestLang = code
                                                showGoogleLangMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            pickMedia.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isProcessingImage
                    ) {
                        if (isProcessingImage) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.ocr_recognizing))
                        } else {
                            Icon(Icons.Default.Search, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.ocr_pick_image))
                        }
                    }

                    if (ocrResultText.isNotEmpty() || isProcessingImage) {
                        HorizontalDivider()
                        Text(stringResource(R.string.ocr_result_label), style = MaterialTheme.typography.labelMedium)
                        OutlinedTextField(
                            value = ocrResultText,
                            onValueChange = { ocrResultText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 150.dp, max = 300.dp),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            placeholder = { Text(stringResource(R.string.ocr_result_label)) }
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left-bottom: Translate
                            TextButton(
                                onClick = { onTranslate(ocrResultText) },
                                enabled = ocrResultText.isNotEmpty()
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.translationball),
                                    contentDescription = stringResource(R.string.tab_translation),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.ocr_send_to_translate))
                            }

                            // Right-bottom: Copy and Share
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("OCR 结果", ocrResultText)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                                }, enabled = ocrResultText.isNotEmpty()) {
                                    Icon(Icons.Default.ContentCopy, stringResource(R.string.copy))
                                }
                                IconButton(onClick = {
                                    val sendIntent: Intent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, ocrResultText)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, null)
                                    context.startActivity(shareIntent)
                                }, enabled = ocrResultText.isNotEmpty()) {
                                    Icon(Icons.Default.Share, stringResource(R.string.share))
                                }
                                IconButton(onClick = { ocrResultText = "" }) {
                                    Icon(Icons.Default.Delete, stringResource(R.string.clear))
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Text(
            stringResource(R.string.ocr_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        AppFooter()
    }
}

@Composable
private fun TranslationInteractionCard(
    vm: TranslationScreenViewModel,
    languages: List<Pair<String, String>>,
    isModelLoaded: Boolean,
    canAutoLoadModel: Boolean,
    loadedModelId: String?,
    availableModels: List<TranslationModel>,
    settingsManager: SettingsManager,
    googleTranslationManager: GoogleTranslationManager,
    transEngine: String,
    onTranslateClick: () -> Unit
) {
    val context = LocalContext.current
    var showSourceLangPicker by rememberSaveable { mutableStateOf(false) }
    var showTargetLangPicker by rememberSaveable { mutableStateOf(false) }

    val googleDownloadedModels by googleTranslationManager.downloadedModels.collectAsState()
    val isGoogleEngine = transEngine == "GOOGLE"

    // 过滤语言列表：如果是 Google 引擎，只显示已下载的语言，且不支持自动检测
    val filteredLanguages = remember(isGoogleEngine, googleDownloadedModels) {
        if (isGoogleEngine) {
            googleTranslationManager.getAvailableLanguages().filter { 
                googleDownloadedModels.contains(it.second) 
            }
        } else {
            languages
        }
    }

    // 确保当前选择的语言在过滤后的列表中
    LaunchedEffect(isGoogleEngine, filteredLanguages) {
        if (isGoogleEngine) {
            if (vm.sourceLang == "自动检测" || filteredLanguages.none { it.first == vm.sourceLang }) {
                vm.sourceLang = filteredLanguages.firstOrNull()?.first ?: "英语"
            }
            if (filteredLanguages.none { it.first == vm.targetLang }) {
                vm.targetLang = filteredLanguages.firstOrNull { it.first != vm.sourceLang }?.first ?: "中文"
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showSourceLangPicker = true }) {
                    Text(vm.sourceLang)
                }
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                TextButton(onClick = { showTargetLangPicker = true }) {
                    Text(vm.targetLang)
                }
                IconButton(onClick = {
                    if (vm.sourceLang == "自动检测") {
                        if (vm.targetLang == "中文") {
                            vm.sourceLang = "中文"
                            vm.targetLang = "英语"
                        } else {
                            vm.sourceLang = "英语"
                            vm.targetLang = "中文"
                        }
                    } else {
                        val temp = vm.sourceLang
                        vm.sourceLang = vm.targetLang
                        vm.targetLang = temp
                    }
                }) {
                    Icon(Icons.Default.SwapHoriz, "交换")
                }
            }

            OutlinedTextField(
                value = vm.sourceText,
                onValueChange = { vm.sourceText = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                placeholder = { Text("输入要翻译的文本...") },
                trailingIcon = {
                    if (vm.sourceText.isNotEmpty()) {
                        IconButton(onClick = { vm.sourceText = "" }) {
                            Icon(Icons.Default.Close, "清除")
                        }
                    }
                }
            )

            Button(
                onClick = onTranslateClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = (vm.sourceText.isNotEmpty() && (isModelLoaded || canAutoLoadModel)) || vm.isTranslating
            ) {
                Text(if (vm.isTranslating) "停止推理" else "立即翻译")
            }

            if (vm.translatedText.isNotEmpty() || vm.isTranslating) {
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("翻译结果：", style = MaterialTheme.typography.labelMedium)
                    
                    val inlineContentId = "loading_ball"
                    val annotatedString = buildAnnotatedString {
                        append(vm.translatedText)
                        if (vm.isTranslating) {
                            append(" ")
                            appendInlineContent(inlineContentId, "[loading]")
                        }
                    }

                    val inlineContent = mapOf(
                        inlineContentId to InlineTextContent(
                            Placeholder(
                                width = 18.sp,
                                height = 18.sp,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                            )
                        ) {
                            HedgehogLoading(loadingSize = 16.dp, color = MaterialTheme.colorScheme.primary)
                        }
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        shape = OutlinedTextFieldDefaults.shape,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        color = Color.Transparent
                    ) {
                        val scrollState = rememberScrollState()
                         var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                         var containerSize by remember { mutableStateOf(IntSize.Zero) }
                         
                         // 使用 TextFieldValue 管理光标和选择状态
                         var textFieldValue by remember { 
                             mutableStateOf(TextFieldValue(vm.translatedText)) 
                         }
                         
                         // 当外部 translatedText 变化时（如推理中），同步更新本地 state 并滚动到底部
                         LaunchedEffect(vm.translatedText) {
                             if (vm.translatedText != textFieldValue.text) {
                                 textFieldValue = textFieldValue.copy(text = vm.translatedText)
                                 if (vm.isTranslating) {
                                     delay(50) // 等待布局更新
                                     scrollState.animateScrollTo(scrollState.maxValue)
                                 }
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
                             
                             if (viewportHeight > 0) {
                                 if (cursorTop < scrollOffset) {
                                     scrollState.animateScrollTo(cursorTop.roundToInt())
                                 } else if (cursorBottom > scrollOffset + viewportHeight) {
                                     scrollState.animateScrollTo((cursorBottom - viewportHeight).roundToInt())
                                 }
                             }
                         }

                         Box(modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 300.dp).padding(16.dp)) {
                             // 使用与悬浮窗一致的 BasicTextField 滚动逻辑
                             androidx.compose.foundation.text.BasicTextField(
                                 value = textFieldValue,
                                 onValueChange = {
                                     textFieldValue = it
                                     if (it.text != vm.translatedText) {
                                         vm.translatedText = it.text
                                     }
                                 },
                                 modifier = Modifier
                                     .fillMaxWidth()
                                     .onGloballyPositioned { containerSize = it.size }
                                     .verticalScroll(scrollState),
                                 textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Transparent),
                                 cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                 onTextLayout = { textLayoutResult = it },
                                 decorationBox = { innerTextField ->
                                     Box {
                                         val inlineContentId = "loading_ball"
                                         val annotatedString = buildAnnotatedString {
                                             append(vm.translatedText)
                                             if (vm.isTranslating) {
                                                 append(" ")
                                                 appendInlineContent(inlineContentId, "[loading]")
                                             }
                                         }

                                         val inlineContent = mapOf(
                                             inlineContentId to InlineTextContent(
                                                 Placeholder(
                                                     width = 18.sp,
                                                     height = 18.sp,
                                                     placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                                                 )
                                             ) {
                                                 HedgehogLoading(loadingSize = 16.dp, color = MaterialTheme.colorScheme.primary)
                                             }
                                         )

                                         // 底层显示层：支持 InlineContent
                                         Text(
                                             text = annotatedString,
                                             inlineContent = inlineContent,
                                             style = MaterialTheme.typography.bodyLarge,
                                             color = MaterialTheme.colorScheme.onSurface,
                                             modifier = Modifier.fillMaxWidth()
                                         )

                                         // 实际的编辑层
                                         innerTextField()
                                     }
                                 }
                             )
                         }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("翻译结果", vm.translatedText)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, "复制")
                        }
                        IconButton(onClick = {
                            val sendIntent: Intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, vm.translatedText)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, null))
                        }) {
                            Icon(Icons.Default.Share, "分享")
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            if (vm.sourceLang == "自动检测") {
                                vm.pendingReverseTranslate = true
                                Toast.makeText(context, "自动检测模式无法反向，请先选择原语言", Toast.LENGTH_SHORT).show()
                                showSourceLangPicker = true
                            } else {
                                val tempLang = vm.sourceLang
                                vm.sourceLang = vm.targetLang
                                vm.targetLang = tempLang
                                vm.sourceText = vm.translatedText
                                onTranslateClick()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.CompareArrows, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("反向翻译")
                        }
                    }
                }
            }
        }
    }

    if (showSourceLangPicker) {
        LanguagePicker(
            languages = filteredLanguages,
            loadedModelId = loadedModelId,
            availableModels = availableModels,
            onDismiss = {
                showSourceLangPicker = false
                vm.pendingReverseTranslate = false
            }
        ) { selected: Pair<String, String> ->
            if (selected.first == vm.targetLang && selected.first != "自动检测") {
                Toast.makeText(context, "原语言和目标语言不能相同", Toast.LENGTH_SHORT).show()
            } else {
                vm.sourceLang = selected.first
                if (vm.pendingReverseTranslate && selected.first != "自动检测") {
                    vm.pendingReverseTranslate = false
                    val tempLang = vm.sourceLang
                    vm.sourceLang = vm.targetLang
                    vm.targetLang = tempLang
                    vm.sourceText = vm.translatedText
                    onTranslateClick()
                }
            }
            showSourceLangPicker = false
        }
    }

    if (showTargetLangPicker) {
        LanguagePicker(
            languages = filteredLanguages,
            loadedModelId = loadedModelId,
            availableModels = availableModels,
            onDismiss = { showTargetLangPicker = false }
        ) { selected: Pair<String, String> ->
            if (selected.first == vm.sourceLang) {
                Toast.makeText(context, "目标语言和原语言不能相同", Toast.LENGTH_SHORT).show()
            } else {
                vm.targetLang = selected.first
            }
            showTargetLangPicker = false
        }
    }
}

@Composable
fun FirstLaunchDialog(onDismiss: () -> Unit) {
    var inputText by rememberSaveable { mutableStateOf("") }
    val agreeText = stringResource(R.string.first_launch_agree_text)
    val isAgreed = inputText.trim() == agreeText

    AlertDialog(
        onDismissRequest = { /* 不允许点击外部消失 */ },
        title = { Text(stringResource(R.string.first_launch_title), fontWeight = FontWeight.Bold) },
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
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text(
                    text = stringResource(R.string.first_launch_agree_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(agreeText, style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isAgreed) Color.Green else MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = if (isAgreed) Color.Green else MaterialTheme.colorScheme.outline
                    )
                )

                AppFooter()
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                enabled = isAgreed,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.first_launch_start))
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    )
}

@Composable
fun LanguagePicker(
    languages: List<Pair<String, String>>,
    loadedModelId: String? = null,
    availableModels: List<TranslationModel> = emptyList(),
    onDismiss: () -> Unit,
    onSelect: (Pair<String, String>) -> Unit
) {
    val loadedModel = availableModels.find { it.id == loadedModelId }
    val supportedLangs = loadedModel?.supportedLanguages

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择语言") },
        text = {
            LazyColumn {
                items(languages) { lang ->
                    val isSupported = supportedLangs == null || lang.first == "自动检测" || supportedLangs.contains(lang.first)
                    
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = { onSelect(lang) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = lang.first,
                                modifier = Modifier.fillMaxWidth(),
                                color = if (isSupported) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                            )
                        }
                        if (!isSupported) {
                            Text(
                                text = stringResource(R.string.language_not_supported_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun TranslationModelLoadDialog(
    models: List<TranslationModel>,
    loadedModelId: String?,
    isDownloaded: (String) -> Boolean,
    onDismiss: () -> Unit,
    onSelect: (TranslationModel) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择要加载的模型") },
        text = {
            LazyColumn {
                items(models, key = { it.id }) { model ->
                    val downloaded = isDownloaded(model.id)
                    val loaded = loadedModelId == model.id
                    TextButton(
                        onClick = { if (downloaded) onSelect(model) },
                        enabled = downloaded,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val status = when {
                            loaded -> "（已加载）"
                            downloaded -> "（已下载）"
                            else -> "（未下载）"
                        }
                        Text("${model.name} $status", modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun GoogleTranslationModelItem(
    googleTranslationManager: GoogleTranslationManager,
    langName: String,
    langCode: String,
    isDownloaded: Boolean,
    onAnyModelStateChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isDeleting by remember { mutableStateOf(false) }
    val downloadingModels by googleTranslationManager.downloadingModels.collectAsState()
    val isDownloading = downloadingModels.contains(langCode)

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(langName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Text("($langCode)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (isDownloaded) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                    Text("已下载 (约 30MB)", color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            isDeleting = true
                            scope.launch {
                                googleTranslationManager.deleteModel(langCode)
                                isDeleting = false
                                onAnyModelStateChanged()
                            }
                        },
                        enabled = !isDeleting
                    ) {
                        if (isDeleting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else {
                Button(
                    onClick = {
                        scope.launch {
                            googleTranslationManager.downloadModel(langCode)
                            onAnyModelStateChanged()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isDownloading
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("正在下载...")
                    } else {
                        Icon(Icons.Default.Download, null)
                        Spacer(Modifier.width(8.dp))
                        Text("下载离线包 (约 30MB)")
                    }
                }
            }
        }
    }
}

@Composable
fun TranslationModelItem(
    translationManager: TranslationManager,
    model: TranslationModel,
    isLoaded: Boolean,
    onAnyModelStateChanged: () -> Unit,
    onDeleteLoadedModel: () -> Unit
) {
    val progressFlow = remember(translationManager, model.id) { translationManager.getDownloadProgress(model.id) }
    val downloadProgressInfo by progressFlow.collectAsState(initial = null)
    var isDownloaded by remember(model.id) { mutableStateOf(translationManager.isModelDownloaded(model.id)) }
    val (workState, progress, speed) = downloadProgressInfo ?: Triple(null, 0, 0L)

    val speedText = remember(speed) {
        when {
            speed >= 1024 * 1024 -> "%.2f MB/s".format(speed / (1024f * 1024f))
            speed >= 1024 -> "%.2f KB/s".format(speed / 1024f)
            else -> "$speed B/s"
        }
    }

    LaunchedEffect(workState) {
        if (workState == WorkInfo.State.SUCCEEDED) {
            isDownloaded = translationManager.isModelDownloaded(model.id)
            onAnyModelStateChanged()
        }
    }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(model.name, style = MaterialTheme.typography.titleMedium)
            Text(model.description, style = MaterialTheme.typography.bodySmall)

            if (isDownloaded) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        if (isLoaded) "模型已加载 (${model.sizeText})" else "模型已下载 (${model.sizeText})",
                        color = if (isLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        if (translationManager.deleteModel(model.id)) {
                            isDownloaded = false
                            onAnyModelStateChanged()
                            if (isLoaded) onDeleteLoadedModel()
                        }
                    }) {
                        Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                val isDownloading = workState == WorkInfo.State.RUNNING || workState == WorkInfo.State.ENQUEUED
                if (isDownloading) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DownloadInfoRow(
                        progress = progress,
                        speedText = speedText,
                        onCancel = { translationManager.cancelDownload(model.id) }
                    )
                } else {
                    Button(
                        onClick = { translationManager.enqueueDownload(model) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, null)
                        Spacer(Modifier.width(8.dp))
                        Text("下载离线模型 (${model.sizeText})")
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadInfoRow(
    progress: Int,
    speedText: String,
    onCancel: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val text = if (progress >= 0) "正在下载: $progress% ($speedText)" else "正在下载... ($speedText)"
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        if (onCancel != null) {
            TextButton(onClick = onCancel) {
                Text("取消")
            }
        }
    }
}

@Composable
fun HedgehogLoading(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    loadingSize: Dp = 24.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Hedgehog")
    
    // 边数平滑过渡：3 -> 8 -> 3，1秒一个完整周期（500ms增，500ms减）
    val edgeCount by infiniteTransition.animateFloat(
        initialValue = 3f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EdgeCount"
    )

    // 旋转：1秒一圈
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    // 波动幅度：1秒一个完整缩放周期
    val amplitude by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = SineWayEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Amplitude"
    )

    Box(
        modifier = modifier
            .size(loadingSize)
            .drawBehind {
                val drawSize = this.size
                val center = Offset(drawSize.width / 2f, drawSize.height / 2f)
                val baseRadius = drawSize.minDimension / 2.5f
                
                val totalPoints = 180
                val path = Path()
                
                for (i in 0 until totalPoints) {
                    val angle = (i.toFloat() / totalPoints) * 2 * Math.PI
                    
                    // 为了消除 2pi 处的跳变，平滑插值两个相邻整数边数的状态
                    val floorCount = kotlin.math.floor(edgeCount).toInt()
                    val ceilCount = kotlin.math.ceil(edgeCount).toInt()
                    val fraction = edgeCount - floorCount
                    
                    val r1 = 1f + amplitude * cos(angle * floorCount).toFloat()
                    val r2 = 1f + amplitude * cos(angle * ceilCount).toFloat()
                    val rFactor = r1 + (r2 - r1) * fraction
                    
                    val r = baseRadius * rFactor
                    val finalAngle = angle + Math.toRadians(rotation.toDouble())
                    
                    val x = center.x + r * cos(finalAngle).toFloat()
                    val y = center.y + r * sin(finalAngle).toFloat()
                    
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                
                drawPath(
                    path = path,
                    color = color
                )
                
                // 添加柔和的外阴影/发光感
                drawPath(
                    path = path,
                    color = color.copy(alpha = 0.2f),
                    style = Stroke(width = 4.dp.toPx())
                )
            }
    )
}

// 辅助 Easing
private val SineWayEasing = Easing { fraction ->
    kotlin.math.sin(fraction * Math.PI).toFloat()
}

@Composable
fun GeometricFlipLoading(
    modifier: Modifier = Modifier,
    color: Color = if (androidx.compose.foundation.isSystemInDarkTheme()) 
        MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
    size: Dp = 24.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "GeometricFlip")
    
    val rotationX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 180f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0f at 0
                0f at 600
                180f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "RotationX"
    )

    val rotationY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 180f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0f at 0
                180f at 600
                180f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "RotationY"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                this.rotationX = rotationX
                this.rotationY = rotationY
                cameraDistance = 12f * density
            }
            .background(color)
    )
}

@Composable
fun ModelSelectionDialog(
    modelManager: ModelManager,
    onModelSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val downloadedModels = remember { modelManager.getDownloadedModels() }
    val filteredModels = downloadedModels.filter { it.contains(searchQuery, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择模型") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索已下载模型...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
                
                if (filteredModels.isEmpty()) {
                    Text(
                        text = if (downloadedModels.isEmpty()) "没有已下载的模型，请先下载。" else "未找到匹配的模型。",
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(filteredModels) { modelName ->
                            TextButton(
                                onClick = { onModelSelected(modelName) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(modelName, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun MainScreen(
    modelManager: ModelManager,
    settingsManager: SettingsManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var hasOverlayPermission by rememberSaveable { mutableStateOf(AndroidSettings.canDrawOverlays(context)) }
    var isAudioPermissionGranted by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isNotificationPermissionGranted by rememberSaveable {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isAudioPermissionGranted = isGranted
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isNotificationPermissionGranted = isGranted
    }

    val models = remember { mutableStateListOf<ModelItem>() }
    val listState = rememberLazyListState()
    var isLoading by rememberSaveable { mutableStateOf(true) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showAllModels by rememberSaveable { mutableStateOf(false) }
    var showModelSelectionDialog by rememberSaveable { mutableStateOf(false) }

    if (showModelSelectionDialog) {
        ModelSelectionDialog(
            modelManager = modelManager,
            onModelSelected = { modelName ->
                showModelSelectionDialog = false
                val intent = Intent(context, FloatingWindowService::class.java).apply {
                    putExtra("model_name", modelName)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            },
            onDismiss = { showModelSelectionDialog = false }
        )
    }

    // Fetch model list
    LaunchedEffect(showAllModels) {
        try {
            isLoading = true
            errorMessage = null
            val voskModels = modelManager.fetchModelList(onlyRecommended = !showAllModels)
            models.clear()
            models.addAll(voskModels.map { 
                ModelItem(
                    model = it,
                    status = if (modelManager.isModelDownloaded(it.name)) DownloadStatus.DOWNLOADED 
                            else DownloadStatus.NOT_DOWNLOADED
                )
            })
            if (models.isEmpty()) {
                errorMessage = if (showAllModels) "列表为空" else "未找到符合条件的推荐模型"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            errorMessage = "加载失败: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item(key = "permission_section", contentType = "permission") {
            PermissionSection(
                hasOverlayPermission = hasOverlayPermission,
                hasAudioPermission = isAudioPermissionGranted,
                hasNotificationPermission = isNotificationPermissionGranted,
                onRefreshOverlay = { hasOverlayPermission = AndroidSettings.canDrawOverlays(context) },
                onRequestAudio = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                onRequestNotification = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
        }

        if (isLoading) {
            item(key = "loading_state", contentType = "state") {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    HedgehogLoading(loadingSize = 32.dp)
                }
            }
        } else if (errorMessage != null) {
            item(key = "error_state", contentType = "state") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        isLoading = true
                                        errorMessage = null
                                        val voskModels = modelManager.fetchModelList()
                                        models.clear()
                                        models.addAll(voskModels.map { 
                                            ModelItem(
                                                model = it,
                                                status = if (modelManager.isModelDownloaded(it.name)) DownloadStatus.DOWNLOADED 
                                                        else DownloadStatus.NOT_DOWNLOADED
                                            )
                                        })
                                        if (models.isEmpty()) {
                                            errorMessage = "未找到符合条件的模型（已过滤 small 且非 obsolete 的中文模型）"
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = "加载失败: ${e.message}"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("重试")
                        }
                    }
                }
            }
        } else {
            item(key = "model_header", contentType = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showAllModels) "所有模型" else "推荐模型",
                        style = MaterialTheme.typography.titleLarge
                    )
                    TextButton(onClick = { modelManager.cancelAllDownloads() }) {
                        Text("全部取消")
                    }
                }
            }

            items(models, key = { it.model.name }, contentType = { "model_item" }) { model ->
                ModelListItem(
                    modelManager = modelManager, 
                    model = model, 
                    onDownload = { modelManager.enqueueDownload(model.model) }
                )
            }
            
            item(key = "toggle_models", contentType = "action") {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = { showAllModels = !showAllModels }) {
                        Text(if (showAllModels) "显示推荐模型" else "查看全部模型")
                    }
                }
            }
        }

        item(key = "service_control", contentType = "control") {
            ServiceControlSection(
                hasPermission = hasOverlayPermission,
                onStartService = {
                    val defaultModel = settingsManager.defaultModelName
                    if (defaultModel != null && modelManager.isModelDownloaded(defaultModel)) {
                        val intent = Intent(context, FloatingWindowService::class.java).apply {
                            putExtra("model_name", defaultModel)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                    } else {
                        showModelSelectionDialog = true
                    }
                },
                onStopService = {
                    val intent = Intent(context, FloatingWindowService::class.java)
                    context.stopService(intent)
                }
            )
        }

        item(key = "footer") {
            AppFooter()
        }
    }
}

@Composable
fun PermissionSection(
    hasOverlayPermission: Boolean,
    hasAudioPermission: Boolean,
    hasNotificationPermission: Boolean,
    onRefreshOverlay: () -> Unit,
    onRequestAudio: () -> Unit,
    onRequestNotification: () -> Unit
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "权限管理", style = MaterialTheme.typography.titleLarge)
        
        // Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (hasNotificationPermission) MaterialTheme.colorScheme.secondaryContainer 
                                    else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "通知权限", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (hasNotificationPermission) "已授权" else "需要授权以显示后台服务通知",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (!hasNotificationPermission) {
                        Button(onClick = onRequestNotification) {
                            Text("去授权")
                        }
                    } else {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "已授权")
                    }
                }
            }
        }

        // Overlay Permission
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (hasOverlayPermission) MaterialTheme.colorScheme.secondaryContainer 
                                else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "悬浮窗权限", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (hasOverlayPermission) "已授权" else "需要授权以显示悬浮窗",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (!hasOverlayPermission) {
                    Button(onClick = {
                        val intent = Intent(
                            AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }) {
                        Text("去授权")
                    }
                } else {
                    TextButton(onClick = onRefreshOverlay) {
                        Text("刷新状态")
                    }
                }
            }
        }

        // Audio Permission
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (hasAudioPermission) MaterialTheme.colorScheme.secondaryContainer 
                                else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "麦克风权限", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (hasAudioPermission) "已授权" else "需要授权以进行语音识别",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (!hasAudioPermission) {
                    Button(onClick = onRequestAudio) {
                        Text("去授权")
                    }
                } else {
                    Icon(imageVector = Icons.Default.Check, contentDescription = "已授权")
                }
            }
        }
    }
}

@Composable
fun ModelListItem(modelManager: ModelManager, model: ModelItem, onDownload: () -> Unit) {
    val progressFlow = remember(modelManager, model.model.name) {
        modelManager.getDownloadProgress(model.model.name)
    }
    val downloadProgressInfo by progressFlow.collectAsState(initial = null)
    var isDownloaded by remember { mutableStateOf(modelManager.isModelDownloaded(model.model.name)) }
    
    val (workState, progress, speed) = downloadProgressInfo ?: Triple(null, 0, 0L)

    val speedText = remember(speed) {
        when {
            speed >= 1024 * 1024 -> "%.2f MB/s".format(speed / (1024f * 1024f))
            speed >= 1024 -> "%.2f KB/s".format(speed / 1024f)
            else -> "$speed B/s"
        }
    }

    LaunchedEffect(workState) {
        if (workState == WorkInfo.State.SUCCEEDED) {
            isDownloaded = modelManager.isModelDownloaded(model.model.name)
        }
    }

    val currentStatus = remember(isDownloaded, workState, model.status) {
        when {
            isDownloaded -> DownloadStatus.DOWNLOADED
            workState == WorkInfo.State.RUNNING -> DownloadStatus.DOWNLOADING
            workState == WorkInfo.State.ENQUEUED -> DownloadStatus.WAITING
            workState == WorkInfo.State.CANCELLED -> DownloadStatus.PAUSED
            workState == WorkInfo.State.FAILED -> DownloadStatus.ERROR
            else -> model.status
        }
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.model.name, 
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (currentStatus == DownloadStatus.DOWNLOADED) "已下载 (${model.model.size_text})" 
                           else model.model.size_text, 
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (currentStatus == DownloadStatus.DOWNLOADING || currentStatus == DownloadStatus.PAUSED || currentStatus == DownloadStatus.WAITING) {
                    val progressValue = remember(progress) { if (currentStatus == DownloadStatus.WAITING) 0f else progress / 100f }
                    LinearProgressIndicator(
                        progress = { progressValue },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                    DownloadInfoRow(
                        progress = progress,
                        speedText = speedText,
                        onCancel = null // 这里的 UI 逻辑中取消按钮在右侧 IconButton
                    )
                }
            }
            
            Row {
                if (currentStatus == DownloadStatus.DOWNLOADING || currentStatus == DownloadStatus.WAITING) {
                    IconButton(onClick = { modelManager.cancelDownload(model.model.name) }) {
                        Icon(Icons.Default.Pause, contentDescription = "暂停")
                    }
                } else if (currentStatus == DownloadStatus.DOWNLOADED || currentStatus == DownloadStatus.PAUSED || currentStatus == DownloadStatus.ERROR) {
                    IconButton(onClick = {
                        if (modelManager.deleteModel(model.model.name)) {
                            isDownloaded = false
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                    }
                    if (currentStatus == DownloadStatus.DOWNLOADED) {
                        Icon(
                            imageVector = Icons.Default.Check, 
                            contentDescription = "已完成",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(12.dp)
                        )
                    } else {
                        IconButton(onClick = onDownload) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "重试"
                            )
                        }
                    }
                } else {
                    IconButton(onClick = onDownload) {
                        Icon(
                            imageVector = if (currentStatus == DownloadStatus.PAUSED || currentStatus == DownloadStatus.ERROR) 
                                           Icons.Default.Refresh else Icons.Default.Download,
                            contentDescription = "下载"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ServiceControlSection(
    hasPermission: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    val context = LocalContext.current
    var isRunning by remember { 
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        isRunning = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == FloatingWindowService::class.java.name }
    }

    // 监听语音服务停止广播
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.buildsession.localtranslator.VOICE_SERVICE_STOPPED") {
                    isRunning = false
                }
            }
        }
        val filter = android.content.IntentFilter("com.buildsession.localtranslator.VOICE_SERVICE_STOPPED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "服务控制",
            style = MaterialTheme.typography.titleLarge
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { 
                    isRunning = true
                    onStartService() 
                },
                modifier = Modifier.weight(1f),
                enabled = hasPermission && !isRunning,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("启动悬浮窗")
            }
            
            OutlinedButton(
                onClick = { 
                    isRunning = false
                    onStopService() 
                },
                modifier = Modifier.weight(1f),
                enabled = isRunning
            ) {
                Icon(Icons.Default.Stop, null)
                Spacer(Modifier.width(8.dp))
                Text("停止服务")
            }
        }
    }
}

@Composable
fun AppFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.fox),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth(0.7f),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Made by BuildSession",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
