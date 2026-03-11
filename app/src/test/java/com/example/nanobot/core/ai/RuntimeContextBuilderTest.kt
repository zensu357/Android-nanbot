package com.example.nanobot.core.ai

import com.example.nanobot.core.ai.provider.ProviderRegistry
import com.example.nanobot.core.mcp.McpRegistry
import com.example.nanobot.core.mcp.McpServerDefinition
import com.example.nanobot.core.mcp.McpToolDescriptor
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.tools.AgentTool
import com.example.nanobot.core.tools.ToolAccessCategory
import com.example.nanobot.core.tools.ToolAccessPolicy
import com.example.nanobot.core.tools.ToolRegistry
import com.example.nanobot.core.tools.ToolValidator
import com.example.nanobot.domain.repository.WorkspaceRepository
import com.example.nanobot.core.workspace.WorkspaceEntry
import com.example.nanobot.core.workspace.WorkspaceFileContent
import com.example.nanobot.core.workspace.WorkspaceReplaceResult
import com.example.nanobot.core.workspace.WorkspaceRoot
import com.example.nanobot.core.workspace.WorkspaceSearchHit
import com.example.nanobot.core.workspace.WorkspaceWriteResult
import com.example.nanobot.testutil.FakeSkillRepository
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

class RuntimeContextBuilderTest {
    @Test
    fun includesSubagentMetadataAndEnabledSkills() = runTest {
        val registry = ToolRegistry(ToolValidator(), ToolAccessPolicy()).apply {
            register(FakeTool("delegate_task", ToolAccessCategory.LOCAL_ORCHESTRATION))
            register(FakeTool("write_file", ToolAccessCategory.WORKSPACE_SIDE_EFFECT))
        }
        val builder = RuntimeContextBuilder(
            workspaceRepository = FakeWorkspaceRepository(),
            toolRegistry = registry,
            skillRepository = FakeSkillRepository(),
            mcpRegistry = FakeMcpRegistry()
        )
        val config = AgentConfig(enabledSkillIds = listOf("planner_mode"))
        val route = ProviderRegistry.resolve(
            providerType = config.providerType,
            apiKey = config.apiKey,
            baseUrl = config.baseUrl,
            model = config.model,
            temperature = config.temperature
        )

        val context = builder.build(
            config = config,
            runContext = AgentRunContext(
                sessionId = "child-session",
                parentSessionId = "parent-session",
                subagentDepth = 1,
                maxSubagentDepth = 1
            ),
            route = route
        )

        assertTrue(context.contains("Session ID: child-session"))
        assertTrue(context.contains("Parent Session ID: parent-session"))
        assertTrue(context.contains("Subagent Depth: 1"))
        assertTrue(context.contains("Can Delegate Subtasks: false"))
        assertTrue(context.contains("Enabled Skills: planner_mode"))
        assertTrue(context.contains("Enabled MCP Servers: GitHub MCP"))
        assertTrue(context.contains("Dynamic MCP Tools: 1"))
        assertTrue(!context.contains("Workspace Sandbox:"))
    }

    @Test
    fun includesDiagnosticsWhenUserAsksForDebugContext() = runTest {
        val registry = ToolRegistry(ToolValidator(), ToolAccessPolicy()).apply {
            register(FakeTool("delegate_task", ToolAccessCategory.LOCAL_ORCHESTRATION))
            register(FakeTool("write_file", ToolAccessCategory.WORKSPACE_SIDE_EFFECT))
        }
        val builder = RuntimeContextBuilder(
            workspaceRepository = FakeWorkspaceRepository(),
            toolRegistry = registry,
            skillRepository = FakeSkillRepository(),
            mcpRegistry = FakeMcpRegistry()
        )
        val config = AgentConfig(enabledSkillIds = listOf("planner_mode"), maxTokens = 512)
        val route = ProviderRegistry.resolve(
            providerType = config.providerType,
            apiKey = config.apiKey,
            baseUrl = config.baseUrl,
            model = config.model,
            temperature = config.temperature
        )

        val context = builder.build(
            config = config,
            runContext = AgentRunContext.root(sessionId = "session-1"),
            route = route,
            latestUserInput = "Help me debug the provider and workspace context."
        )

        assertTrue(context.contains("Workspace Sandbox: workspace:/sandbox"))
        assertTrue(context.contains("Provider Label:"))
        assertTrue(context.contains("Tool Access Policy:"))
    }

    private class FakeMcpRegistry : McpRegistry {
        override fun observeServers() = throw UnsupportedOperationException()

        override fun observeCachedTools() = throw UnsupportedOperationException()

        override suspend fun listEnabledServers(): List<McpServerDefinition> = listOf(
            McpServerDefinition(
                id = "github",
                label = "GitHub MCP",
                endpoint = "https://mcp.example/github",
                enabled = true
            )
        )

        override suspend fun listEnabledTools(): List<McpToolDescriptor> = listOf(
            McpToolDescriptor(
                serverId = "github",
                serverLabel = "GitHub MCP",
                name = "issue_search",
                remoteName = "issue-search",
                description = "Search issues",
                inputSchema = buildJsonObject { },
                readOnlyHint = true
            )
        )

        override suspend fun refreshTools() = throw UnsupportedOperationException()

        override suspend fun saveServers(servers: List<McpServerDefinition>) = Unit

        override suspend fun callTool(toolName: String, arguments: JsonObject): String = toolName

        override suspend fun getDiscoverySnapshot() = throw UnsupportedOperationException()
    }

    private class FakeWorkspaceRepository : WorkspaceRepository {
        override suspend fun getWorkspaceRoot(): WorkspaceRoot =
            WorkspaceRoot(rootId = "workspace:/sandbox", isAvailable = true, accessMode = "read_write")

        override suspend fun list(relativePath: String, limit: Int): List<WorkspaceEntry> = emptyList()

        override suspend fun readText(relativePath: String, maxChars: Int): WorkspaceFileContent {
            error("unused")
        }

        override suspend fun search(query: String, relativePath: String, limit: Int): List<WorkspaceSearchHit> = emptyList()

        override suspend fun writeText(relativePath: String, content: String, overwrite: Boolean): WorkspaceWriteResult {
            error("unused")
        }

        override suspend fun replaceText(
            relativePath: String,
            find: String,
            replaceWith: String,
            expectedOccurrences: Int?
        ): WorkspaceReplaceResult {
            error("unused")
        }
    }

    private class FakeTool(
        override val name: String,
        override val accessCategory: ToolAccessCategory
    ) : AgentTool {
        override val description: String = name
        override val parametersSchema: JsonObject = buildJsonObject { }

        override suspend fun execute(
            arguments: JsonObject,
            config: AgentConfig,
            runContext: AgentRunContext
        ): String = name
    }
}
