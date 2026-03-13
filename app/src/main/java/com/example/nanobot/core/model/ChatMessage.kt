package com.example.nanobot.core.model

import java.util.UUID

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val role: MessageRole,
    val content: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null,
    val toolCallsJson: String? = null,
    val protectedContext: Boolean = false,
    val finishReason: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
