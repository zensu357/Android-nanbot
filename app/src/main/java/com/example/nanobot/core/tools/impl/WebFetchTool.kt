package com.example.nanobot.core.tools.impl

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.tools.AgentTool
import com.example.nanobot.core.tools.ToolAccessCategory
import com.example.nanobot.domain.repository.WebAccessRepository
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class WebFetchTool @Inject constructor(
    private val webAccessRepository: WebAccessRepository
) : AgentTool {
    override val name: String = "web_fetch"
    override val description: String = "Fetches a public http/https page, extracts readable text, and returns a compact summary body; fetch only when the page is likely to answer the remaining question"
    override val accessCategory: ToolAccessCategory = ToolAccessCategory.EXTERNAL_READ_ONLY
    override val availabilityHint: String = "Public web read-only access; blocked in workspace-restricted mode"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("url") {
                put("type", "string")
                put("description", "Public http/https URL to fetch. Prefer pages that directly answer the outstanding question.")
            }
            putJsonObject("maxChars") {
                put("type", "integer")
                put("description", "Optional max characters to return; defaults to 4000. Use a modest limit when only a small excerpt is needed.")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("url")) })
    }

    override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
        val url = arguments["url"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        val maxChars = arguments["maxChars"]?.jsonPrimitive?.intOrNull ?: 4_000
        if (url.isBlank()) {
            return "The 'url' field is required for web_fetch."
        }

        return runCatching {
            val result = webAccessRepository.fetch(url, maxChars, config)
            buildString {
                appendLine("URL: ${result.url}")
                result.title?.let { appendLine("Title: $it") }
                result.contentType?.let { appendLine("Content Type: $it") }
                appendLine("Truncated: ${result.truncated}")
                appendLine("Content:")
                append(result.content)
            }.trim()
        }.getOrElse { throwable ->
            throwable.message ?: "Failed to fetch web content."
        }
    }
}
