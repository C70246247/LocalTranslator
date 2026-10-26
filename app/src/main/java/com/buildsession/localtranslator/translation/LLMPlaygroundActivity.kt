package com.buildsession.localtranslator.translation

import android.os.Bundle
import android.util.Log
import android.widget.Toast

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import com.buildsession.localtranslator.R
import com.buildsession.localtranslator.HedgehogLoading
import com.buildsession.localtranslator.ui.theme.localtranslatorTheme
import kotlinx.coroutines.launch
import org.nehuatl.llamacpp.LlamaHelper

private const val TAG = "LLMPlayground"

class LLMPlaygroundActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            localtranslatorTheme {
                PlaygroundScreen(onBack = { finish() })
            }
        }
    }
}

enum class MessageRole { USER, AI }
data class ChatMessage(val role: MessageRole, val content: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaygroundScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val translationManager = remember { TranslationManager(context) }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboard.current
    
    val loadedModelId by translationManager.loadedModelId.collectAsState()
    val sessionId by translationManager.sessionId.collectAsState()
    
    // UI States
    var systemPrompt by rememberSaveable { mutableStateOf("") }
    var userMessage by rememberSaveable { mutableStateOf("") }
    
    // 使用 rememberSaveable 处理 chatHistory，需要自定义 Saver
    val chatHistory = rememberSaveable(saver = listSaver(
        save = { list -> list.map { listOf(it.role.name, it.content) } },
        restore = { list -> 
            val newList = mutableStateListOf<ChatMessage>()
            list.forEach { item ->
                val role = MessageRole.valueOf(item[0])
                val content = item[1]
                newList.add(ChatMessage(role, content))
            }
            newList
        }
    )) { mutableStateListOf<ChatMessage>() }
    
    var aiResponse by rememberSaveable { mutableStateOf("") }
    var isGenerating by rememberSaveable { mutableStateOf(false) }
    
    // 监听全局忙碌状态，同步实验室状态
    val isBusy by translationManager.isBusy.collectAsState()
    LaunchedEffect(isBusy) {
        if (!isBusy) {
            isGenerating = false
        }
    }
    
    // LLM Parameters
    var temperature by rememberSaveable { mutableFloatStateOf(0.8f) }
    var topK by rememberSaveable { mutableFloatStateOf(40f) }
    var topP by rememberSaveable { mutableFloatStateOf(0.95f) }
    var repeatPenalty by rememberSaveable { mutableFloatStateOf(1.1f) }
    var showParams by rememberSaveable { mutableStateOf(false) }

    // 初始化提示词 & 处理模型更换或会话更新
    LaunchedEffect(sessionId) {
        systemPrompt = translationManager.getDefaultSystemPrompt(loadedModelId)
        chatHistory.clear()
        aiResponse = ""
        isGenerating = false
    }
    
    val listState = rememberLazyListState()
    var showDisclaimer by remember { mutableStateOf(true) }

    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = { /* 不允许点击外部关闭 */ },
            title = { Text(stringResource(R.string.trans_playground_disclaimer_title)) },
            text = { Text(stringResource(R.string.trans_playground_disclaimer_content)) },
            confirmButton = {
                Button(
                    onClick = { showDisclaimer = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.trans_playground_disclaimer_agree))
                }
            },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        )
    }

    // 监听 LLM 事件
    LaunchedEffect(Unit) {
        translationManager.llmFlow.collect { event ->
            when (event) {
                is LlamaHelper.LLMEvent.Started -> {
                    aiResponse = ""
                    isGenerating = true
                }
                is LlamaHelper.LLMEvent.Ongoing -> {
                    aiResponse += event.word
                }
                is LlamaHelper.LLMEvent.Done -> {
                    if (aiResponse.isNotEmpty()) {
                        chatHistory.add(ChatMessage(MessageRole.AI, aiResponse))
                        aiResponse = ""
                    }
                    isGenerating = false
                }
                is LlamaHelper.LLMEvent.Error -> {
                    isGenerating = false
                    Log.e(TAG, "LLM Error: ${event.message}")
                    Toast.makeText(context, context.getString(R.string.trans_error, event.message), Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }

    // 自动滚动到底部
    LaunchedEffect(chatHistory.size, aiResponse) {
        if (chatHistory.isNotEmpty() || aiResponse.isNotEmpty()) {
            listState.animateScrollToItem(if (aiResponse.isNotEmpty()) chatHistory.size else chatHistory.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(stringResource(R.string.trans_llm_playground_activity_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            loadedModelId ?: stringResource(R.string.trans_no_model_loaded), 
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        systemPrompt = translationManager.getDefaultSystemPrompt(loadedModelId)
                        userMessage = ""
                        aiResponse = ""
                        chatHistory.clear()
                        temperature = 0.8f
                        topK = 40f
                        topP = 0.95f
                        repeatPenalty = 1.1f
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.trans_llm_playground_reset))
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = userMessage,
                            onValueChange = { userMessage = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.trans_llm_playground_user_message_placeholder)) },
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                        
                        FloatingActionButton(
                            onClick = {
                                if (isGenerating) {
                                    translationManager.abort()
                                    isGenerating = false
                                } else if (userMessage.isNotBlank() && loadedModelId != null) {
                                    val currentMessage = userMessage
                                    chatHistory.add(ChatMessage(MessageRole.USER, currentMessage))
                                    userMessage = ""
                                    
                                    val prompt = formatPrompt(loadedModelId, systemPrompt, currentMessage)
                                    scope.launch {
                                        translationManager.rawPredict(
                                            prompt = prompt,
                                            temperature = temperature,
                                            topK = topK.toInt(),
                                            topP = topP,
                                            repeatPenalty = repeatPenalty
                                        )
                                    }
                                }
                            },
                            shape = CircleShape,
                            containerColor = if (isGenerating) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (isGenerating) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(
                                if (isGenerating) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                                contentDescription = if (isGenerating) stringResource(R.string.trans_llm_playground_stop_btn) else stringResource(R.string.trans_llm_playground_send_btn)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 系统提示词卡片 (作为列表的第一项)
            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.trans_llm_playground_system_prompt_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            IconButton(onClick = { showParams = !showParams }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Tune, null, modifier = Modifier.size(16.dp))
                            }
                        }
                        TextField(
                            value = systemPrompt,
                            onValueChange = { systemPrompt = it },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            placeholder = { Text(stringResource(R.string.trans_llm_playground_system_prompt_placeholder)) }
                        )
                        
                        AnimatedVisibility(visible = showParams) {
                            Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Temperature
                                ParameterSlider(
                                    "Temperature", 
                                    temperature, 
                                    0.1f..2.0f, 
                                    desc = stringResource(R.string.trans_llm_playground_temp_desc)
                                ) { temperature = it }
                                
                                // Top P
                                ParameterSlider(
                                    "Top P", 
                                    topP, 
                                    0.1f..1.0f, 
                                    desc = stringResource(R.string.trans_llm_playground_topp_desc)
                                ) { topP = it }

                                // Top K
                                ParameterSlider("Top K", topK, 1f..100f, steps = 99) { topK = it }

                                // Repeat Penalty
                                ParameterSlider(
                                    "Repeat Penalty", 
                                    repeatPenalty, 
                                    1.0f..2.0f, 
                                    desc = stringResource(R.string.trans_llm_playground_repeat_penalty_desc)
                                ) { repeatPenalty = it }
                            }
                        }
                    }
                }
            }

            // 聊天记录
            items(chatHistory) { message ->
                ChatBubble(message)
            }

            // 正在生成的 AI 回复
            if (aiResponse.isNotEmpty() || isGenerating) {
                item {
                    ChatBubble(ChatMessage(MessageRole.AI, aiResponse), isGenerating)
                }
            }
        }
    }
}

