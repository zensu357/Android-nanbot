package com.example.nanobot.core.ai

import com.example.nanobot.core.memory.VisualMemoryExtractor
import com.example.nanobot.core.mcp.McpRefreshResult
import com.example.nanobot.core.mcp.McpRegistry
import com.example.nanobot.core.mcp.McpServerDefinition
import com.example.nanobot.core.mcp.McpToolDescriptor
import com.example.nanobot.core.mcp.McpToolDiscoverySnapshot
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.model.Attachment
import com.example.nanobot.core.model.AttachmentType
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.MemoryFact
import com.example.nanobot.core.model.MemorySummary
import com.example.nanobot.core.model.MessageRole
import com.example.nanobot.core.tools.ToolAccessPolicy
import com.example.nanobot.core.tools.ToolRegistry
import com.example.nanobot.core.tools.ToolValidator
import com.example.nanobot.core.workspace.WorkspaceEntry
import com.example.nanobot.core.workspace.WorkspaceFileContent
import com.example.nanobot.core.workspace.WorkspaceReplaceResult
import com.example.nanobot.core.workspace.WorkspaceRoot
import com.example.nanobot.core.workspace.WorkspaceSearchHit
import com.example.nanobot.core.workspace.WorkspaceWriteResult
import com.example.nanobot.domain.repository.MemoryRepository
import com.example.nanobot.domain.repository.WorkspaceRepository
import com.example.nanobot.testutil.FakeSkillRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive

class PromptComposerAttachmentReplayTest {
    @Test
    fun composeOmitsHistoricalAttachmentsFromReplayButKeepsCurrentOnes() = runTest {
        val composer = PromptComposer(
            systemPromptBuilder = SystemPromptBuilder(
                PromptPresetCatalog(),
                FakeSkillRepository(),
                ToolAccessPolicy(),
                SkillSelector(),
                SkillPromptAssembler(),
                ContextBudgetPlanner()
            ),
            runtimeContextBuilder = RuntimeContextBuilder(
                workspaceRepository = FakeWorkspaceRepository(),
                toolRegistry = ToolRegistry(ToolValidator(), ToolAccessPolicy()),
                skillRepository = FakeSkillRepository(),
                mcpRegistry = FakeMcpRegistry()
            ),
            memoryConsolidator = MemoryConsolidator(
                FakeMemoryRepository(),
                FakeChatRepository(),
                MemoryPromptBuilder(),
                VisualMemoryExtractor(FakeChatRepository())
            ),
            memoryExposurePlanner = MemoryExposurePlanner(FakeMemoryRepository()),
            historyExposurePlanner = HistoryExposurePlanner(),
            promptDiagnosticsStore = PromptDiagnosticsStore()
        )

        val historicalAttachment = Attachment(
            id = "att-1",
            type = AttachmentType.IMAGE,
            displayName = "clipboard.png",
            mimeType = "image/png",
            sizeBytes = 128,
            localPath = "attachments/images/clipboard.png"
        )
        val currentAttachment = historicalAttachment.copy(id = "att-2", displayName = "fresh.png")

        val messages = composer.compose(
            runContext = AgentRunContext.root("session-1"),
            config = AgentConfig(maxTokens = 512, model = "moonshot-v1"),
            history = listOf(
                ChatMessage(
                    sessionId = "session-1",
                    role = MessageRole.USER,
                    content = "Look at this chart",
                    attachments = listOf(historicalAttachment)
                ),
                ChatMessage(
                    sessionId = "session-1",
                    role = MessageRole.ASSISTANT,
                    content = "I can inspect it."
                )
            ),
            latestUserInput = "Search the latest SSE index.",
            latestAttachments = listOf(currentAttachment)
        )

        val replayedHistory = messages[1]
        assertTrue(replayedHistory.attachments.isEmpty())
        assertTrue(replayedHistory.content?.jsonPrimitive?.content.orEmpty().contains("prior attachment was omitted from replay", ignoreCase = true))
        assertTrue(!replayedHistory.content?.jsonPrimitive?.content.orEmpty().contains("clipboard", ignoreCase = true))

        val latestUserMessage = messages.last()
        assertEquals(1, latestUserMessage.attachments.size)
        assertEquals("fresh.png", latestUserMessage.attachments.single().fileName)
    }

