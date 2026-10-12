package com.example.data.api

data class ApiConfig(
    val provider: VisionAiProvider = VisionAiProvider.GEMINI,
    val apiKey: String = "",
    val baseUrl: String = VisionAiProvider.OPENAI_COMPATIBLE.defaultBaseUrl,
    val modelName: String = VisionAiProvider.GEMINI.defaultModel
)
