package com.example.data.api

import android.content.Context
import com.example.model.ChatMessage

class VisionAiRepository(context: Context) {
    private val configRepository = ApiConfigRepository.getInstance(context)
    private val geminiService = GeminiVisionService()
    private val openAiService = OpenAiVisionService()

    suspend fun analyzeImage(
        imageBase64: String?,
        prompt: String,
        history: List<ChatMessage>
    ): Result<String> {
        val config = configRepository.getConfig()
        val apiKey = configRepository.getEffectiveApiKey(config)

        if (apiKey.isBlank()) {
            return Result.failure(
                IllegalStateException("No API key configured for ${config.provider.displayName}. Please open Settings to set your key.")
            )
        }

        return when (config.provider) {
            VisionAiProvider.GEMINI -> geminiService.analyze(imageBase64, prompt, history, config, apiKey)
            VisionAiProvider.OPENAI_COMPATIBLE -> openAiService.analyze(imageBase64, prompt, history, config, apiKey)
        }
    }

    suspend fun testConnection(customConfig: ApiConfig? = null): Result<String> {
        val config = customConfig ?: configRepository.getConfig()
        val apiKey = configRepository.getEffectiveApiKey(config)
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("No API key found. Please enter an API key first."))
        }
        val prompt = "Respond in 5 words confirming the vision API connection is working."
        return when (config.provider) {
            VisionAiProvider.GEMINI -> geminiService.analyze(null, prompt, emptyList(), config, apiKey)
            VisionAiProvider.OPENAI_COMPATIBLE -> openAiService.analyze(null, prompt, emptyList(), config, apiKey)
        }
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
