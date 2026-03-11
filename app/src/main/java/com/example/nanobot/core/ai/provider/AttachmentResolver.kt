package com.example.nanobot.core.ai.provider

import com.example.nanobot.core.attachments.AttachmentStore
import com.example.nanobot.core.model.LlmMessageDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Resolves [LlmMessageDto] attachments into OpenAI-compatible multimodal content parts.
 *
 * This is shared between [OpenAiCompatibleProvider] and [AzureOpenAiProvider] so that
 * both paths produce identical content-parts payloads.
 */
suspend fun LlmMessageDto.withResolvedAttachments(
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
            when (attachment.type) {
                "image" -> {
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
                "file" -> {
                    val fileContent = attachmentStore.readFileAsText(attachment.localPath)
                    val label = attachment.fileName.ifBlank { attachment.localPath.substringAfterLast('/') }
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "--- FILE: $label ---\n$fileContent\n--- END FILE ---")
                    })
                }
            }
        }
    }

    return copy(content = JsonArray(parts), attachments = attachments)
}

private fun kotlinx.serialization.json.JsonElement?.jsonPrimitiveOrNull(): String? {
    return when (this) {
        null -> null
        is JsonPrimitive -> contentOrNull
        else -> toString()
    }
}
