package com.example.nanobot.core.ai.provider

import com.example.nanobot.core.attachments.AttachmentStore
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.LlmChatRequest
import com.example.nanobot.core.model.LlmMessageDto
import com.example.nanobot.core.model.ProviderChatResult
import com.example.nanobot.core.model.ToolCallRequest
import com.example.nanobot.core.network.api.LlmApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.HttpException

class OpenAiCompatibleProvider @Inject constructor(
    private val requestSanitizer: ProviderRequestSanitizer,
    private val attachmentStore: AttachmentStore
) {
    suspend fun completeChat(
        config: AgentConfig,
        route: ResolvedProviderRoute,
        request: LlmChatRequest
    ): ProviderChatResult = withContext(Dispatchers.IO) {
        if (request.messages.any { it.attachments.isNotEmpty() } && !route.supportsImageAttachments) {
            return@withContext ProviderChatResult(
                content = "The selected provider does not support image attachments in this Android build yet.",
                finishReason = "error"
            )
        }

        val multimodalRequest = request.copy(
            messages = request.messages.map { it.withResolvedAttachments(attachmentStore) }
        )
        val sanitizedRequest = requestSanitizer.sanitize(
            multimodalRequest.copy(
                model = route.resolvedModel,
                temperature = route.resolvedTemperature
            )
        )
        val response = try {
            createApi(config, route).createChatCompletion(sanitizedRequest)
        } catch (exception: HttpException) {
            val errorBody = runCatching { exception.response()?.errorBody()?.string().orEmpty() }
                .getOrDefault("")
                .trim()
            val detail = errorBody.ifBlank { exception.message() ?: "HTTP ${exception.code()}" }
            throw IllegalStateException("Provider API error ${exception.code()}: $detail", exception)
        }
        val choice = response.choices.firstOrNull()
        val toolCalls = choice?.message?.toolCalls?.map { toolCall ->
            ToolCallRequest(
                id = toolCall.id,
                name = toolCall.function.name,
                arguments = parseArguments(toolCall.function.arguments)
            )
        }.orEmpty()

        ProviderChatResult(
            content = choice?.message?.content?.jsonPrimitiveOrNull(),
            toolCalls = toolCalls,
            finishReason = choice?.finishReason ?: "stop",
            reasoningContent = choice?.message?.reasoningContent
        )
    }

    private fun createApi(config: AgentConfig, route: ResolvedProviderRoute): LlmApi {
        val loggingInterceptor = okhttp3.logging.HttpLoggingInterceptor().apply {
            level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val builder: Request.Builder = chain.request().newBuilder()
                    .header("Content-Type", "application/json")

                if (config.apiKey.isNotBlank()) {
                    builder.header("Authorization", "Bearer ${config.apiKey}")
                }

                if (route.spec.name == "openrouter") {
                    builder.header("HTTP-Referer", "https://github.com/example/nanobot-android")
                    builder.header("X-Title", "Nanobot Android")
                }

                chain.proceed(builder.build())
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(route.effectiveBaseUrl.cleanCustomBaseUrl())
            .client(client)
            .addConverterFactory(networkJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LlmApi::class.java)
    }
}

private suspend fun LlmMessageDto.withResolvedAttachments(
    attachmentStore: AttachmentStore
): LlmMessageDto {
    if (attachments.isEmpty()) return this

    val textContent = content?.jsonPrimitiveOrNull().orEmpty()
    val parts = buildJsonArray {
        if (textContent.isNotBlank()) {
            add(buildJsonObject {
                put("type", "text")
                put("text", textContent)
            })
        }
        attachments.forEach { attachment ->
            val dataUrl = attachment.dataUrl ?: attachmentStore.buildDataUrl(
                localPath = attachment.localPath,
                mimeType = attachment.mimeType
            )
            add(buildJsonObject {
                put("type", "image_url")
                putJsonObject("image_url") {
                    put("url", dataUrl)
                }
            })
        }
    }

    return copy(content = JsonArray(parts), attachments = attachments)
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

private fun parseArguments(raw: String): JsonObject {
    return runCatching { networkJson.parseToJsonElement(raw).jsonObject }
        .getOrElse { buildJsonObject {} }
}

private fun kotlinx.serialization.json.JsonElement?.jsonPrimitiveOrNull(): String? {
    return when (this) {
        null -> null
        is JsonPrimitive -> contentOrNull
        else -> toString()
    }
}
