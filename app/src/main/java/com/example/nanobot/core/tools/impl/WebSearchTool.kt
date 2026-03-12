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

class WebSearchTool @Inject constructor(
    private val webAccessRepository: WebAccessRepository
) : AgentTool {
    override val name: String = "web_search"
    override val description: String = "Searches the public web and returns compact result snippets; use it to gather missing evidence, then stop searching once you have enough to answer"
    override val accessCategory: ToolAccessCategory = ToolAccessCategory.EXTERNAL_READ_ONLY
    override val availabilityHint: String = "Requires Web Search API Key; blocked in workspace-restricted mode"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put("description", "Search query. Prefer a focused query that resolves one specific information gap.")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Optional max number of results to return; defaults to 5. Keep this small unless broader coverage is necessary.")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("query")) })
    }

    override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
        val query = arguments["query"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        val limit = arguments["limit"]?.jsonPrimitive?.intOrNull ?: 5
        if (query.isBlank()) {
            return "The 'query' field is required for web_search."
        }

        return runCatching {
            val result = webAccessRepository.search(query, limit, config)
            if (result.results.isEmpty()) {
                return@runCatching "No web search results found for '${result.query}'."
            }

            buildString {
                appendLine("Query: ${result.query}")
                appendLine("Results:")
                result.results.forEachIndexed { index, item ->
                    appendLine("${index + 1}. ${item.title}")
                    appendLine("   URL: ${item.url}")
                    if (item.snippet.isNotBlank()) {
                        appendLine("   Snippet: ${item.snippet}")
                    }
                }
            }.trim()
        }.getOrElse { throwable ->
            throwable.message ?: "Failed to search the web."
        }
    }
}
