package com.example.model

import java.util.UUID

enum class MessageSender {
    USER,
    AI
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val hasImageAttachment: Boolean = false,
    val imageCount: Int = if (hasImageAttachment) 1 else 0,
    val imagesBase64: List<String> = emptyList(),
    val isCancelled: Boolean = false
)
