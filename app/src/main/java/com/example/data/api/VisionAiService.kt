package com.example.data.api

import android.graphics.Bitmap
import com.example.model.ChatMessage

interface VisionAiService {
    suspend fun analyze(
        bitmap: Bitmap?,
        prompt: String,
        history: List<ChatMessage>,
        config: ApiConfig,
        apiKey: String
    ): Result<String>
}
