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

    private var imageToReplace: Bitmap? = null

    fun beginCapture(replaceCurrentImage: Boolean = false): Boolean {
        if (_isCapturing.value) return false
        _isToolboxExpanded.value = false
        imageToReplace = if (replaceCurrentImage) _currentImage.value else null
        captureVersion++
        captureRequestedAt = System.nanoTime() / 1_000_000
        _isCapturing.value = true
        return true
    }

    fun finishCapture(showPanel: Boolean = true) {
        captureVersion++
        _capturedScreenToCrop.value = null
        imageToReplace = null
        setPanelExpanded(showPanel)
        _isCapturing.value = false
    }

    const val MAX_IMAGES = 6
    private val _images = MutableStateFlow<List<Bitmap>>(emptyList())
    val images: StateFlow<List<Bitmap>> = _images.asStateFlow()
    private val _isLoadingImages = MutableStateFlow(false)
    val isLoadingImages: StateFlow<Boolean> = _isLoadingImages.asStateFlow()

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

    // Kept as a single-image reset for session setup; capture/import use appendImage.
    fun setCurrentImage(bitmap: Bitmap?) {
        _images.value = listOfNotNull(bitmap)
        _currentImage.value = bitmap
    }

    fun appendImage(bitmap: Bitmap): Boolean {
        if (_images.value.size >= MAX_IMAGES) {
            _errorMessage.value = "You can attach up to $MAX_IMAGES images. Remove an image before adding more."
            return false
        }
        val scaled = BitmapUtils.scaleDownIfNeeded(bitmap)
        _images.value = _images.value + scaled
        _currentImage.value = scaled
        _statusMessage.value = "${_images.value.size} image(s) ready."
        _errorMessage.value = null
        return true
    }

    fun selectImage(bitmap: Bitmap) {
        if (bitmap in _images.value) _currentImage.value = bitmap
    }

    fun removeImage(bitmap: Bitmap) {
        _images.value = _images.value.filterNot { it === bitmap }
        if (_currentImage.value === bitmap) _currentImage.value = _images.value.lastOrNull()
    }

    fun completeImageSelection(bitmap: Bitmap) {
        val target = imageToReplace
        val index = _images.value.indexOf(target)
        if (target != null && index >= 0) {
            val scaled = BitmapUtils.scaleDownIfNeeded(bitmap)
            _images.value = _images.value.toMutableList().apply { set(index, scaled) }
            _currentImage.value = scaled
        } else appendImage(bitmap)
    }

    fun setCapturedScreenToCrop(bitmap: Bitmap?) {
        _capturedScreenToCrop.value = bitmap
    }

    fun cancelImageLoading() {
        imageLoadVersion++
        imageLoad?.cancel()
        _isLoadingImages.value = false
    }

    fun clearImage() {
        cancelImageLoading()
        _images.value = emptyList()
        _currentImage.value = null
        _statusMessage.value = "Images removed."
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
    private var imageLoadVersion = 0L

    fun loadImages(context: Context, uris: List<Uri>, onFinished: () -> Unit = {}) {
        val loadVersion = ++imageLoadVersion
        imageLoad?.cancel()
        val appContext = context.applicationContext
        _isLoadingImages.value = true
        _statusMessage.value = "Loading images…"
        imageLoad = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var failures = 0
            try {
                // Decode sequentially to bound peak memory. Enforce our own limit because
                // older document pickers can ignore PickMultipleVisualMedia's limit.
                val available = (MAX_IMAGES - _images.value.size).coerceAtLeast(0)
                if (uris.size > available) _errorMessage.value = "Only $available more image(s) fit; the limit is $MAX_IMAGES."
                for (uri in uris.take(available)) {
                    try {
                        val bitmap = ImageLoader.decode(appContext, uri)
                        if (!appendImage(bitmap)) bitmap.recycle()
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { failures++ }
                }
                if (failures > 0) _errorMessage.value = "Could not open $failures image(s). Try another file."
                else if (uris.size > available) _errorMessage.value = "Added $available image(s). You can attach up to $MAX_IMAGES."
                setPanelExpanded(true)
            } finally {
                if (loadVersion == imageLoadVersion) _isLoadingImages.value = false
                onFinished()
            }
        }
    }

    // Only the six most recent valid messages are sent. Release image strings in
    // older UI rows instead of keeping sixty turns of full image data in memory.
    private fun trimHistoryImages(messages: List<ChatMessage>): List<ChatMessage> {
        val retained = messages.filterNot { it.isError || it.isCancelled }.takeLast(6).map { it.id }.toSet()
        return messages.map { if (it.id !in retained && it.imagesBase64.isNotEmpty()) it.copy(imagesBase64 = emptyList()) else it }
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
        analyze: suspend (List<String>, String, List<ChatMessage>) -> Result<String>
    ): Boolean {
        synchronized(requestLock) {
            if (_isLoading.value) {
                return false
            }

            val trimmed = prompt.trim()
            val attachments = _images.value.toList()
            if (_isLoadingImages.value) {
                _errorMessage.value = "Wait for the images to finish loading."
                return false
            }

            if (trimmed.isEmpty() && attachments.isEmpty()) {
                _errorMessage.value = "Please select an image or type a prompt first."
                return false
            }

            val displayPrompt = trimmed.ifEmpty { "Explain these images" }
            val userMessage = ChatMessage(
                sender = MessageSender.USER,
                text = displayPrompt,
                hasImageAttachment = attachments.isNotEmpty(),
                imageCount = attachments.size
            )

            val history = _chatMessages.value.toList()
            val version = ++requestVersion
            activeUserMessageId = userMessage.id
            _chatMessages.value = trimHistoryImages((_chatMessages.value + userMessage).takeLast(60))
            _isLoading.value = true
            _errorMessage.value = null
            _statusMessage.value = "Peek is analyzing with AI..."

            activeRequest = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    val encoded = if (attachments.isEmpty()) emptyList() else withContext(Dispatchers.Default) { attachments.map { BitmapUtils.toBase64Jpeg(it) } }
                    if (version != requestVersion) return@launch
                    _chatMessages.value = _chatMessages.value.map {
                        if (it.id == userMessage.id) it.copy(imagesBase64 = encoded) else it
                    }
                    val result = analyze(encoded, displayPrompt, history)
                    if (version != requestVersion) return@launch

                    result.onSuccess { aiResponse ->
                        // Submitted attachments live in the turn history. A follow-up does not
                        // resend them as another current attachment unless the user adds them.
                        _images.value = _images.value.filterNot { it in attachments }
                        if (_currentImage.value in attachments) _currentImage.value = _images.value.lastOrNull()

                        val aiMessage = ChatMessage(
                            sender = MessageSender.AI,
                            text = aiResponse
                        )
                        _chatMessages.value = trimHistoryImages(_chatMessages.value + aiMessage)
                        _statusMessage.value = "Analysis complete. You can ask follow-up questions."
                    }.onFailure { error ->
                        val errorText = error.localizedMessage ?: "Failed to generate AI response."
                        _chatMessages.value = _chatMessages.value.map { if (it.id == userMessage.id) it.copy(isError = true) else it }
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
