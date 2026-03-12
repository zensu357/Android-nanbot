package com.example.nanobot.core.tools

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.tools.impl.WebSearchTool
import com.example.nanobot.core.web.WebSearchResult
import com.example.nanobot.domain.repository.WebAccessRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class WebSearchToolTest {
    @Test
    fun executeUsesPassedAgentConfigInsteadOfOnlyPersistedSettings() = runTest {
        val repository = RecordingWebAccessRepository()
        val tool = WebSearchTool(repository)
        val config = AgentConfig(webSearchApiKey = "serper-key")

        tool.execute(
            arguments = buildJsonObject {
                put("query", "SSE index")
                put("limit", 3)
            },
            config = config,
            runContext = AgentRunContext.root("session-1")
        )

        assertEquals("serper-key", repository.lastConfig?.webSearchApiKey)
        assertEquals("SSE index", repository.lastQuery)
        assertEquals(3, repository.lastLimit)
    }

    private class RecordingWebAccessRepository : WebAccessRepository {
        var lastConfig: AgentConfig? = null
        var lastQuery: String? = null
        var lastLimit: Int? = null

        override suspend fun fetch(url: String, maxChars: Int, config: AgentConfig?) =
            throw UnsupportedOperationException()

        override suspend fun search(query: String, limit: Int, config: AgentConfig?): WebSearchResult {
            lastConfig = config
            lastQuery = query
            lastLimit = limit
            return WebSearchResult(query = query, results = emptyList())
        }
    }
}