    @Test
    fun composeReplaysHistoricalImageAttachmentsWhenProviderSupportsVision() = runTest {
        val composer = PromptComposer(
            systemPromptBuilder = SystemPromptBuilder(
                PromptPresetCatalog(),
                FakeSkillRepository(),
                ToolAccessPolicy(),
                SkillSelector(),
                SkillPromptAssembler(),
                ContextBudgetPlanner()
            ),
            runtimeContextBuilder = RuntimeContextBuilder(
                workspaceRepository = FakeWorkspaceRepository(),
                toolRegistry = ToolRegistry(ToolValidator(), ToolAccessPolicy()),
                skillRepository = FakeSkillRepository(),
                mcpRegistry = FakeMcpRegistry()
            ),
            memoryConsolidator = MemoryConsolidator(
                FakeMemoryRepository(),
                FakeChatRepository(),
                MemoryPromptBuilder(),
                VisualMemoryExtractor(FakeChatRepository())
            ),
            memoryExposurePlanner = MemoryExposurePlanner(FakeMemoryRepository()),
            historyExposurePlanner = HistoryExposurePlanner(),
            promptDiagnosticsStore = PromptDiagnosticsStore()
        )

        val historicalAttachment = Attachment(
            id = "att-1",
            type = AttachmentType.IMAGE,
            displayName = "clipboard.png",
            mimeType = "image/png",
            sizeBytes = 128,
            localPath = "attachments/images/clipboard.png"
        )

        val messages = composer.compose(
            runContext = AgentRunContext.root("session-1", supportsVision = true),
            config = AgentConfig(model = "gpt-4o-mini", maxTokens = 512),
            history = listOf(
                ChatMessage(
                    sessionId = "session-1",
                    role = MessageRole.USER,
                    content = "Look at this chart",
                    attachments = listOf(historicalAttachment)
                )
            ),
            latestUserInput = "Continue",
            latestAttachments = emptyList()
        )

        val replayedHistory = messages[1]
        assertTrue(replayedHistory.attachments.isNotEmpty())
        assertEquals("Look at this chart", replayedHistory.content?.jsonPrimitive?.content)
    }

    private class FakeMemoryRepository : MemoryRepository {
        override fun observeFacts(): Flow<List<MemoryFact>> = flowOf(emptyList())
        override fun observeSummaries(): Flow<List<MemorySummary>> = flowOf(emptyList())
        override suspend fun getFacts(): List<MemoryFact> = emptyList()
        override suspend fun getFactsForSession(sessionId: String): List<MemoryFact> = emptyList()
        override suspend fun getAllSummaries(): List<MemorySummary> = emptyList()
        override suspend fun getFactsForQuery(query: String): List<MemoryFact> = emptyList()
        override suspend fun observeSummariesSnapshot(): List<MemorySummary> = emptyList()
        override suspend fun getSummaryForSession(sessionId: String): MemorySummary? = null
        override suspend fun deleteFact(factId: String) = Unit
        override suspend fun deleteSummary(sessionId: String) = Unit
        override suspend fun pruneFacts(maxFacts: Int) = Unit
        override suspend fun upsertFact(fact: MemoryFact) = Unit
        override suspend fun upsertSummary(summary: MemorySummary) = Unit
    }

    private class FakeWorkspaceRepository : WorkspaceRepository {
        override suspend fun getWorkspaceRoot(): WorkspaceRoot = WorkspaceRoot("workspace:/sandbox", true, "read_write")
        override suspend fun list(relativePath: String, limit: Int): List<WorkspaceEntry> = emptyList()
        override suspend fun readText(relativePath: String, maxChars: Int): WorkspaceFileContent = error("unused")
        override suspend fun search(query: String, relativePath: String, limit: Int): List<WorkspaceSearchHit> = emptyList()
        override suspend fun writeText(relativePath: String, content: String, overwrite: Boolean): WorkspaceWriteResult = error("unused")
        override suspend fun replaceText(relativePath: String, find: String, replaceWith: String, expectedOccurrences: Int?): WorkspaceReplaceResult = error("unused")
    }

    private class FakeMcpRegistry : McpRegistry {
        override fun observeServers() = throw UnsupportedOperationException()
        override fun observeCachedTools() = throw UnsupportedOperationException()
        override suspend fun listEnabledServers(): List<McpServerDefinition> = emptyList()
        override suspend fun listEnabledTools(): List<McpToolDescriptor> = emptyList()
        override suspend fun refreshTools(): McpRefreshResult = McpRefreshResult(0, 0, 0, emptyList(), 0, 0, 0)
        override suspend fun saveServers(servers: List<McpServerDefinition>) = Unit
        override suspend fun callTool(toolName: String, arguments: JsonObject): String = toolName
        override suspend fun getDiscoverySnapshot(): McpToolDiscoverySnapshot = McpToolDiscoverySnapshot(emptyList(), emptyList())
    }

    private class FakeChatRepository : com.example.nanobot.domain.repository.ChatRepository {
        override suspend fun completeChat(request: com.example.nanobot.core.model.LlmChatRequest, config: AgentConfig) =
            com.example.nanobot.core.model.ProviderChatResult(content = "{}")
    }
}