@Composable
fun ParameterSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, desc: String? = null, onValueChange: (Float) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$label: ${if (steps > 0) value.toInt() else "%.2f".format(value)}", style = MaterialTheme.typography.labelSmall)
            if (desc != null) {
                Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range, steps = steps, modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ChatBubble(message: ChatMessage, isGenerating: Boolean = false) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val isUser = message.role == MessageRole.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    val shape = if (isUser) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = shape,
            tonalElevation = 2.dp,
            modifier = Modifier.clickable {
                clipboardManager.setText(AnnotatedString(message.content))
                Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
            }
        ) {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                val inlineContentId = "loading_ball"
                val annotatedString = buildAnnotatedString {
                    append(message.content)
                    if (isGenerating && message.role == MessageRole.AI) {
                        append(" ")
                        appendInlineContent(inlineContentId, "[loading]")
                    }
                }

                val inlineContent = mapOf(
                    inlineContentId to InlineTextContent(
                        Placeholder(
                            width = 16.sp,
                            height = 16.sp,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                        )
                    ) {
                        HedgehogLoading(loadingSize = 14.dp, color = textColor)
                    }
                )

                SelectionContainer {
                    Text(
                        text = annotatedString,
                        inlineContent = inlineContent,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor
                    )
                }
            }
        }
    }
}

/**
 * 根据模型 ID 自动适配 Prompt 模板
 */
private fun formatPrompt(modelId: String?, system: String, user: String): String {
    return when {
        modelId?.contains("qwen") == true || modelId?.contains("sakura") == true -> {
            "<|im_start|>system\n$system<|im_end|>\n<|im_start|>user\n$user<|im_end|>\n<|im_start|>assistant\n"
        }
        modelId?.contains("hy-mt") == true -> {
            "<｜hy_begin▁of▁sentence｜>$system<｜hy_place▁holder▁no▁3｜><｜hy_User｜>$user<｜hy_Assistant｜>"
        }
        modelId?.contains("internlm") == true -> {
            "<|im_start|>system\n$system<|im_end|>\n<|im_start|>user\n$user<|im_end|>\n<|im_start|>assistant\n"
        }
        else -> {
            // 默认兜底模板
            "System: $system\nUser: $user\nAssistant:"
        }
    }
}

@Composable
fun SelectionContainer(content: @Composable () -> Unit) {
    androidx.compose.foundation.text.selection.SelectionContainer {
        content()
    }
}
