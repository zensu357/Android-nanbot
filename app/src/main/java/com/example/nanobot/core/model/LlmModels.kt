package com.example.nanobot.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class LlmChatRequest(
        val model: String,
        val messages: List<LlmMessageDto>,
        val temperature: Double = 0.2,
        @SerialName("max_tokens") val maxTokens: Int? = null,
        val tools: List<LlmToolDefinitionDto>? = null,
        @SerialName("tool_choice") val toolChoice: String? = null
)

@Serializable
data class LlmMessageDto(
        val role: String,
        val content: JsonElement? = null,
        @kotlinx.serialization.Transient val attachments: List<LlmAttachmentDto> = emptyList(),
        @SerialName("tool_calls") val toolCalls: List<LlmToolCallDto>? = null,
        @SerialName("tool_call_id") val toolCallId: String? = null,
        val name: String? = null,
        @SerialName("reasoning_content") val reasoningContent: String? = null
)

@Serializable
data class LlmAttachmentDto(
        val type: String,
        val mimeType: String,
        val fileName: String,
        val localPath: String,
        val dataUrl: String? = null
)

@Serializable
data class LlmChatResponse(
        val id: String? = null,
        val choices: List<LlmChoiceDto> = emptyList(),
        val usage: LlmUsageDto? = null
)

@Serializable
data class LlmChoiceDto(
        val index: Int,
        val message: LlmMessageDto,
        @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class LlmToolDefinitionDto(val type: String, val function: LlmToolFunctionDto)

@Serializable
data class LlmToolFunctionDto(
        val name: String,
        val description: String,
        val parameters: JsonObject
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class LlmToolCallDto(
        val id: String,
        @EncodeDefault val type: String = "function",
        val function: LlmToolCallFunctionDto,
        @SerialName("thought_signature") @JsonNames("thoughtSignature") val thoughtSignature: String? = null
)

@Serializable data class LlmToolCallFunctionDto(val name: String, val arguments: String)

@Serializable
data class LlmUsageDto(
        @SerialName("prompt_tokens") val promptTokens: Int? = null,
        @SerialName("completion_tokens") val completionTokens: Int? = null,
        @SerialName("total_tokens") val totalTokens: Int? = null
)

data class ToolCallRequest(
        val id: String,
        val name: String,
        val arguments: JsonObject,
        val thoughtSignature: String? = null
)

data class ProviderChatResult(
        val content: String?,
        val toolCalls: List<ToolCallRequest> = emptyList(),
        val finishReason: String = "stop",
        val reasoningContent: String? = null
)

data class AgentTurnResult(val newMessages: List<ChatMessage>, val finalResponse: ChatMessage?)
