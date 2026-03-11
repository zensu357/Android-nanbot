package com.example.nanobot.core.ai.provider

import com.example.nanobot.core.model.LlmChatRequest
import com.example.nanobot.core.model.LlmMessageDto
import com.example.nanobot.core.model.LlmToolCallDto
import java.util.UUID
import javax.inject.Inject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class ProviderRequestSanitizer @Inject constructor() {
    fun sanitize(request: LlmChatRequest): LlmChatRequest {
        val idMap = linkedMapOf<String, String>()
        val sanitizedMessages = request.messages.map { message -> sanitizeMessage(message, idMap) }

        return request.copy(
            maxTokens = request.maxTokens?.coerceAtLeast(1),
            messages = sanitizedMessages
        )
    }

    private fun sanitizeMessage(
        message: LlmMessageDto,
        idMap: MutableMap<String, String>
    ): LlmMessageDto {
        val normalizedToolCalls = message.toolCalls?.map { toolCall ->
            toolCall.copy(id = normalizeToolCallId(toolCall.id, idMap))
        }
        val normalizedToolCallId = message.toolCallId?.takeIf { it.isNotBlank() }?.let { normalizeToolCallId(it, idMap) }
        val normalizedContent = sanitizeContent(
            role = message.role,
            content = message.content,
            hasToolCalls = !normalizedToolCalls.isNullOrEmpty()
        )

        return message.copy(
            content = normalizedContent,
            toolCalls = normalizedToolCalls,
            toolCallId = normalizedToolCallId
        )
    }

    private fun sanitizeContent(
        role: String,
        content: JsonElement?,
        hasToolCalls: Boolean
    ): JsonElement? {
        return when (content) {
            null, JsonNull -> if (role == "assistant" && hasToolCalls) null else JsonPrimitive(" ")
            is JsonPrimitive -> {
                val text = content.contentOrNull
                if (text.isNullOrEmpty()) {
                    if (role == "assistant" && hasToolCalls) null else JsonPrimitive(" ")
                } else {
                    content
                }
            }
            is JsonArray -> {
                val filtered = content.filterNot { item -> isEmptyTextBlock(item) }
                if (filtered.isEmpty()) {
                    if (role == "assistant" && hasToolCalls) null else JsonPrimitive(" ")
                } else {
                    JsonArray(filtered)
                }
            }
            is JsonObject -> JsonArray(listOf(content))
            else -> content
        }
    }

    private fun isEmptyTextBlock(item: JsonElement): Boolean {
        if (item !is JsonObject) return false
        val type = item["type"]?.jsonPrimitive?.contentOrNull ?: return false
        if (type !in setOf("text", "input_text", "output_text")) return false
        return item["text"]?.jsonPrimitive?.contentOrNull.isNullOrEmpty()
    }

    private fun normalizeToolCallId(value: String, idMap: MutableMap<String, String>): String {
        return idMap.getOrPut(value) {
            when {
                value.isBlank() -> generateToolCallId()
                else -> value.trim()
            }
        }
    }

    private fun generateToolCallId(): String {
        return "call_${UUID.randomUUID().toString().replace("-", "")}"
    }
}
