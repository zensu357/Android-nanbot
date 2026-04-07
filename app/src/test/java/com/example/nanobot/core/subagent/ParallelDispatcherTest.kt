package com.example.nanobot.core.subagent

import com.example.nanobot.core.ai.AgentTurnRunner
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentProgressEvent
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.model.AgentTurnResult
import com.example.nanobot.core.model.Attachment
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.ChatSession
import com.example.nanobot.core.model.MessageRole
import com.example.nanobot.domain.repository.SessionRepository
import javax.inject.Provider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ParallelDispatcherTest {
    @Test
    fun timesOutIndividualSubtasksWithoutBlockingSuccessfulOnes() = runTest {
        val coordinator = SubagentCoordinator(
            sessionRepository = FakeSessionRepository(),
            agentTurnRunnerProvider = providerOf(DelayingAgentTurnRunner())
        )
        val dispatcher = ParallelDispatcher(coordinator, ResultAggregator())

        val result = dispatcher.dispatchAll(
            subtasks = listOf(
                SubtaskSpec(task = "slow task", title = "Slow Task", priority = 10),
                SubtaskSpec(task = "fast task", title = "Fast Task", priority = 80)
            ),
            config = AgentConfig(),
            parentContext = AgentRunContext.root(
                sessionId = "parent",
                maxSubagentDepth = 3,
                maxParallelSubagents = 2
            ),
            timeoutMs = 50L
        )

        assertTrue(result.success)
        assertEquals(1, result.failedCount)
        assertEquals(2, result.individualResults.size)
        assertTrue(result.individualResults.any { it.success })
        assertTrue(result.individualResults.any { !it.success && it.summary.contains("timed out") })
        assertTrue(result.mergedSummary.contains("### Fast Task [completed]"))
    }

    @Test
    fun blocksDispatchWhenDelegationDepthIsExceeded() = runTest {
        val coordinator = SubagentCoordinator(
            sessionRepository = FakeSessionRepository(),
            agentTurnRunnerProvider = providerOf(DelayingAgentTurnRunner())
        )
        val dispatcher = ParallelDispatcher(coordinator, ResultAggregator())

        val result = dispatcher.dispatchAll(
            subtasks = listOf(SubtaskSpec(task = "one")),
            config = AgentConfig(),
            parentContext = AgentRunContext(
                sessionId = "parent",
                subagentDepth = 3,
                maxSubagentDepth = 3,
                maxParallelSubagents = 2
            )
        )

        assertFalse(result.success)
        assertTrue(result.mergedSummary.contains("maximum subagent depth"))
    }

    @Test
    fun runsSubtasksConcurrentlyInsteadOfSerially() = runTest {
        val coordinator = SubagentCoordinator(
            sessionRepository = FakeSessionRepository(),
            agentTurnRunnerProvider = providerOf(DelayingAgentTurnRunner())
        )
        val dispatcher = ParallelDispatcher(coordinator, ResultAggregator())

        val before = currentTime
        val result = dispatcher.dispatchAll(
            subtasks = listOf(
                SubtaskSpec(task = "slow task a", title = "A"),
                SubtaskSpec(task = "slow task b", title = "B"),
                SubtaskSpec(task = "slow task c", title = "C"),
                SubtaskSpec(task = "slow task d", title = "D")
            ),
            config = AgentConfig(),
            parentContext = AgentRunContext.root(
                sessionId = "parent",
                maxSubagentDepth = 3,
                maxParallelSubagents = 4
            ),
            timeoutMs = 1_000L
        )
        val elapsed = currentTime - before

        assertTrue(result.success)
        assertEquals(0, result.failedCount)
        assertTrue(elapsed < 250L, "Expected parallel execution; elapsed=${elapsed}ms")
    }

    private class DelayingAgentTurnRunner : AgentTurnRunner {
        override suspend fun runTurn(
            sessionId: String,
            history: List<ChatMessage>,
            userInput: String,
            attachments: List<Attachment>,
            config: AgentConfig,
            runContext: AgentRunContext,
            onProgress: suspend (AgentProgressEvent) -> Unit
        ): AgentTurnResult {
            if (userInput.contains("slow task")) {
                delay(100)
            }
            val content = "Finished: $userInput"
            return AgentTurnResult(
                newMessages = listOf(
                    ChatMessage(
                        sessionId = sessionId,
                        role = MessageRole.ASSISTANT,
                        content = content
                    )
                ),
                finalResponse = ChatMessage(
                    sessionId = sessionId,
                    role = MessageRole.ASSISTANT,
                    content = content
                )
            )
        }
    }

    private class FakeSessionRepository : SessionRepository {
        val sessions = mutableListOf<ChatSession>()
        val messages = mutableMapOf<String, MutableList<ChatMessage>>()

        override fun observeCurrentSession(): Flow<ChatSession?> = flowOf(sessions.firstOrNull())

        override fun observeSessions(): Flow<List<ChatSession>> = flowOf(sessions)

        override fun observeMessages(sessionId: String): Flow<List<ChatMessage>> = flowOf(messages[sessionId].orEmpty())

        override suspend fun observeSessionsSnapshot(): List<ChatSession> = sessions.toList()

        override suspend fun getOrCreateCurrentSession(): ChatSession {
            return sessions.firstOrNull() ?: createSession()
        }

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
            return session
        }

        override suspend fun upsertSession(session: ChatSession, makeCurrent: Boolean): ChatSession {
            sessions.removeAll { it.id == session.id }
            sessions += session
            return session
        }

        override suspend fun selectSession(sessionId: String) = Unit

        override suspend fun deleteSession(sessionId: String) {
            sessions.removeAll { it.id == sessionId }
            messages.remove(sessionId)
        }

        override suspend fun deleteSessionsOlderThan(cutoffMillis: Long) = Unit

        override suspend fun getMessages(sessionId: String): List<ChatMessage> = messages[sessionId].orEmpty()

        override suspend fun getHistoryForModel(sessionId: String, maxMessages: Int): List<ChatMessage> =
            messages[sessionId].orEmpty().takeLast(maxMessages)

        override suspend fun saveMessage(message: ChatMessage) {
            messages.getOrPut(message.sessionId) { mutableListOf() } += message
        }

        override suspend fun touchSession(session: ChatSession, makeCurrent: Boolean) {
            upsertSession(session.copy(updatedAt = session.updatedAt + 1), makeCurrent)
        }
    }

    private fun providerOf(runner: AgentTurnRunner): Provider<AgentTurnRunner> = Provider { runner }
}
