package com.example.nanobot.core.ai

import com.example.nanobot.core.model.AttachmentType
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.LlmAttachmentDto
import com.example.nanobot.core.model.LlmMessageDto
import com.example.nanobot.core.model.LlmToolCallDto
import com.example.nanobot.core.model.ProviderChatResult
import com.example.nanobot.core.model.ToolCallRequest
import com.example.nanobot.core.model.MessageRole
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

private val mapperJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

fun ChatMessage.toLlmMessage(): LlmMessageDto {
    val normalizedRole = role.name.lowercase()
    val normalizedContent = when {
        content != null -> JsonPrimitive(content)
        role == MessageRole.ASSISTANT && toolCallsJson != null -> JsonNull
        else -> JsonPrimitive("")
    }
    val toolCalls = toolCallsJson?.let { mapperJson.decodeFromString<List<LlmToolCallDto>>(it) }

    return LlmMessageDto(
        role = normalizedRole,
        content = normalizedContent,
        attachments = attachments.map { attachment ->
            when (attachment.type) {
                AttachmentType.IMAGE -> LlmAttachmentDto(
                    type = "image",
                    mimeType = attachment.mimeType,
                    fileName = attachment.displayName,
                    localPath = attachment.localPath
                )
                AttachmentType.FILE -> LlmAttachmentDto(
                    type = "file",
                    mimeType = attachment.mimeType,
                    fileName = attachment.displayName,
                    localPath = attachment.localPath
                )
            }
        },
        toolCalls = toolCalls,
        toolCallId = toolCallId,
        // OpenAI-style tool result messages should carry only `tool_call_id`, not a `name` field.
        name = toolName?.takeIf { role != MessageRole.TOOL }
    )
}

fun ChatMessage.toHistoryLlmMessage(): LlmMessageDto {
    if (attachments.isEmpty()) return toLlmMessage()

    val attachmentCount = attachments.size
    val historyNote = if (attachmentCount == 1) {
        "[A prior attachment was omitted from replay for provider compatibility. Ask the user to re-attach it if needed.]"
    } else {
        "[$attachmentCount prior attachments were omitted from replay for provider compatibility. Ask the user to re-attach them if needed.]"
    }
    val mergedContent = listOfNotNull(
        content?.takeIf { it.isNotBlank() },
        historyNote
    ).joinToString("\n\n")

    return toLlmMessage().copy(
        content = JsonPrimitive(mergedContent.ifBlank { historyNote }),
        attachments = emptyList()
    )
}

fun ProviderChatResult.toAssistantMessage(sessionId: String): ChatMessage {
    val encodedToolCalls = if (toolCalls.isEmpty()) {
        null
    } else {
        mapperJson.encodeToString(toolCalls.map { it.toDto() })
    }

        return ChatMessage(
        sessionId = sessionId,
        role = MessageRole.ASSISTANT,
        content = content,
        toolCallsJson = encodedToolCalls,
        finishReason = finishReason
    )
}

private fun ToolCallRequest.toDto(): LlmToolCallDto = LlmToolCallDto(
    id = id,
    type = "function",
    function = com.example.nanobot.core.model.LlmToolCallFunctionDto(
        name = name,
        arguments = arguments.toString()
    ),
    thoughtSignature = thoughtSignature
)
