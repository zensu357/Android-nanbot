package com.example.nanobot.data.mapper

import com.example.nanobot.core.database.entity.MessageEntity
import com.example.nanobot.core.database.entity.SessionEntity
import com.example.nanobot.core.model.Attachment
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.ChatSession
import com.example.nanobot.core.model.MessageRole
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val attachmentJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

fun SessionEntity.toModel(): ChatSession = ChatSession(
    id = id,
    title = title,
    parentSessionId = parentSessionId,
    subagentDepth = subagentDepth,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ChatSession.toEntity(): SessionEntity = SessionEntity(
    id = id,
    title = title,
    parentSessionId = parentSessionId,
    subagentDepth = subagentDepth,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun MessageEntity.toModel(): ChatMessage = ChatMessage(
    id = id,
    sessionId = sessionId,
    role = MessageRole.valueOf(role),
    content = content,
    attachments = attachmentsJson?.let {
        attachmentJson.decodeFromString(ListSerializer(Attachment.serializer()), it)
    }.orEmpty(),
    toolCallId = toolCallId,
    toolName = toolName,
    toolCallsJson = toolCallsJson,
    protectedContext = protectedContext,
    finishReason = finishReason,
    createdAt = createdAt
)

fun ChatMessage.toEntity(): MessageEntity = MessageEntity(
    id = id,
    sessionId = sessionId,
    role = role.name,
    content = content,
    attachmentsJson = attachments.takeIf { it.isNotEmpty() }?.let {
        attachmentJson.encodeToString(ListSerializer(Attachment.serializer()), it)
    },
    toolCallId = toolCallId,
    toolName = toolName,
    toolCallsJson = toolCallsJson,
    protectedContext = protectedContext,
    finishReason = finishReason,
    createdAt = createdAt
)
