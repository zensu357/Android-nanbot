package com.example.nanobot.core.taskplan

import com.example.nanobot.core.ai.AgentTurnRunner
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentProgressEvent
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.model.AgentTurnResult
import com.example.nanobot.core.model.Attachment
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.ChatSession
import com.example.nanobot.core.model.MessageRole
import com.example.nanobot.core.subagent.ParallelDispatcher
import com.example.nanobot.core.subagent.ResultAggregator
import com.example.nanobot.core.subagent.SubagentCoordinator
import com.example.nanobot.domain.repository.SessionRepository
import javax.inject.Provider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class TaskExecutionEngineTest {
    @Test
    fun retriesFailedStepThenCompletesPlan() = runTest {
        val sessionRepository = FakeSessionRepository()
        val turnRunner = FlakyAgentTurnRunner()
        val store = TaskStateStore(FakeTaskPlanDao())
        val engine = TaskExecutionEngine(
            agentTurnRunnerProvider = Provider { turnRunner },
            parallelDispatcher = ParallelDispatcher(
                subagentCoordinator = SubagentCoordinator(sessionRepository, Provider { turnRunner }),
                resultAggregator = ResultAggregator()
            ),
            taskStateStore = store,
            sessionRepository = sessionRepository
        )
        val config = AgentConfig(maxToolIterations = 10)
        val plan = TaskPlan(
            sessionId = "session-1",
            title = "Retry Plan",
            originalGoal = "Goal",
            steps = listOf(
                TaskStep(index = 0, description = "Fragile step", maxRetries = 1),
                TaskStep(index = 1, description = "Follow-up step")
            )
        )

        val result = engine.execute(plan, config, AgentRunContext.root("session-1"))

        assertEquals(TaskPlanStatus.COMPLETED, result.status)
        assertEquals(1, result.steps.first().retryCount)
        assertEquals(StepStatus.COMPLETED, result.steps.first().status)
        assertEquals(StepStatus.COMPLETED, result.steps[1].status)
    }

    @Test
    fun marksPlanFailedWhenStepExhaustsRetries() = runTest {
        val sessionRepository = FakeSessionRepository()
        val turnRunner = AlwaysFailingAgentTurnRunner()
        val stepOne = TaskStep(index = 0, description = "Fails", maxRetries = 0)
        val stepTwo = TaskStep(index = 1, description = "Depends", dependsOn = listOf(stepOne.id))
        val store = TaskStateStore(FakeTaskPlanDao())
        val engine = TaskExecutionEngine(
            agentTurnRunnerProvider = Provider { turnRunner },
            parallelDispatcher = ParallelDispatcher(
                subagentCoordinator = SubagentCoordinator(sessionRepository, Provider { turnRunner }),
                resultAggregator = ResultAggregator()
            ),
            taskStateStore = store,
            sessionRepository = sessionRepository
        )
        val plan = TaskPlan(
            sessionId = "session-1",
            title = "Dependency Plan",
            originalGoal = "Goal",
            steps = listOf(stepOne, stepTwo)
        )

        val result = engine.execute(plan, AgentConfig(), AgentRunContext.root("session-1"))

        assertEquals(TaskPlanStatus.FAILED, result.status)
        assertEquals(StepStatus.FAILED, result.steps.first().status)
    }

    @Test
    fun pausesPlanWhenStepBudgetForCurrentTurnIsReached() = runTest {
        val sessionRepository = FakeSessionRepository()
        val turnRunner = SuccessfulAgentTurnRunner()
        val store = TaskStateStore(FakeTaskPlanDao())
        val engine = TaskExecutionEngine(
            agentTurnRunnerProvider = Provider { turnRunner },
            parallelDispatcher = ParallelDispatcher(
                subagentCoordinator = SubagentCoordinator(sessionRepository, Provider { turnRunner }),
                resultAggregator = ResultAggregator()
            ),
            taskStateStore = store,
            sessionRepository = sessionRepository
        )
        val plan = TaskPlan(
            sessionId = "session-1",
            title = "Budget Plan",
            originalGoal = "Goal",
            steps = listOf(
                TaskStep(index = 0, description = "Step 1"),
                TaskStep(index = 1, description = "Step 2"),
                TaskStep(index = 2, description = "Step 3")
            )
        )

        val result = engine.execute(
            plan = plan,
            config = AgentConfig(maxToolIterations = 2),
            runContext = AgentRunContext.root("session-1")
        )

        assertEquals(TaskPlanStatus.IN_PROGRESS, result.status)
        assertEquals(StepStatus.COMPLETED, result.steps[0].status)
        assertEquals(StepStatus.PENDING, result.steps[1].status)
        assertEquals(StepStatus.PENDING, result.steps[2].status)
    }

    @Test
    fun executesSameLayerDelegatableStepsInParallelBatch() = runTest {
        val sessionRepository = FakeSessionRepository()
        val turnRunner = SuccessfulAgentTurnRunner()
        val store = TaskStateStore(FakeTaskPlanDao())
        val engine = TaskExecutionEngine(
            agentTurnRunnerProvider = Provider { turnRunner },
            parallelDispatcher = ParallelDispatcher(
                subagentCoordinator = SubagentCoordinator(sessionRepository, Provider { turnRunner }),
                resultAggregator = ResultAggregator()
            ),
            taskStateStore = store,
            sessionRepository = sessionRepository
        )
        val plan = TaskPlan(
            sessionId = "session-1",
            title = "Parallel Layer Plan",
            originalGoal = "Goal",
            steps = listOf(
                TaskStep(index = 0, description = "Parallel step 1", delegatable = true),
                TaskStep(index = 1, description = "Parallel step 2", delegatable = true),
                TaskStep(index = 2, description = "Final step")
            )
        )

        val result = engine.execute(
            plan = plan,
            config = AgentConfig(maxToolIterations = 6),
            runContext = AgentRunContext.root("session-1")
        )

        assertEquals(StepStatus.COMPLETED, result.steps[0].status)
        assertEquals(StepStatus.COMPLETED, result.steps[1].status)
        assertEquals(StepStatus.COMPLETED, result.steps[2].status)
        assertEquals(TaskPlanStatus.COMPLETED, result.status)
    }

    private class FlakyAgentTurnRunner : AgentTurnRunner {
        private var callCount = 0

        override suspend fun runTurn(
            sessionId: String,
            history: List<ChatMessage>,
            userInput: String,
            attachments: List<Attachment>,
            config: AgentConfig,
            runContext: AgentRunContext,
            onProgress: suspend (AgentProgressEvent) -> Unit
        ): AgentTurnResult {
            callCount += 1
            if (callCount == 1) {
                error("temporary failure")
            }
            return AgentTurnResult(
                newMessages = listOf(ChatMessage(sessionId = sessionId, role = MessageRole.ASSISTANT, content = "Completed")),
                finalResponse = ChatMessage(sessionId = sessionId, role = MessageRole.ASSISTANT, content = "Completed")
            )
        }
    }

    private class AlwaysFailingAgentTurnRunner : AgentTurnRunner {
        override suspend fun runTurn(
            sessionId: String,
            history: List<ChatMessage>,
            userInput: String,
            attachments: List<Attachment>,
            config: AgentConfig,
            runContext: AgentRunContext,
            onProgress: suspend (AgentProgressEvent) -> Unit
        ): AgentTurnResult {
            error("boom")
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
                newMessages = listOf(ChatMessage(sessionId = sessionId, role = MessageRole.ASSISTANT, content = "Completed")),
                finalResponse = ChatMessage(sessionId = sessionId, role = MessageRole.ASSISTANT, content = "Completed")
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

    private class FakeTaskPlanDao : com.example.nanobot.core.database.dao.TaskPlanDao {
        private val plans = linkedMapOf<String, com.example.nanobot.core.database.entity.TaskPlanEntity>()

        override suspend fun upsert(entity: com.example.nanobot.core.database.entity.TaskPlanEntity) {
            plans[entity.id] = entity
        }

        override suspend fun getById(id: String): com.example.nanobot.core.database.entity.TaskPlanEntity? = plans[id]

        override suspend fun getBySession(sessionId: String): List<com.example.nanobot.core.database.entity.TaskPlanEntity> =
            plans.values.filter { it.sessionId == sessionId }

        override suspend fun getActiveBySession(sessionId: String): com.example.nanobot.core.database.entity.TaskPlanEntity? =
            plans.values.firstOrNull { it.sessionId == sessionId && (it.status == TaskPlanStatus.PENDING.name || it.status == TaskPlanStatus.IN_PROGRESS.name) }
    }
}
