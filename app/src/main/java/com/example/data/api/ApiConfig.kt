package com.example.data.api

data class ApiConfig(
    val provider: VisionAiProvider = VisionAiProvider.GEMINI,
    val apiKey: String = "",
    val baseUrl: String = provider.defaultBaseUrl,
    val modelName: String = provider.defaultModel,
    val supportsImages: Boolean? = null
)
