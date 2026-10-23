package com.example.data.api

import android.content.Context
import android.graphics.Bitmap
import com.example.model.ChatMessage
import com.example.util.BitmapUtils

class VisionAiRepository(context: Context) {
    private val configRepository = ApiConfigRepository.getInstance(context)
    private val geminiService = GeminiVisionService()
    private val openAiService = OpenAiVisionService()
    private val nativeService = NativeVisionService()

    suspend fun analyzeImage(
        imageBase64: String?,
        prompt: String,
        history: List<ChatMessage>
    ): Result<String> {
        val config = configRepository.getConfig()
        val apiKey = configRepository.getEffectiveApiKey(config)

        if (apiKey.isBlank() && config.provider.requiresApiKey) {
            return Result.failure(
                IllegalStateException("No API key configured for ${config.provider.displayName}. Please open Settings to set your key.")
            )
        }

        if (config.modelName.isBlank()) return Result.failure(IllegalStateException("Choose a model in Models first."))
        if (config.supportsImages == false && (imageBase64 != null || history.any { !it.isError && !it.isCancelled && it.imageBase64 != null })) {
            return Result.failure(IllegalStateException("This model is text-only. Choose an image-capable model, or clear the image and conversation to use text chat."))
        }
        return service(config).analyze(imageBase64, prompt, history, config, apiKey)
    }

    private fun service(config: ApiConfig): VisionAiService = when (config.provider.protocol) {
        ApiProtocol.GEMINI -> geminiService
        ApiProtocol.OPENAI -> openAiService
        else -> nativeService
    }

    suspend fun testConnection(customConfig: ApiConfig? = null, testImage: Boolean = false): Result<String> {
        val config = customConfig ?: configRepository.getConfig()
        val apiKey = configRepository.getEffectiveApiKey(config)
        if (apiKey.isBlank() && config.provider.requiresApiKey) {
            return Result.failure(IllegalStateException("No API key found. Please enter an API key first."))
        }
        // An image test uses a generated swatch, never the user's current screenshot.
        val image = if (testImage) Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).let {
            try { it.eraseColor(android.graphics.Color.BLUE); BitmapUtils.toBase64Jpeg(it) } finally { it.recycle() }
        } else null
        return service(config).analyze(image, if (testImage) "Name the main color in this image." else "Reply with OK.", emptyList(), config, apiKey)
    }

    companion object {
        @Volatile
        private var instance: VisionAiRepository? = null

        fun getInstance(context: Context): VisionAiRepository {
            return instance ?: synchronized(this) {
                instance ?: VisionAiRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
