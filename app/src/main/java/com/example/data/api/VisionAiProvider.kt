package com.example.data.api

enum class VisionAiProvider(
    val displayName: String,
    val defaultModel: String,
    val defaultBaseUrl: String,
    val supportsCustomUrl: Boolean,
    val supportedModels: List<String>
) {
    GEMINI(
        displayName = "Google Gemini",
        defaultModel = "gemini-2.5-flash",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/",
        supportsCustomUrl = false,
        supportedModels = listOf(
            "gemini-2.5-flash",
            "gemini-3.5-flash",
            "gemini-3.1-pro-preview"
        )
    ),
    OPENAI_COMPATIBLE(
        displayName = "OpenAI-Compatible",
        defaultModel = "gpt-4o-mini",
        defaultBaseUrl = "https://api.openai.com/v1/",
        supportsCustomUrl = true,
        supportedModels = listOf(
            "gpt-4o-mini",
            "gpt-4o",
            "custom"
        )
    )
}
