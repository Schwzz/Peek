package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.util.ImageLoader
import com.example.data.api.VisionAiRepository
import com.example.model.ChatMessage
import com.example.model.MessageSender
import com.example.util.BitmapUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object OverlayStateManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _isPanelExpanded = MutableStateFlow(false)
    val isPanelExpanded: StateFlow<Boolean> = _isPanelExpanded.asStateFlow()

    private val _isToolboxExpanded = MutableStateFlow(false)
    val isToolboxExpanded: StateFlow<Boolean> = _isToolboxExpanded.asStateFlow()

    fun setToolboxExpanded(expanded: Boolean) {
        if (expanded && _isCapturing.value) return
        _isToolboxExpanded.value = expanded
        if (expanded) _isPanelExpanded.value = false
    }

    fun toggleToolbox() = setToolboxExpanded(!_isToolboxExpanded.value)

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _isScreenSharing = MutableStateFlow(false)
    val isScreenSharing: StateFlow<Boolean> = _isScreenSharing.asStateFlow()
    fun setScreenSharing(active: Boolean) { _isScreenSharing.value = active }
    private val _isStartingScreenSharing = MutableStateFlow(false)
    val isStartingScreenSharing: StateFlow<Boolean> = _isStartingScreenSharing.asStateFlow()
    fun setStartingScreenSharing(starting: Boolean) { _isStartingScreenSharing.value = starting }
    var captureVersion = 0L
        private set
    var captureRequestedAt = 0L
        private set

    fun beginCapture(): Boolean {
        if (_isCapturing.value) return false
        _isToolboxExpanded.value = false
        captureVersion++
        captureRequestedAt = System.nanoTime() / 1_000_000
        _isCapturing.value = true
        return true
    }

    fun finishCapture(showPanel: Boolean = true) {
        captureVersion++
        _capturedScreenToCrop.value = null
        setPanelExpanded(showPanel)
        _isCapturing.value = false
    }

    private val _currentImage = MutableStateFlow<Bitmap?>(null)
    val currentImage: StateFlow<Bitmap?> = _currentImage.asStateFlow()

    private val _capturedScreenToCrop = MutableStateFlow<Bitmap?>(null)
    val capturedScreenToCrop: StateFlow<Bitmap?> = _capturedScreenToCrop.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>("Ready. Tap Capture or + to begin.")
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _draftPrompt = MutableStateFlow("")
    val draftPrompt: StateFlow<String> = _draftPrompt.asStateFlow()
    fun setDraftPrompt(text: String) { _draftPrompt.value = text }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun setPanelExpanded(expanded: Boolean) {
        _isPanelExpanded.value = expanded
        if (expanded) _isToolboxExpanded.value = false
    }

    fun togglePanel() {
        setPanelExpanded(!_isPanelExpanded.value)
    }

    fun setCurrentImage(bitmap: Bitmap?) {
        _currentImage.value = bitmap
        if (bitmap != null) {
            _statusMessage.value = "Image ready (${bitmap.width}x${bitmap.height}px). Ask a question!"
            _errorMessage.value = null
        }
    }

    fun setCapturedScreenToCrop(bitmap: Bitmap?) {
        _capturedScreenToCrop.value = bitmap
    }

    fun clearImage() {
        imageLoad?.cancel()
        _currentImage.value = null
        _statusMessage.value = "Image removed."
    }

    fun clearChat() {
        cancelRequest()
        _chatMessages.value = emptyList()
        _draftPrompt.value = ""
        _errorMessage.value = null
        _statusMessage.value = "Chat reset."
    }

    fun setErrorMessage(error: String?) {
        _errorMessage.value = error
    }

    fun setStatusMessage(status: String?) {
        _statusMessage.value = status
    }

    private val requestLock = Any()
    private var activeRequest: Job? = null
    private var activeUserMessageId: String? = null
    private var requestVersion = 0L
    private var imageLoad: Job? = null

    fun loadImage(context: Context, uri: Uri, onFinished: () -> Unit = {}) {
        imageLoad?.cancel()
        val appContext = context.applicationContext
        _statusMessage.value = "Loading image..."
        imageLoad = scope.launch {
            try {
                setCurrentImage(ImageLoader.decode(appContext, uri))
                setPanelExpanded(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setErrorMessage("Failed to load image: ${e.localizedMessage}")
                if (_isServiceRunning.value) setPanelExpanded(true)
            } finally {
                onFinished()
            }
        }
    }

    fun cancelRequest() = synchronized(requestLock) {
        requestVersion++
        activeRequest?.cancel()
        activeRequest = null
        val pendingId = activeUserMessageId
        _chatMessages.value = _chatMessages.value.map {
            if (it.id == pendingId) it.copy(isCancelled = true) else it
        }
        activeUserMessageId = null
        _isLoading.value = false
        _statusMessage.value = "Request stopped."
    }

    fun sendPrompt(prompt: String, context: Context): Boolean {
        val configRepo = com.example.data.api.ApiConfigRepository.getInstance(context)
        if (!configRepo.isConfigured()) {
            _errorMessage.value = "Choose a model and configure its connection in Models first."
            return false
        }
        val repository = VisionAiRepository.getInstance(context.applicationContext)
        return sendPrompt(prompt) { image, text, history -> repository.analyzeImage(image, text, history) }
    }

    internal fun sendPrompt(
        prompt: String,
        analyze: suspend (String?, String, List<ChatMessage>) -> Result<String>
    ): Boolean {
        synchronized(requestLock) {
            if (_isLoading.value) {
                return false
            }

            val trimmed = prompt.trim()
            val image = _currentImage.value

            if (trimmed.isEmpty() && image == null) {
                _errorMessage.value = "Please select an image or type a prompt first."
                return false
            }

            val displayPrompt = trimmed.ifEmpty { "Explain this image" }
            val userMessage = ChatMessage(
                sender = MessageSender.USER,
                text = displayPrompt,
                hasImageAttachment = image != null
            )

            val history = _chatMessages.value.toList()
            val version = ++requestVersion
            activeUserMessageId = userMessage.id
            _chatMessages.value = (_chatMessages.value + userMessage).takeLast(60)
            _isLoading.value = true
            _errorMessage.value = null
            _statusMessage.value = "Peek is analyzing with AI..."

            activeRequest = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    val encoded = if (image == null) null else withContext(Dispatchers.Default) { BitmapUtils.toBase64Jpeg(image) }
                    if (version != requestVersion) return@launch
                    _chatMessages.value = _chatMessages.value.map {
                        if (it.id == userMessage.id) it.copy(imageBase64 = encoded) else it
                    }
                    val result = analyze(encoded, displayPrompt, history)
                    if (version != requestVersion) return@launch

                    result.onSuccess { aiResponse ->
                        val aiMessage = ChatMessage(
                            sender = MessageSender.AI,
                            text = aiResponse
                        )
                        _chatMessages.value = _chatMessages.value + aiMessage
                        _statusMessage.value = "Analysis complete. You can ask follow-up questions."
                    }.onFailure { error ->
                        val errorText = error.localizedMessage ?: "Failed to generate AI response."
                        _errorMessage.value = errorText
                        _chatMessages.value = _chatMessages.value + ChatMessage(
                            sender = MessageSender.AI,
                            text = errorText,
                            isError = true
                        )
                        _statusMessage.value = "Error occurred."
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (version == requestVersion) {
                        _errorMessage.value = e.localizedMessage ?: "Request failed. Please try again."
                        _statusMessage.value = "Error occurred."
                    }
                } finally {
                    synchronized(requestLock) {
                        if (version == requestVersion) {
                            _isLoading.value = false
                            activeRequest = null
                            activeUserMessageId = null
                        }
                    }
                }
            }
            activeRequest?.start()
            return true
        }
    }
}
