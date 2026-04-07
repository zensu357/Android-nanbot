package com.example.nanobot.core.ai

import com.example.nanobot.core.ai.provider.ProviderRegistry
import com.example.nanobot.core.learning.StrategyOptimizer
import com.example.nanobot.core.memory.VisualMemoryExtractor
import com.example.nanobot.core.mcp.McpRefreshResult
import com.example.nanobot.core.mcp.McpRegistry
import com.example.nanobot.core.mcp.McpServerDefinition
import com.example.nanobot.core.mcp.McpToolDescriptor
import com.example.nanobot.core.mcp.McpToolDiscoverySnapshot
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.MemoryFact
import com.example.nanobot.core.model.MemorySummary
import com.example.nanobot.core.model.MessageRole
import com.example.nanobot.core.taskplan.TaskStateStore
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject

class PromptComposerDiagnosticsTest {
    @Test
    fun composePublishesPromptDiagnosticsSnapshot() = runTest {
        val diagnosticsStore = PromptDiagnosticsStore()
        val memoryRepository = FakeMemoryRepository()
        val composer = PromptComposer(
            systemPromptBuilder = SystemPromptBuilder(
                PromptPresetCatalog(),
                FakeSkillRepository(),
                ToolAccessPolicy(),
                SkillSelector(),
                SkillPromptAssembler(),
                ContextBudgetPlanner(),
                StrategyOptimizer(com.example.nanobot.core.learning.BehaviorAnalyzer(FakeBehaviorEventDao()))
            ),
            runtimeContextBuilder = RuntimeContextBuilder(
                workspaceRepository = FakeWorkspaceRepository(),
                toolRegistry = ToolRegistry(ToolValidator(), ToolAccessPolicy()),
                skillRepository = FakeSkillRepository(),
                mcpRegistry = FakeMcpRegistry(),
                taskStateStore = TaskStateStore(FakeTaskPlanDao())
            ),
            memoryConsolidator = MemoryConsolidator(
                memoryRepository,
                FakeChatRepository(),
                MemoryPromptBuilder(),
                VisualMemoryExtractor(FakeChatRepository())
            ),
            memoryExposurePlanner = MemoryExposurePlanner(memoryRepository),
            historyExposurePlanner = HistoryExposurePlanner(),
            promptDiagnosticsStore = diagnosticsStore
        )

        composer.compose(
            runContext = AgentRunContext.root("session-1"),
            config = AgentConfig(enabledSkillIds = listOf("planner_mode"), maxTokens = 256),
            history = listOf(
                ChatMessage(sessionId = "session-1", role = MessageRole.USER, content = "old message " + "A".repeat(600)),
                ChatMessage(sessionId = "session-1", role = MessageRole.ASSISTANT, content = "old reply")
            ),
            latestUserInput = "Please debug the workspace context."
        )

        val snapshot = diagnosticsStore.snapshot.value
        assertNotNull(snapshot)
        assertTrue(snapshot.systemPromptChars > 0)
        assertEquals(true, snapshot.runtimeDiagnosticsEnabled)
        assertEquals(2, snapshot.historyOriginalCount)
        assertTrue(snapshot.historyKeptCount >= 1)
        assertTrue(snapshot.memoryScratchEntryCount >= 1)
    }

    private class FakeMemoryRepository : MemoryRepository {
        private val facts = listOf(
            MemoryFact(
                id = "fact-1",
                fact = "The user is working on workspace debugging.",
                sourceSessionId = "session-1",
                createdAt = 1L,
                updatedAt = 2L,
                confidence = 0.8f
            )
        )
        private val summary = MemorySummary(
            sessionId = "session-1",
            summary = "Current focus is workspace debugging.",
            updatedAt = 3L,
            sourceMessageCount = 2,
            confidence = 0.85f
        )

        override fun observeFacts(): Flow<List<MemoryFact>> = flowOf(facts)
        override fun observeSummaries(): Flow<List<MemorySummary>> = flowOf(listOf(summary))
        override suspend fun getFacts(): List<MemoryFact> = facts
        override suspend fun getFactsForSession(sessionId: String): List<MemoryFact> = facts.filter { it.sourceSessionId == sessionId }
        override suspend fun getAllSummaries(): List<MemorySummary> = listOf(summary)
        override suspend fun getFactsForQuery(query: String): List<MemoryFact> = facts
        override suspend fun observeSummariesSnapshot(): List<MemorySummary> = listOf(summary)
        override suspend fun getSummaryForSession(sessionId: String): MemorySummary? = summary.takeIf { it.sessionId == sessionId }
        override suspend fun deleteFact(factId: String) = Unit
        override suspend fun deleteSummary(sessionId: String) = Unit
        override suspend fun pruneFacts(maxFacts: Int) = Unit
        override suspend fun upsertFact(fact: MemoryFact) = Unit
        override suspend fun upsertSummary(summary: MemorySummary) = Unit
    }

    private class FakeTaskPlanDao : com.example.nanobot.core.database.dao.TaskPlanDao {
        override suspend fun upsert(entity: com.example.nanobot.core.database.entity.TaskPlanEntity) = Unit
        override suspend fun getById(id: String): com.example.nanobot.core.database.entity.TaskPlanEntity? = null
        override suspend fun getBySession(sessionId: String): List<com.example.nanobot.core.database.entity.TaskPlanEntity> = emptyList()
        override suspend fun getActiveBySession(sessionId: String): com.example.nanobot.core.database.entity.TaskPlanEntity? = null
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
