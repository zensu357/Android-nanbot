package com.example.nanobot.core.ai

import com.example.nanobot.core.mcp.McpRefreshResult
import com.example.nanobot.core.mcp.McpRegistry
import com.example.nanobot.core.mcp.McpServerDefinition
import com.example.nanobot.core.mcp.McpToolDescriptor
import com.example.nanobot.core.mcp.McpToolDiscoverySnapshot
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.LlmMessageDto
import com.example.nanobot.core.model.MessageRole
import com.example.nanobot.core.model.ProviderChatResult
import com.example.nanobot.core.model.ToolCallRequest
import com.example.nanobot.core.tools.AgentTool
import com.example.nanobot.core.tools.ToolAccessCategory
import com.example.nanobot.core.tools.ToolAccessPolicy
import com.example.nanobot.core.tools.ToolRegistry
import com.example.nanobot.core.tools.ToolValidator
import com.example.nanobot.domain.repository.ChatRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class ToolLoopExecutorIntegrationTest {
    @Test
    fun ordinaryChatCompletesToolCallLoopAndReturnsFinalAssistantResponse() = runTest {
        val chatRepository = ScriptedChatRepository(
            responses = listOf(
                ProviderChatResult(
                    content = null,
                    toolCalls = listOf(
                        ToolCallRequest(
                            id = "tool-1",
                            name = "device_time",
                            arguments = buildJsonObject { }
                        )
                    )
                ),
                ProviderChatResult(content = "The current local time is available above.")
            )
        )
        val toolRegistry = ToolRegistry(ToolValidator(), ToolAccessPolicy()).apply {
            register(
                StaticTool(
                    name = "device_time",
                    accessCategory = ToolAccessCategory.LOCAL_READ_ONLY,
                    result = "Device local time: 2026-03-09 10:00:00"
                )
            )
        }
        val executor = ToolLoopExecutor(chatRepository, toolRegistry)

        val result = executor.execute(
            sessionId = "parent-session",
            initialMessages = listOf(
                LlmMessageDto(role = "user", content = JsonPrimitive("What time is it?"))
            ),
            config = AgentConfig(enableTools = true)
        )

        assertEquals(listOf(MessageRole.ASSISTANT, MessageRole.TOOL, MessageRole.ASSISTANT), result.newMessages.map { it.role })
        assertTrue(result.newMessages[1].content.orEmpty().contains("Device local time"))
        assertEquals("The current local time is available above.", result.finalResponse?.content)
    }

    @Test
    fun restrictToWorkspaceAllowsWorkspaceWriteButRejectsWebAndMcpTools() = runTest {
        val chatRepository = ScriptedChatRepository(
            responses = listOf(
                ProviderChatResult(
                    content = null,
                    toolCalls = listOf(
                        ToolCallRequest("tool-write", "write_file", buildJsonObject {
                            put("relativePath", "notes.txt")
                            put("content", "hello")
                            put("overwrite", true)
                        }),
                        ToolCallRequest("tool-web", "web_fetch", buildJsonObject {
                            put("url", "https://example.com")
                        }),
                        ToolCallRequest("tool-mcp", "mcp.github.issue_search", buildJsonObject {
                            put("query", "nanobot")
                        })
                    )
                ),
                ProviderChatResult(content = "Completed the allowed work and rejected blocked tools.")
            )
        )
        val mcpRegistry = FakeMcpRegistry(
            tools = listOf(
                McpToolDescriptor(
                    serverId = "github",
                    serverLabel = "GitHub MCP",
                    name = "issue_search",
                    remoteName = "issue-search",
                    description = "Search issues",
                    inputSchema = buildJsonObject { put("type", "object") },
                    readOnlyHint = true
                )
            )
        )
        val toolRegistry = ToolRegistry(ToolValidator(), ToolAccessPolicy(), mcpRegistry).apply {
            register(StaticTool("write_file", ToolAccessCategory.WORKSPACE_SIDE_EFFECT, "Path: notes.txt"))
            register(StaticTool("web_fetch", ToolAccessCategory.EXTERNAL_READ_ONLY, "fetched"))
        }
        val executor = ToolLoopExecutor(chatRepository, toolRegistry)

        val result = executor.execute(
            sessionId = "workspace-session",
            initialMessages = listOf(LlmMessageDto(role = "user", content = JsonPrimitive("Update my notes"))),
            config = AgentConfig(enableTools = true, restrictToWorkspace = true)
        )

        val firstRequestTools = chatRepository.requests.first().tools.orEmpty().map { it.function.name }
        assertEquals(listOf("write_file"), firstRequestTools)
        assertTrue(result.newMessages.any { it.toolName == "write_file" && it.content.orEmpty().contains("Path: notes.txt") })
        assertTrue(result.newMessages.any { it.toolName == "web_fetch" && it.content.orEmpty().contains("workspace-restricted mode policy") })
        assertTrue(result.newMessages.any { it.toolName == "mcp.github.issue_search" && it.content.orEmpty().contains("workspace-restricted mode policy") })
    }

    @Test
    fun cachedDynamicMcpToolExecutesWhenUnrestrictedAndIsBlockedWhenRestricted() = runTest {
        val mcpRegistry = FakeMcpRegistry(
            tools = listOf(
                McpToolDescriptor(
                    serverId = "github",
                    serverLabel = "GitHub MCP",
                    name = "issue_search",
                    remoteName = "issue-search",
                    description = "Search issues",
                    inputSchema = buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {})
                    },
                    readOnlyHint = true
                )
            )
        )
        val toolRegistry = ToolRegistry(ToolValidator(), ToolAccessPolicy(), mcpRegistry)

        val unrestrictedChatRepository = ScriptedChatRepository(
            responses = listOf(
                ProviderChatResult(
                    content = null,
                    toolCalls = listOf(
                        ToolCallRequest(
                            id = "tool-mcp",
                            name = "mcp.github.issue_search",
                            arguments = buildJsonObject { put("query", "nanobot") }
                        )
                    )
                ),
                ProviderChatResult(content = "Used the cached MCP tool successfully.")
            )
        )
        val unrestrictedExecutor = ToolLoopExecutor(unrestrictedChatRepository, toolRegistry)

        val unrestrictedResult = unrestrictedExecutor.execute(
            sessionId = "mcp-session",
            initialMessages = listOf(LlmMessageDto(role = "user", content = JsonPrimitive("Search GitHub issues"))),
            config = AgentConfig(enableTools = true, restrictToWorkspace = false)
        )

        assertTrue(unrestrictedChatRepository.requests.first().tools.orEmpty().any { it.function.name == "mcp.github.issue_search" })
        assertTrue(unrestrictedResult.newMessages.any { it.toolName == "mcp.github.issue_search" && it.content.orEmpty().contains("MCP result for mcp.github.issue_search") })

        val restrictedChatRepository = ScriptedChatRepository(
            responses = listOf(
                ProviderChatResult(
                    content = null,
                    toolCalls = listOf(
                        ToolCallRequest(
                            id = "tool-mcp-restricted",
                            name = "mcp.github.issue_search",
                            arguments = buildJsonObject { put("query", "nanobot") }
                        )
                    )
                ),
                ProviderChatResult(content = "The restricted run blocked the MCP tool.")
            )
        )
        val restrictedExecutor = ToolLoopExecutor(restrictedChatRepository, toolRegistry)

        val restrictedResult = restrictedExecutor.execute(
            sessionId = "mcp-session-restricted",
            initialMessages = listOf(LlmMessageDto(role = "user", content = JsonPrimitive("Search GitHub issues"))),
            config = AgentConfig(enableTools = true, restrictToWorkspace = true)
        )

        assertTrue(restrictedChatRepository.requests.first().tools.orEmpty().none { it.function.name == "mcp.github.issue_search" })
        assertTrue(restrictedResult.newMessages.any { it.toolName == "mcp.github.issue_search" && it.content.orEmpty().contains("workspace-restricted mode policy") })
    }

    @Test
    fun requestsFinalAnswerAfterToolIterationLimitInsteadOfStoppingAbruptly() = runTest {
        val chatRepository = ScriptedChatRepository(
            responses = listOf(
                ProviderChatResult(
                    content = null,
                    toolCalls = listOf(
                        ToolCallRequest(
                            id = "tool-web",
                            name = "web_fetch",
                            arguments = buildJsonObject { put("url", "https://example.com") }
                        )
                    )
                ),
                ProviderChatResult(content = "Based on the fetched page, here is the best answer I can provide now.")
            )
        )
        val toolRegistry = ToolRegistry(ToolValidator(), ToolAccessPolicy()).apply {
            register(StaticTool("web_fetch", ToolAccessCategory.EXTERNAL_READ_ONLY, "Fetched page summary"))
        }
        val executor = ToolLoopExecutor(chatRepository, toolRegistry)

        val result = executor.execute(
            sessionId = "tool-limit-session",
            initialMessages = listOf(LlmMessageDto(role = "user", content = JsonPrimitive("Research this page"))),
            config = AgentConfig(enableTools = true, maxToolIterations = 1)
        )

        assertTrue(result.finalResponse?.content.orEmpty().contains("best answer"))
        assertTrue(chatRepository.requests.last().tools.isNullOrEmpty())
        assertTrue(chatRepository.requests.last().messages.last().content?.jsonPrimitive?.content.orEmpty().contains("iteration limit reached"))
    }

    private class ScriptedChatRepository(
        private val responses: List<ProviderChatResult>
    ) : ChatRepository {
        val requests = mutableListOf<com.example.nanobot.core.model.LlmChatRequest>()
        private var index = 0

        override suspend fun completeChat(
            request: com.example.nanobot.core.model.LlmChatRequest,
            config: AgentConfig
        ): ProviderChatResult {
            requests += request
            return responses.getOrElse(index++) { responses.last() }
        }
    }

    private class StaticTool(
        override val name: String,
        override val accessCategory: ToolAccessCategory,
        private val result: String
    ) : AgentTool {
        override val description: String = name
        override val parametersSchema: JsonObject = buildJsonObject { put("type", "object") }

        override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
            return result
        }
    }

    private class FakeMcpRegistry(
        private val tools: List<McpToolDescriptor> = emptyList()
    ) : McpRegistry {
        override fun observeServers(): Flow<List<McpServerDefinition>> = flowOf(emptyList())

        override fun observeCachedTools(): Flow<List<McpToolDescriptor>> = flowOf(tools)

        override suspend fun listEnabledServers(): List<McpServerDefinition> = emptyList()

        override suspend fun listEnabledTools(): List<McpToolDescriptor> = tools

        override suspend fun refreshTools(): McpRefreshResult = McpRefreshResult(0, tools.size)

        override suspend fun saveServers(servers: List<McpServerDefinition>) = Unit

        override suspend fun callTool(toolName: String, arguments: JsonObject): String {
            val query = arguments["query"]?.jsonPrimitive?.contentOrNull.orEmpty()
            return "MCP result for $toolName ($query)"
        }

        override suspend fun getDiscoverySnapshot(): McpToolDiscoverySnapshot {
            return McpToolDiscoverySnapshot(emptyServers(), tools)
        }

        private fun emptyServers(): List<McpServerDefinition> = emptyList()
    }
}
