package com.example.nanobot.core.ai.provider

import com.example.nanobot.core.attachments.AttachmentStore
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.LlmChatRequest
import com.example.nanobot.core.model.LlmMessageDto
import com.example.nanobot.core.model.LlmToolDefinitionDto
import com.example.nanobot.core.model.ProviderChatResult
import com.example.nanobot.core.model.ToolCallRequest
import com.example.nanobot.core.network.HttpClientFactory
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AzureOpenAiProvider @Inject constructor(
    private val requestSanitizer: ProviderRequestSanitizer,
    private val attachmentStore: AttachmentStore,
    private val httpClientFactory: HttpClientFactory
) {
    suspend fun completeChat(
        config: AgentConfig,
        route: ResolvedProviderRoute,
        request: LlmChatRequest
    ): ProviderChatResult = withContext(Dispatchers.IO) {
        val multimodalRequest = request.copy(
            messages = request.messages.map { it.withResolvedAttachments(attachmentStore) }
        )
        val sanitizedRequest = requestSanitizer.sanitize(
            multimodalRequest.copy(
                model = route.resolvedModel,
                temperature = route.resolvedTemperature
            ),
            route = route
        )
        val endpoint = route.effectiveBaseUrl.cleanCustomBaseUrl() +
            "openai/deployments/${route.resolvedModel}/chat/completions?api-version=2024-10-21"
        val messagesElement = networkJson.encodeToJsonElement(
            ListSerializer(LlmMessageDto.serializer()),
            sanitizedRequest.messages
        )
        val toolsElement = sanitizedRequest.tools?.let {
            networkJson.encodeToJsonElement(
                ListSerializer(LlmToolDefinitionDto.serializer()),
                it
            )
        }

        val payload = buildJsonObject {
            put("messages", messagesElement)
            sanitizedRequest.maxTokens?.coerceAtLeast(1)?.let { put("max_completion_tokens", it) }
            put("temperature", route.resolvedTemperature)
            toolsElement?.let { put("tools", it) }
            sanitizedRequest.toolChoice?.let { put("tool_choice", it) }
        }

        val httpRequest = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .header("api-key", config.apiKey)
            .post(networkJson.encodeToString(payload).toRequestBody("application/json".toMediaType()))
            .build()

        val loggingInterceptor = okhttp3.logging.HttpLoggingInterceptor().apply {
            level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
        }

        httpClientFactory.llmClient {
            addInterceptor(loggingInterceptor)
        }.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext ProviderChatResult(
                    content = "Azure OpenAI API error ${response.code}: ${response.body?.string().orEmpty()}",
                    finishReason = "error"
                )
            }

            val body = response.body?.string().orEmpty()
            val parsed = networkJson.parseToJsonElement(body).jsonObject
            val choice = parsed["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            val message = choice?.get("message")?.jsonObject
            val toolCalls = message?.get("tool_calls")?.jsonArray?.mapNotNull { item ->
                val obj = item.jsonObject
                val function = obj["function"]?.jsonObject ?: return@mapNotNull null
                ToolCallRequest(
                    id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    name = function["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    arguments = networkJson.parseToJsonElement(
                        function["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}"
                    ).jsonObject,
                    thoughtSignature = obj["thought_signature"]?.jsonPrimitive?.contentOrNull
                        ?: obj["thoughtSignature"]?.jsonPrimitive?.contentOrNull
                )
            }.orEmpty()

            ProviderChatResult(
                content = message?.get("content")?.jsonPrimitive?.contentOrNull,
                toolCalls = toolCalls,
                finishReason = choice?.get("finish_reason")?.jsonPrimitive?.contentOrNull ?: "stop",
                reasoningContent = message?.get("reasoning_content")?.jsonPrimitive?.contentOrNull
            )
        }
    }
}

private fun String.cleanCustomBaseUrl(): String {
    var url = this.trim()
    url = url.removeSuffix("/chat/completions/")
    url = url.removeSuffix("/chat/completions")
    return if (url.endsWith('/')) url else "$url/"
}

private val networkJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
