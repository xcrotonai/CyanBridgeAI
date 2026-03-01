package com.cyanbridge.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyanbridge.ai.AiProvider
import com.cyanbridge.ai.AiRouter
import com.cyanbridge.glasses.BleManager
import com.cyanbridge.glasses.GlassesConnectionState
import com.cyanbridge.glasses.GlassesDevice
import com.cyanbridge.glasses.GlassesEventListener
import com.cyanbridge.glasses.GlassesStatus
import com.cyanbridge.translation.Languages
import com.cyanbridge.translation.LiveTranslator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val providerName: String = "",
    val attachedPhoto: Bitmap? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val bleManager = BleManager(application)
    val aiRouter = AiRouter()
    val translator = LiveTranslator(application)
    val prefs = SecurePrefs(application)

    // ── UI state ──────────────────────────────────────────────
    val connectionState: StateFlow<GlassesConnectionState> = bleManager.connectionState
    val foundDevices: StateFlow<List<GlassesDevice>> = bleManager.foundDevices
    val glassesStatus: StateFlow<GlassesStatus> = bleManager.status

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(listOf(
        ChatMessage("Hi! I'm connected to your HeyCyan glasses. Take a photo, ask me to describe what you see, or start translating!", false, "System")
    ))
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking

    private val _lastTranslation = MutableStateFlow<String?>(null)
    val lastTranslation: StateFlow<String?> = _lastTranslation

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage

    private val _isRecordingVideo = MutableStateFlow(false)
    val isRecordingVideo: StateFlow<Boolean> = _isRecordingVideo

    private val _isRecordingAudio = MutableStateFlow(false)
    val isRecordingAudio: StateFlow<Boolean> = _isRecordingAudio

    init {
        loadSavedKeys()
        setupGlassesEvents()
    }

    private fun loadSavedKeys() {
        aiRouter.geminiKey = prefs.geminiKey
        aiRouter.openaiKey = prefs.openaiKey
        aiRouter.claudeKey = prefs.claudeKey
        aiRouter.activeProvider = try {
            AiProvider.valueOf(prefs.activeAiProvider)
        } catch (e: Exception) { AiProvider.GEMINI }

        translator.geminiKey = prefs.geminiKey
        translator.googleCloudKey = prefs.googleTranslateKey
        translator.targetLang = prefs.translationTargetLang
    }

    private fun setupGlassesEvents() {
        bleManager.eventListener = object : GlassesEventListener {
            override fun onPhotoCaptured(bitmap: Bitmap) {
                // Auto-send to AI with default question
                askAiWithPhoto("What do you see in this photo? Describe it briefly.", bitmap)
            }
            override fun onAiImageReceived(bitmap: Bitmap) {
                addMessage(ChatMessage("✨ AI image received from glasses!", false, "System"))
            }
            override fun onStatusUpdated(status: GlassesStatus) { /* handled via StateFlow */ }
            override fun onError(message: String) {
                _toastMessage.value = message
            }
        }
    }

    // ── AI Chat ───────────────────────────────────────────────
    fun sendMessage(text: String) {
        if (text.isBlank()) return
        addMessage(ChatMessage(text, true))
        viewModelScope.launch {
            _isAiThinking.value = true
            val response = aiRouter.ask(text)
            _isAiThinking.value = false
            addMessage(ChatMessage(response.text, false, response.provider.displayName))
        }
    }

    fun askAiWithPhoto(question: String, photo: Bitmap) {
        addMessage(ChatMessage(question, true, attachedPhoto = photo))
        viewModelScope.launch {
            _isAiThinking.value = true
            val response = aiRouter.askWithPhoto(question, photo)
            _isAiThinking.value = false
            addMessage(ChatMessage(response.text, false, response.provider.displayName))
        }
    }

    private fun addMessage(msg: ChatMessage) {
        val current = _chatMessages.value.toMutableList()
        current.add(msg)
        _chatMessages.value = current
    }

    // ── Camera controls ───────────────────────────────────────
    fun takePhoto() {
        if (!bleManager.isConnected()) { _toastMessage.value = "Connect to glasses first"; return }
        bleManager.takePhoto()
        addMessage(ChatMessage("📷 Taking photo...", true))
    }

    fun toggleVideo() {
        if (!bleManager.isConnected()) { _toastMessage.value = "Connect to glasses first"; return }
        if (_isRecordingVideo.value) {
            bleManager.stopVideo()
            _isRecordingVideo.value = false
            addMessage(ChatMessage("⏹ Video recording stopped", true))
        } else {
            if (_isRecordingAudio.value) { _toastMessage.value = "Stop audio first"; return }
            bleManager.startVideo()
            _isRecordingVideo.value = true
            addMessage(ChatMessage("🎬 Video recording started", true))
        }
    }

    fun toggleAudio() {
        if (!bleManager.isConnected()) { _toastMessage.value = "Connect to glasses first"; return }
        if (_isRecordingAudio.value) {
            bleManager.stopAudio()
            _isRecordingAudio.value = false
            addMessage(ChatMessage("⏹ Audio recording stopped", true))
        } else {
            if (_isRecordingVideo.value) { _toastMessage.value = "Stop video first"; return }
            bleManager.startAudio()
            _isRecordingAudio.value = true
            addMessage(ChatMessage("🎙️ Audio recording started", true))
        }
    }

    fun generateAiImage() {
        if (!bleManager.isConnected()) { _toastMessage.value = "Connect to glasses first"; return }
        bleManager.generateAiImage()
        addMessage(ChatMessage("✨ AI image generation requested...", true))
    }

    // ── Translation ───────────────────────────────────────────
    fun translateText(text: String) {
        viewModelScope.launch {
            val result = translator.translate(text)
            _lastTranslation.value = result.translated
            if (prefs.autoSpeakTranslation) translator.speak(result.translated)
        }
    }

    fun translatePhoto(bitmap: Bitmap) {
        viewModelScope.launch {
            val result = translator.translatePhoto(bitmap)
            _lastTranslation.value = "Extracted: ${result.original}\n\nTranslated: ${result.translated}"
            if (prefs.autoSpeakTranslation) translator.speak(result.translated)
        }
    }

    fun translateLastPhoto() {
        if (!bleManager.isConnected()) { _toastMessage.value = "Connect to glasses first"; return }
        bleManager.takePhoto()
        addMessage(ChatMessage("📷 Taking photo for translation...", true))
        // After photo arrives, GlassesEventListener.onPhotoCaptured fires
        // For translation we override it in the translate tab
    }

    // ── Settings ──────────────────────────────────────────────
    fun saveKeys(gemini: String, openai: String, claude: String, googleTranslate: String) {
        prefs.geminiKey = gemini
        prefs.openaiKey = openai
        prefs.claudeKey = claude
        prefs.googleTranslateKey = googleTranslate
        aiRouter.geminiKey = gemini
        aiRouter.openaiKey = openai
        aiRouter.claudeKey = claude
        translator.geminiKey = gemini
        translator.googleCloudKey = googleTranslate
        _toastMessage.value = "API keys saved securely ✓"
    }

    fun setActiveProvider(provider: AiProvider) {
        aiRouter.activeProvider = provider
        prefs.activeAiProvider = provider.name
        aiRouter.clearHistory()
        addMessage(ChatMessage("Switched to ${provider.displayName}", false, "System"))
    }

    fun setTranslationTarget(langCode: String) {
        translator.targetLang = langCode
        prefs.translationTargetLang = langCode
    }

    fun clearToast() { _toastMessage.value = null }
    fun clearHistory() {
        aiRouter.clearHistory()
        _chatMessages.value = listOf(ChatMessage("Chat history cleared. How can I help?", false, "System"))
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.disconnect()
        translator.release()
    }
}
