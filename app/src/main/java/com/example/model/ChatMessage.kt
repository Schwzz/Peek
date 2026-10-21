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
    val imageBase64: String? = null,
    val isCancelled: Boolean = false
)
