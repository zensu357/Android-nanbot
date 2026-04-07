package com.example.nanobot.feature.chat

import com.example.nanobot.core.skills.ActivatedSkillSource
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.Attachment
import com.example.nanobot.core.voice.VoiceState

data class ActivatableSkillUiState(
    val name: String,
    val title: String,
    val description: String
)

data class ActiveSkillUiState(
    val name: String,
    val title: String,
    val source: ActivatedSkillSource
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val sessionTitle: String = "New Chat",
    val modelName: String? = null,
    val input: String = "",
    val pendingAttachments: List<Attachment> = emptyList(),
    val expandedToolMessageIds: Set<String> = emptySet(),
    val isLoadingHistory: Boolean = false,
    val isSending: Boolean = false,
    val isRunning: Boolean = false,
    val isCancelling: Boolean = false,
    val availableSkills: List<ActivatableSkillUiState> = emptyList(),
    val activeSkills: List<ActiveSkillUiState> = emptyList(),
    val statusText: String? = null,
    val activeToolName: String? = null,
    val errorMessage: String? = null,
    val voiceState: VoiceState = VoiceState.IDLE,
    val voiceInputEnabled: Boolean = false,
    val voiceAutoPlayEnabled: Boolean = false,
    val voiceStatusHint: String? = null
)

internal fun ChatUiState.toggleToolMessageExpansion(messageId: String): ChatUiState {
    val updated = expandedToolMessageIds.toMutableSet()
    if (!updated.add(messageId)) {
        updated.remove(messageId)
    }
    return copy(expandedToolMessageIds = updated)
}
