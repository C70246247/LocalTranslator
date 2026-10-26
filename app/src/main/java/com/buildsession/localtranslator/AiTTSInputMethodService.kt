package com.buildsession.localtranslator

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.buildsession.localtranslator.model.ModelManager
import com.buildsession.localtranslator.ui.theme.localtranslatorTheme
import com.buildsession.localtranslator.util.SettingsManager
import com.buildsession.localtranslator.util.VoskHelper
import com.buildsession.localtranslator.util.VoskResultParser
import kotlinx.coroutines.*
import org.vosk.Model
import org.vosk.Recognizer
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

class AiTTSInputMethodService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private lateinit var settingsManager: SettingsManager
    private lateinit var modelManager: ModelManager
    
    private var voskModel: Model? = null
    private var voskRecognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var recognitionJob: Job? = null
    
    private val _isRecognizing = mutableStateOf(false)
    private val _tempDefaultModel = mutableStateOf<String?>(null)
    private val _showModelPicker = mutableStateOf(false)
    private val _downloadedModels = mutableStateListOf<String>()

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        modelManager = ModelManager(this)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        
        _tempDefaultModel.value = settingsManager.defaultModelName
        refreshDownloadedModels()
    }

    override fun onCreateInputView(): View {
        // Toast for debugging
        Toast.makeText(this, "AiTTS IME Created", Toast.LENGTH_SHORT).show()
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(this@AiTTSInputMethodService)
            setViewTreeSavedStateRegistryOwner(this@AiTTSInputMethodService)
            setViewTreeViewModelStoreOwner(this@AiTTSInputMethodService)
            setContent {
                localtranslatorTheme {
                    IMEKeyboardUI()
                }
            }
        }
        return composeView
    }

    override fun onConfigureWindow(win: android.view.Window?, isSoftInputMode: Boolean, isEmptyInput: Boolean) {
        super.onConfigureWindow(win, isSoftInputMode, isEmptyInput)
        win?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeViewModelStoreOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }
    }

    override fun onWindowShown() {
        super.onWindowShown()
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeViewModelStoreOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        if (_isRecognizing.value) {
            stopRecognition()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    private fun refreshDownloadedModels() {
        CoroutineScope(Dispatchers.IO).launch {
            val models = modelManager.getDownloadedModels()
            withContext(Dispatchers.Main) {
                _downloadedModels.clear()
                _downloadedModels.addAll(models)
            }
        }
    }

    @Composable
    fun IMEKeyboardUI() {
        val isRecognizing by _isRecognizing
        val showModelPicker by _showModelPicker
        val downloadedModels = _downloadedModels

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .heightIn(min = 200.dp)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                // Numbers Row
                KeyboardRow(keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"))
                
                // QWERTY Rows
                KeyboardRow(keys = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"))
                KeyboardRow(keys = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"))
                KeyboardRow(keys = listOf("Z", "X", "C", "V", "B", "N", "M", ",", ".", "?"))
                
                // Bottom Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    KeyButton(label = "DEL", weightValue = 1.5f, color = MaterialTheme.colorScheme.secondaryContainer) {
                        currentInputConnection?.deleteSurroundingText(1, 0)
                    }
                    
                    // Space bar as Voice Input Trigger
                    KeyButton(
                        label = if (isRecognizing) "Listening..." else "VOICE / SPACE",
                        weightValue = 3f,
                        color = if (isRecognizing) Color(0xFF61D4FF) else MaterialTheme.colorScheme.primaryContainer
                    ) {
                        if (isRecognizing) {
                            stopRecognition()
                            currentInputConnection?.commitText(" ", 1)
                        } else {
                            handleVoiceInput()
                        }
                    }
                    
                    KeyButton(label = "ENTER", weightValue = 1.5f, color = MaterialTheme.colorScheme.primary) {
                        currentInputConnection?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
                    }
                }
            }

            if (showModelPicker) {
                ModelPickerDialog(
                    models = downloadedModels,
                    onSelect = { model ->
                        _tempDefaultModel.value = model
                        _showModelPicker.value = false
                        startRecognition(model)
                    },
                    onDismiss = { _showModelPicker.value = false }
                )
            }
        }
    }

    @Composable
    fun KeyboardRow(keys: List<String>) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            keys.forEach { key ->
                key(key) {
                    KeyButton(label = key, weightValue = 1f) {
                        currentInputConnection?.commitText(key, 1)
                    }
                }
            }
        }
    }

    @Composable
    fun RowScope.KeyButton(label: String, weightValue: Float, color: Color = MaterialTheme.colorScheme.surfaceVariant, onClick: () -> Unit) {
        Surface(
            modifier = Modifier.weight(weightValue).height(48.dp).clickable { onClick() },
            shape = RoundedCornerShape(4.dp),
            color = color,
            tonalElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    @Composable
    fun ModelPickerDialog(models: List<String>, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
        // Since we are in IME, we can't show a standard Dialog easily that doesn't mess with window focus.
        // We'll use a Box overlay within the IME view itself.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.8f).padding(16.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("选择语音识别模型", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (models.isEmpty()) {
                        Text("未找到已下载的模型", color = Color.Red)
                    } else {
                        LazyVerticalGrid(columns = GridCells.Fixed(1), modifier = Modifier.heightIn(max = 200.dp)) {
                            items(models) { model ->
                                TextButton(
                                    onClick = { onSelect(model) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(model)
                                }
                            }
                        }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text("取消")
                    }
                }
            }
        }
    }

    private fun handleVoiceInput() {
        val modelToUse = _tempDefaultModel.value ?: settingsManager.defaultModelName
        if (modelToUse == null) {
            refreshDownloadedModels()
            _showModelPicker.value = true
        } else {
            startRecognition(modelToUse)
        }
    }

    private fun startRecognition(modelName: String) {
        if (_isRecognizing.value) return
        _isRecognizing.value = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (voskModel == null || _currentModelName != modelName) {
                    val modelPath = modelManager.getLocalModels().find { it.name == modelName }?.absolutePath 
                        ?: throw Exception("模型路径不存在: $modelName")
                    voskModel = Model(modelPath)
                    _currentModelName = modelName
                }

                voskRecognizer = Recognizer(voskModel, 16000.0f)
                
                val bufferSize = 1024
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.CHANNEL_CONFIGURATION_MONO,
                    bufferSize
                )

                audioRecord?.startRecording()
                
                recognitionJob = launch {
                    val buffer = ShortArray(bufferSize)
                    while (isActive && _isRecognizing.value) {
                        val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                        if (read > 0) {
                            if (voskRecognizer?.acceptWaveForm(buffer, read) == true) {
                                val resultStr = voskRecognizer?.result ?: ""
                                val finalText = VoskResultParser.parseResult(resultStr)
                                if (finalText.isNotEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        currentInputConnection?.commitText(finalText, 1)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AiTTSInputMethodService, "识别出错: ${e.message}", Toast.LENGTH_SHORT).show()
                    _isRecognizing.value = false
                }
            }
        }
    }

    private fun stopRecognition() {
        _isRecognizing.value = false
        recognitionJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    private var _currentModelName: String = ""
}
