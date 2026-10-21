package com.example.data.api

import com.example.model.ChatMessage

interface VisionAiService {
    suspend fun analyze(
        imageBase64: String?,
        prompt: String,
        history: List<ChatMessage>,
        config: ApiConfig,
        apiKey: String
    ): Result<String>
}
