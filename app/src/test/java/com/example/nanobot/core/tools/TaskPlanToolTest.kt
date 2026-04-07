package com.example.nanobot.core.tools

import com.example.nanobot.core.ai.AgentTurnRunner
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.model.AgentProgressEvent
import com.example.nanobot.core.model.AgentTurnResult
import com.example.nanobot.core.model.Attachment
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.ChatSession
import com.example.nanobot.core.model.LlmChatRequest
import com.example.nanobot.core.model.MessageRole
import com.example.nanobot.core.model.ProviderChatResult
import com.example.nanobot.core.subagent.ParallelDispatcher
import com.example.nanobot.core.subagent.ResultAggregator
import com.example.nanobot.core.subagent.SubagentCoordinator
import com.example.nanobot.core.taskplan.StepStatus
import com.example.nanobot.core.taskplan.TaskExecutionEngine
import com.example.nanobot.core.taskplan.TaskPlan
import com.example.nanobot.core.taskplan.TaskPlanner
import com.example.nanobot.core.taskplan.TaskPlanStatus
import com.example.nanobot.core.taskplan.TaskStateStore
import com.example.nanobot.core.taskplan.TaskStep
import com.example.nanobot.core.tools.impl.TaskPlanTool
import com.example.nanobot.data.mapper.toEntity
import com.example.nanobot.domain.repository.ChatRepository
import com.example.nanobot.domain.repository.SessionRepository
import javax.inject.Provider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TaskPlanToolTest {
    @Test
    fun planActionFormatsStoredPlan() = runTest {
        val dao = FakeTaskPlanDao()
        val store = TaskStateStore(dao)
        val sessionRepository = FakeSessionRepository()
        val tool = TaskPlanTool(
            planner = TaskPlanner(
                StaticChatRepository(
                    """
                    {
                      "complexity": 4,
                      "title": "Refactor Plan",
                      "steps": [
                        {
                          "description": "Inspect",
                          "depends_on": [],
                          "delegatable": false,
                          "complexity": "LOW"
                        }
                      ]
                    }
                    """.trimIndent()
                )
            ),
            engine = createExecutionEngine(sessionRepository, store, SuccessfulAgentTurnRunner()),
            store = store
        )

        val result = tool.execute(
            arguments = buildJsonObject {
                put("action", "plan")
                put("goal", "Refactor the module")
            },
            config = AgentConfig(),
            runContext = AgentRunContext.root("session-1")
        )

        assertTrue(result.contains("## Task Plan: Refactor Plan"))
        assertEquals("Refactor Plan", store.activeForSession("session-1")?.title)
    }

    @Test
    fun resumeActionRunsPlanAndReturnsUpdatedStatus() = runTest {
        val initialPlan = TaskPlan(
            id = "plan-1",
            sessionId = "session-1",
            title = "Resume Plan",
            originalGoal = "Goal",
            steps = listOf(TaskStep(index = 0, description = "Inspect")),
            status = TaskPlanStatus.PENDING
        )
        val dao = FakeTaskPlanDao(initialPlan)
        val store = TaskStateStore(dao)
        val sessionRepository = FakeSessionRepository()
        val tool = TaskPlanTool(
            planner = TaskPlanner(StaticChatRepository("{\"complexity\":1,\"steps\":[]}")),
            engine = createExecutionEngine(sessionRepository, store, SuccessfulAgentTurnRunner()),
            store = store
        )

        val result = tool.execute(
            arguments = buildJsonObject {
                put("action", "resume")
                put("plan_id", "plan-1")
            },
            config = AgentConfig(),
            runContext = AgentRunContext.root("session-1")
        )

        assertTrue(result.contains("completed"))
        assertTrue(result.contains("[done] Inspect"))
        assertEquals(TaskPlanStatus.COMPLETED, store.load("plan-1")?.status)
    }

    @Test
    fun cancelActionMarksPlanCancelled() = runTest {
        val initialPlan = TaskPlan(
            id = "plan-2",
            sessionId = "session-1",
            title = "Cancel Plan",
            originalGoal = "Goal",
            steps = listOf(TaskStep(index = 0, description = "Inspect")),
            status = TaskPlanStatus.IN_PROGRESS
        )
        val dao = FakeTaskPlanDao(initialPlan)
        val store = TaskStateStore(dao)
        val sessionRepository = FakeSessionRepository()
        val tool = TaskPlanTool(
            planner = TaskPlanner(StaticChatRepository("{\"complexity\":1,\"steps\":[]}")),
            engine = createExecutionEngine(sessionRepository, store, SuccessfulAgentTurnRunner()),
            store = store
        )

        val result = tool.execute(
            arguments = buildJsonObject {
                put("action", "cancel")
                put("plan_id", "plan-2")
            },
            config = AgentConfig(),
            runContext = AgentRunContext.root("session-1")
        )

        assertEquals(TaskPlanStatus.CANCELLED, store.load("plan-2")?.status)
        assertTrue(result.contains("cancelled"))
    }

    private fun createExecutionEngine(
        sessionRepository: SessionRepository,
        store: TaskStateStore,
        runner: AgentTurnRunner
    ): TaskExecutionEngine {
        return TaskExecutionEngine(
            agentTurnRunnerProvider = Provider { runner },
            parallelDispatcher = ParallelDispatcher(
                subagentCoordinator = SubagentCoordinator(sessionRepository, Provider { runner }),
                resultAggregator = ResultAggregator()
            ),
            taskStateStore = store,
            sessionRepository = sessionRepository
        )
    }

    private class FakeTaskPlanDao(
        initial: TaskPlan? = null
    ) : com.example.nanobot.core.database.dao.TaskPlanDao {
        private val plans = linkedMapOf<String, com.example.nanobot.core.database.entity.TaskPlanEntity>()

        init {
            if (initial != null) {
                plans[initial.id] = initial.toEntity()
            }
        }

        override suspend fun upsert(entity: com.example.nanobot.core.database.entity.TaskPlanEntity) {
            plans[entity.id] = entity
        }

        override suspend fun getById(id: String): com.example.nanobot.core.database.entity.TaskPlanEntity? = plans[id]

        override suspend fun getBySession(sessionId: String): List<com.example.nanobot.core.database.entity.TaskPlanEntity> =
            plans.values.filter { it.sessionId == sessionId }

        override suspend fun getActiveBySession(sessionId: String): com.example.nanobot.core.database.entity.TaskPlanEntity? =
            plans.values.firstOrNull { it.sessionId == sessionId && (it.status == TaskPlanStatus.PENDING.name || it.status == TaskPlanStatus.IN_PROGRESS.name) }
    }

    private class StaticChatRepository(
        private val content: String
    ) : ChatRepository {
        override suspend fun completeChat(
            request: LlmChatRequest,
            config: AgentConfig
        ): ProviderChatResult {
            return ProviderChatResult(content = content)
        }
    }

    private class SuccessfulAgentTurnRunner : AgentTurnRunner {
        override suspend fun runTurn(
            sessionId: String,
            history: List<ChatMessage>,
            userInput: String,
            attachments: List<Attachment>,
            config: AgentConfig,
            runContext: AgentRunContext,
            onProgress: suspend (AgentProgressEvent) -> Unit
        ): AgentTurnResult {
            return AgentTurnResult(
                newMessages = listOf(ChatMessage(sessionId = sessionId, role = MessageRole.ASSISTANT, content = "Done")),
                finalResponse = ChatMessage(sessionId = sessionId, role = MessageRole.ASSISTANT, content = "Done")
            )
        }
    }

    private class FakeSessionRepository : SessionRepository {
        private val sessions = mutableListOf(ChatSession(id = "session-1", title = "Session"))
        private val messages = mutableMapOf<String, MutableList<ChatMessage>>()

        override fun observeCurrentSession(): Flow<ChatSession?> = flowOf(sessions.firstOrNull())
        override fun observeSessions(): Flow<List<ChatSession>> = flowOf(sessions)
        override fun observeMessages(sessionId: String): Flow<List<ChatMessage>> = flowOf(messages[sessionId].orEmpty())
        override suspend fun observeSessionsSnapshot(): List<ChatSession> = sessions.toList()
        override suspend fun getOrCreateCurrentSession(): ChatSession = sessions.first()
        override suspend fun getSessionByTitle(title: String): ChatSession? = sessions.firstOrNull { it.title == title }
        override suspend fun createSession(title: String, makeCurrent: Boolean, parentSessionId: String?, subagentDepth: Int): ChatSession {
            val session = ChatSession(title = title, parentSessionId = parentSessionId, subagentDepth = subagentDepth)
            sessions += session
            return session
        }
        override suspend fun upsertSession(session: ChatSession, makeCurrent: Boolean): ChatSession = session
        override suspend fun selectSession(sessionId: String) = Unit
        override suspend fun deleteSession(sessionId: String) = Unit
        override suspend fun deleteSessionsOlderThan(cutoffMillis: Long) = Unit
        override suspend fun getMessages(sessionId: String): List<ChatMessage> = messages[sessionId].orEmpty()
        override suspend fun getHistoryForModel(sessionId: String, maxMessages: Int): List<ChatMessage> = messages[sessionId].orEmpty()
        override suspend fun saveMessage(message: ChatMessage) {
            messages.getOrPut(message.sessionId) { mutableListOf() } += message
        }
        override suspend fun touchSession(session: ChatSession, makeCurrent: Boolean) = Unit
    }
}
