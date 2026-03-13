package com.example.nanobot.core.ai

import com.example.nanobot.core.mcp.McpRefreshResult
import com.example.nanobot.core.mcp.McpRegistry
import com.example.nanobot.core.mcp.McpServerDefinition
import com.example.nanobot.core.mcp.McpToolDescriptor
import com.example.nanobot.core.mcp.McpToolDiscoverySnapshot
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.Attachment
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.ChatSession
import com.example.nanobot.core.model.LlmChatRequest
import com.example.nanobot.core.model.MemoryFact
import com.example.nanobot.core.model.MemorySummary
import com.example.nanobot.core.model.MessageRole
import com.example.nanobot.core.model.ProviderChatResult
import com.example.nanobot.core.model.ToolCallRequest
import com.example.nanobot.core.subagent.SubagentCoordinator
import com.example.nanobot.core.tools.AgentTool
import com.example.nanobot.core.tools.ToolAccessCategory
import com.example.nanobot.core.tools.ToolAccessPolicy
import com.example.nanobot.core.tools.ToolRegistry
import com.example.nanobot.core.tools.ToolValidator
import com.example.nanobot.core.tools.impl.DelegateTaskTool
import com.example.nanobot.core.skills.ActivatedSkillSessionStore
import com.example.nanobot.domain.repository.ChatRepository
import com.example.nanobot.domain.repository.MemoryRepository
import com.example.nanobot.domain.repository.SessionRepository
import com.example.nanobot.domain.usecase.SendMessageUseCase
import com.example.nanobot.testutil.FakeSkillRepository
import javax.inject.Provider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class AgentOrchestratorIntegrationTest {
    @Test
    fun delegateTaskCreatesChildSessionReturnsSummaryAndKeepsParentActive() = runTest {
        val sessionRepository = FakeSessionRepository()
        val chatRepository = DelegatingChatRepository()
        val skillRepository = FakeSkillRepository()
        val mcpRegistry = EmptyMcpRegistry()
        lateinit var orchestrator: AgentOrchestrator
        val subagentCoordinator = SubagentCoordinator(sessionRepository, Provider { orchestrator })
        val toolRegistry = ToolRegistry(ToolValidator(), ToolAccessPolicy(), mcpRegistry).apply {
            register(DelegateTaskTool(subagentCoordinator))
        }
        val promptComposer = PromptComposer(
            systemPromptBuilder = SystemPromptBuilder(PromptPresetCatalog(), skillRepository, ToolAccessPolicy(), SkillSelector(), SkillPromptAssembler(), ContextBudgetPlanner()),
            runtimeContextBuilder = RuntimeContextBuilder(FakeWorkspaceRepository(), toolRegistry, skillRepository, mcpRegistry),
            memoryConsolidator = MemoryConsolidator(FakeMemoryRepository(), chatRepository, MemoryPromptBuilder()),
            memoryExposurePlanner = MemoryExposurePlanner(FakeMemoryRepository()),
            historyExposurePlanner = HistoryExposurePlanner(),
            promptDiagnosticsStore = PromptDiagnosticsStore()
        )
        orchestrator = AgentOrchestrator(promptComposer, ToolLoopExecutor(chatRepository, toolRegistry))
        val useCase = SendMessageUseCase(sessionRepository, orchestrator, skillRepository, ActivatedSkillSessionStore())

        val messages = useCase(
            input = "Please delegate a focused report task.",
            config = AgentConfig(enableTools = true)
        )

        val childSession = sessionRepository.createdSessions.singleOrNull { it.parentSessionId == "parent-session" }
        assertNotNull(childSession)
        assertEquals(1, childSession.subagentDepth)
        assertEquals("parent-session", sessionRepository.currentSessionId)
        assertFalse(sessionRepository.selectedSessionIds.contains(childSession.id))
        assertTrue(messages.any { it.role == MessageRole.TOOL && it.content.orEmpty().contains("Subagent Session ID: ${childSession.id}") })
        assertTrue(messages.any { it.role == MessageRole.TOOL && it.content.orEmpty().contains("Summary: Child completed the delegated report") })
        assertTrue(sessionRepository.messagesBySession(childSession.id).any { it.role == MessageRole.ASSISTANT && it.content.orEmpty().contains("Child completed the delegated report") })
    }

    @Test
    fun delegateTaskCanWriteWorkspaceArtifactAndReturnArtifactSummaryToParent() = runTest {
        val sessionRepository = FakeSessionRepository()
        val chatRepository = DelegateAndWriteChatRepository()
        val skillRepository = FakeSkillRepository()
        val mcpRegistry = EmptyMcpRegistry()
        lateinit var orchestrator: AgentOrchestrator
        val subagentCoordinator = SubagentCoordinator(sessionRepository, Provider { orchestrator })
        val toolRegistry = ToolRegistry(ToolValidator(), ToolAccessPolicy(), mcpRegistry).apply {
            register(DelegateTaskTool(subagentCoordinator))
            register(
                StaticTool(
                    name = "write_file",
                    accessCategory = ToolAccessCategory.WORKSPACE_SIDE_EFFECT,
                    result = "Workspace file written.\nPath: reports/delegated-report.txt"
                )
            )
        }
        val promptComposer = PromptComposer(
            systemPromptBuilder = SystemPromptBuilder(PromptPresetCatalog(), skillRepository, ToolAccessPolicy(), SkillSelector(), SkillPromptAssembler(), ContextBudgetPlanner()),
            runtimeContextBuilder = RuntimeContextBuilder(FakeWorkspaceRepository(), toolRegistry, skillRepository, mcpRegistry),
            memoryConsolidator = MemoryConsolidator(FakeMemoryRepository(), chatRepository, MemoryPromptBuilder()),
            memoryExposurePlanner = MemoryExposurePlanner(FakeMemoryRepository()),
            historyExposurePlanner = HistoryExposurePlanner(),
            promptDiagnosticsStore = PromptDiagnosticsStore()
        )
        orchestrator = AgentOrchestrator(promptComposer, ToolLoopExecutor(chatRepository, toolRegistry))
        val useCase = SendMessageUseCase(sessionRepository, orchestrator, skillRepository, ActivatedSkillSessionStore())

        val messages = useCase(
            input = "Please delegate a report and save it in the workspace.",
            config = AgentConfig(enableTools = true)
        )

        val childSession = sessionRepository.createdSessions.singleOrNull { it.parentSessionId == "parent-session" }
        assertNotNull(childSession)
        val childMessages = sessionRepository.messagesBySession(childSession.id)
        assertTrue(childMessages.any { it.role == MessageRole.TOOL && it.toolName == "write_file" && it.content.orEmpty().contains("reports/delegated-report.txt") })
        assertTrue(messages.any { it.role == MessageRole.TOOL && it.toolName == "delegate_task" && it.content.orEmpty().contains("Artifacts: reports/delegated-report.txt") })
        assertEquals("parent-session", sessionRepository.currentSessionId)
    }

    private class DelegatingChatRepository : ChatRepository {
        override suspend fun completeChat(request: LlmChatRequest, config: AgentConfig): ProviderChatResult {
            val latestMessage = request.messages.last()
            val latestText = latestMessage.content?.jsonPrimitive?.contentOrNull.orEmpty()
            return when {
                latestMessage.role == "user" && latestText.contains("Please delegate a focused report task") -> {
                    ProviderChatResult(
                        content = null,
                        toolCalls = listOf(
                            ToolCallRequest(
                                id = "delegate-1",
                                name = "delegate_task",
                                arguments = buildJsonObject {
                                    put("task", "Inspect workspace and prepare a concise report")
                                    put("title", "Delegated Report")
                                }
                            )
                        )
                    )
                }

                latestMessage.role == "user" && latestText.contains("Inspect workspace and prepare a concise report") -> {
                    ProviderChatResult(content = "Child completed the delegated report and saved the findings.")
                }

                latestMessage.role == "tool" && latestMessage.name == "delegate_task" -> {
                    ProviderChatResult(content = "I reviewed the child summary and kept the parent session active.")
                }

                else -> ProviderChatResult(content = "Unhandled test branch.")
            }
        }
    }

    private class DelegateAndWriteChatRepository : ChatRepository {
        override suspend fun completeChat(request: LlmChatRequest, config: AgentConfig): ProviderChatResult {
            val latestMessage = request.messages.last()
            val latestText = latestMessage.content?.jsonPrimitive?.contentOrNull.orEmpty()
            return when {
                latestMessage.role == "user" && latestText.contains("Please delegate a report and save it in the workspace") -> {
                    ProviderChatResult(
                        content = null,
                        toolCalls = listOf(
                            ToolCallRequest(
                                id = "delegate-2",
                                name = "delegate_task",
                                arguments = buildJsonObject {
                                    put("task", "Write a delegated report into the workspace")
                                    put("title", "Delegated Artifact Report")
                                }
                            )
                        )
                    )
                }

                latestMessage.role == "user" && latestText.contains("Write a delegated report into the workspace") -> {
                    ProviderChatResult(
                        content = null,
                        toolCalls = listOf(
                            ToolCallRequest(
                                id = "child-write-1",
                                name = "write_file",
                                arguments = buildJsonObject {
                                    put("relativePath", "reports/delegated-report.txt")
                                    put("content", "Delegated report")
                                    put("overwrite", true)
                                }
                            )
                        )
                    )
                }

                latestMessage.role == "tool" && latestMessage.name == "write_file" -> {
                    ProviderChatResult(content = "Child completed the delegated report and saved the findings.")
                }

                latestMessage.role == "tool" && latestMessage.name == "delegate_task" -> {
                    ProviderChatResult(content = "The delegated report has been saved and summarized.")
                }

                else -> ProviderChatResult(content = "Unhandled test branch.")
            }
        }
    }

    private class StaticTool(
        override val name: String,
        override val accessCategory: ToolAccessCategory,
        private val result: String
    ) : AgentTool {
        override val description: String = name
        override val parametersSchema: JsonObject = buildJsonObject { put("type", "object") }

        override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: com.example.nanobot.core.model.AgentRunContext): String {
            return result
        }
    }

    private class FakeSessionRepository : SessionRepository {
        private val currentSession = MutableStateFlow(
            ChatSession(id = "parent-session", title = "Parent Session")
        )
        private val sessions = mutableListOf(currentSession.value)
        private val messages = mutableMapOf<String, MutableList<ChatMessage>>()
        val createdSessions = mutableListOf<ChatSession>()
        val selectedSessionIds = mutableListOf<String>()
        val currentSessionId: String get() = currentSession.value.id

        override fun observeCurrentSession(): Flow<ChatSession?> = currentSession

        override fun observeSessions(): Flow<List<ChatSession>> = flowOf(sessions.toList())

        override fun observeMessages(sessionId: String): Flow<List<ChatMessage>> = flowOf(messages[sessionId].orEmpty())

        override suspend fun observeSessionsSnapshot(): List<ChatSession> = sessions.toList()

        override suspend fun getOrCreateCurrentSession(): ChatSession = currentSession.value

        override suspend fun getSessionByTitle(title: String): ChatSession? = sessions.firstOrNull { it.title == title }

        override suspend fun createSession(
            title: String,
            makeCurrent: Boolean,
            parentSessionId: String?,
            subagentDepth: Int
        ): ChatSession {
            val session = ChatSession(
                id = "session-${sessions.size + 1}",
                title = title,
                parentSessionId = parentSessionId,
                subagentDepth = subagentDepth
            )
            sessions += session
            createdSessions += session
            if (makeCurrent) {
                currentSession.value = session
                selectedSessionIds += session.id
            }
            return session
        }

        override suspend fun upsertSession(session: ChatSession, makeCurrent: Boolean): ChatSession {
            sessions.removeAll { it.id == session.id }
            sessions += session
            if (makeCurrent) {
                currentSession.value = session
                selectedSessionIds += session.id
            }
            return session
        }

        override suspend fun selectSession(sessionId: String) {
            sessions.firstOrNull { it.id == sessionId }?.let {
                currentSession.value = it
                selectedSessionIds += sessionId
            }
        }

        override suspend fun deleteSession(sessionId: String) {
            sessions.removeAll { it.id == sessionId }
            messages.remove(sessionId)
            if (currentSession.value?.id == sessionId) {
                currentSession.value = sessions.firstOrNull() ?: ChatSession(title = "New Chat")
            }
        }

        override suspend fun deleteSessionsOlderThan(cutoffMillis: Long) {
            val targetIds = sessions.filter { it.updatedAt < cutoffMillis }.map { it.id }.toSet()
            sessions.removeAll { it.id in targetIds }
            targetIds.forEach(messages::remove)
            if (currentSession.value.id in targetIds) {
                currentSession.value = sessions.firstOrNull() ?: ChatSession(title = "New Chat")
            }
        }

        override suspend fun getMessages(sessionId: String): List<ChatMessage> = messages[sessionId].orEmpty()

        override suspend fun getHistoryForModel(sessionId: String, maxMessages: Int): List<ChatMessage> =
            messages[sessionId].orEmpty().takeLast(maxMessages)

        override suspend fun saveMessage(message: ChatMessage) {
            messages.getOrPut(message.sessionId) { mutableListOf() } += message
        }

        override suspend fun touchSession(session: ChatSession, makeCurrent: Boolean) {
            upsertSession(session.copy(updatedAt = System.currentTimeMillis()), makeCurrent)
        }

        fun messagesBySession(sessionId: String): List<ChatMessage> = messages[sessionId].orEmpty()
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

    private class FakeWorkspaceRepository : com.example.nanobot.domain.repository.WorkspaceRepository {
        override suspend fun getWorkspaceRoot(): com.example.nanobot.core.workspace.WorkspaceRoot {
            return com.example.nanobot.core.workspace.WorkspaceRoot(
                rootId = "workspace:/sandbox",
                isAvailable = true,
                accessMode = "read_write"
            )
        }

        override suspend fun list(relativePath: String, limit: Int): List<com.example.nanobot.core.workspace.WorkspaceEntry> = emptyList()

        override suspend fun readText(relativePath: String, maxChars: Int): com.example.nanobot.core.workspace.WorkspaceFileContent {
            error("unused")
        }

        override suspend fun search(query: String, relativePath: String, limit: Int): List<com.example.nanobot.core.workspace.WorkspaceSearchHit> = emptyList()

        override suspend fun writeText(relativePath: String, content: String, overwrite: Boolean): com.example.nanobot.core.workspace.WorkspaceWriteResult {
            error("unused")
        }

        override suspend fun replaceText(
            relativePath: String,
            find: String,
            replaceWith: String,
            expectedOccurrences: Int?
        ): com.example.nanobot.core.workspace.WorkspaceReplaceResult {
            error("unused")
        }
    }

    private class EmptyMcpRegistry : McpRegistry {
        override fun observeServers(): Flow<List<McpServerDefinition>> = flowOf(emptyList())

        override fun observeCachedTools(): Flow<List<McpToolDescriptor>> = flowOf(emptyList())

        override suspend fun listEnabledServers(): List<McpServerDefinition> = emptyList()

        override suspend fun listEnabledTools(): List<McpToolDescriptor> = emptyList()

        override suspend fun refreshTools(): McpRefreshResult = McpRefreshResult(0, 0)

        override suspend fun saveServers(servers: List<McpServerDefinition>) = Unit

        override suspend fun callTool(toolName: String, arguments: JsonObject): String = toolName

        override suspend fun getDiscoverySnapshot(): McpToolDiscoverySnapshot {
            return McpToolDiscoverySnapshot(emptyList(), emptyList())
        }
    }
}
