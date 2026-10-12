package com.example.service

import android.content.Context
import android.graphics.Bitmap
import com.example.data.api.VisionAiRepository
import com.example.model.ChatMessage
import com.example.model.MessageSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun setPanelExpanded(expanded: Boolean) {
        _isPanelExpanded.value = expanded
    }

    fun togglePanel() {
        _isPanelExpanded.value = !_isPanelExpanded.value
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
        _currentImage.value = null
        _statusMessage.value = "Image removed."
    }

    fun clearChat() {
        _chatMessages.value = emptyList()
        _errorMessage.value = null
        _statusMessage.value = "Chat reset."
    }

    fun setErrorMessage(error: String?) {
        _errorMessage.value = error
    }

    fun setStatusMessage(status: String?) {
        _statusMessage.value = status
    }

    fun sendPrompt(prompt: String, context: Context) {
        val trimmed = prompt.trim()
        val image = _currentImage.value

        if (trimmed.isEmpty() && image == null) {
            _errorMessage.value = "Please select an image or type a prompt first."
            return
        }

        val displayPrompt = trimmed.ifEmpty { "Explain this image" }
        val userMessage = ChatMessage(
            sender = MessageSender.USER,
            text = displayPrompt,
            hasImageAttachment = image != null
        )

        _chatMessages.value = _chatMessages.value + userMessage
        _isLoading.value = true
        _errorMessage.value = null
        _statusMessage.value = "Peek is analyzing with AI..."

        scope.launch {
            val repository = VisionAiRepository.getInstance(context)
            val result = repository.analyzeImage(
                bitmap = image,
                prompt = displayPrompt,
                history = _chatMessages.value.dropLast(1)
            )

            _isLoading.value = false
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
                    text = "⚠️ $errorText",
                    isError = true
                )
                _statusMessage.value = "Error occurred."
            }
        }
    }
}
