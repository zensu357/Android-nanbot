package com.example.nanobot.feature.chat

import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.Attachment

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val sessionTitle: String = "New Chat",
    val input: String = "",
    val pendingAttachments: List<Attachment> = emptyList(),
    val expandedToolMessageIds: Set<String> = emptySet(),
    val isSending: Boolean = false,
    val isRunning: Boolean = false,
    val isCancelling: Boolean = false,
    val statusText: String? = null,
    val activeToolName: String? = null,
    val errorMessage: String? = null
)

internal fun ChatUiState.toggleToolMessageExpansion(messageId: String): ChatUiState {
    val updated = expandedToolMessageIds.toMutableSet()
    if (!updated.add(messageId)) {
        updated.remove(messageId)
    }
    return copy(expandedToolMessageIds = updated)
}
