package com.example.nanobot.core.memory

import com.example.nanobot.core.ai.provider.ProviderRegistry
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.Attachment
import com.example.nanobot.core.model.AttachmentType
import com.example.nanobot.core.model.LlmAttachmentDto
import com.example.nanobot.core.model.LlmChatRequest
import com.example.nanobot.core.model.LlmMessageDto
import com.example.nanobot.domain.repository.ChatRepository
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class VisualMemoryExtractor @Inject constructor(
    private val chatRepository: ChatRepository
) {
    private val parserJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun extractFacts(
        screenshot: Attachment,
        contextHint: String,
        config: AgentConfig
    ): List<ExtractedVisualFact> {
        if (screenshot.type != AttachmentType.IMAGE) return emptyList()
        if (!ProviderRegistry.resolve(config).supportsImageAttachments) return emptyList()

        val response = chatRepository.completeChat(
            request = LlmChatRequest(
                model = config.model,
                messages = listOf(
                    LlmMessageDto(
                        role = "system",
                        content = JsonPrimitive(VISUAL_FACT_EXTRACTION_PROMPT)
                    ),
                    LlmMessageDto(
                        role = "user",
                        content = JsonPrimitive(
                            buildString {
                                appendLine("Context:")
                                appendLine(contextHint.ifBlank { "(none)" })
                                appendLine()
                                append("Extract memorable facts from this screenshot.")
                            }
                        ),
                        attachments = listOf(
                            LlmAttachmentDto(
                                type = "image",
                                mimeType = screenshot.mimeType,
                                fileName = screenshot.displayName,
                                localPath = screenshot.localPath
                            )
                        )
                    )
                ),
                temperature = 0.1,
                maxTokens = minOf(config.maxTokens, 500),
                tools = null,
                toolChoice = null
            ),
            config = config
        )

        return parseExtractedFacts(response.content.orEmpty())
    }

    private fun parseExtractedFacts(raw: String): List<ExtractedVisualFact> {
        val normalized = raw.unwrapJsonFence()
        return runCatching {
            when (val element = parserJson.parseToJsonElement(normalized)) {
                is JsonArray -> parserJson.decodeFromJsonElement(ListSerializer(ExtractedVisualFact.serializer()), element)
                is JsonObject -> {
                    val facts = element["facts"] ?: return emptyList()
                    parserJson.decodeFromJsonElement(ListSerializer(ExtractedVisualFact.serializer()), facts)
                }

                else -> emptyList()
            }
        }.getOrDefault(emptyList()).mapNotNull { fact ->
            val trimmedFact = fact.fact.trim()
            if (trimmedFact.isBlank()) {
                null
            } else {
                fact.copy(fact = trimmedFact, confidence = fact.confidence.coerceIn(0.0, 1.0))
            }
        }
    }

    private fun String.unwrapJsonFence(): String {
        val trimmed = trim()
        if (!trimmed.startsWith("```")) return trimmed

        return trimmed
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    companion object {
        private val VISUAL_FACT_EXTRACTION_PROMPT = """
            You are a visual information extractor. Given a screenshot and context,
            extract only facts that would be useful to remember for future interactions.

            Focus on:
            - App names and meaningful visible states
            - User preferences or settings shown on screen
            - Account information such as usernames, but never passwords or secrets
            - Notification content that matters later
            - Task completion or failure status

            Do not extract:
            - UI coordinates, layout geometry, or element positions
            - Decorative imagery or standard OS chrome
            - Time, battery, or signal status unless clearly relevant
            - Passwords, one-time codes, tokens, payment details, or other secrets

            Return only valid JSON as an array like:
            [{"fact":"...","confidence":0.0}]
        """.trimIndent()
    }
}

@Serializable
data class ExtractedVisualFact(
    val fact: String,
    val confidence: Double
)
