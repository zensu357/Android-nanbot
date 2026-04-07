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
import com.example.nanobot.core.model.SubagentRequest
import com.example.nanobot.domain.repository.SessionRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import javax.inject.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException

class SubagentCoordinatorTest {
    @Test
    fun createsIsolatedChildSessionAndReturnsSummary() = runTest {
        val sessionRepository = FakeSessionRepository()
        val runner = RecordingAgentTurnRunner(
            result = AgentTurnResult(
                newMessages = listOf(
                    ChatMessage(
                        sessionId = "child-session",
                        role = MessageRole.TOOL,
                        content = "Workspace file written.\nPath: notes/report.txt"
                    ),
                    ChatMessage(
                        sessionId = "child-session",
                        role = MessageRole.ASSISTANT,
                        content = "Completed the delegated work and saved the report."
                    )
                ),
                finalResponse = ChatMessage(
                    sessionId = "child-session",
                    role = MessageRole.ASSISTANT,
                    content = "Completed the delegated work and saved the report."
                )
            )
        )
        val coordinator = SubagentCoordinator(sessionRepository, providerOf(runner))

        val result = coordinator.delegate(
            request = SubagentRequest(
                parentSessionId = "parent-session",
                task = "Inspect the workspace and prepare a report"
            ),
            config = AgentConfig()
        )

        assertTrue(result.completed)
        assertEquals("parent-session", result.parentSessionId)
        assertEquals(1, result.subagentDepth)
        assertTrue(result.summary.contains("Completed the delegated work"))
        assertEquals(listOf("notes/report.txt"), result.artifactPaths)
        val childSession = sessionRepository.createdSessions.single()
        assertEquals("parent-session", childSession.parentSessionId)
        assertEquals(1, childSession.subagentDepth)
        assertFalse(sessionRepository.selectedSessionIds.contains(childSession.id))
        val recordedContext = runner.recordedRunContext
        assertNotNull(recordedContext)
        assertEquals(childSession.id, recordedContext.sessionId)
        assertEquals("parent-session", recordedContext.parentSessionId)
        assertEquals(1, recordedContext.subagentDepth)
        assertEquals(4, recordedContext.maxParallelSubagents)
    }

    @Test
    fun blocksRecursiveDelegationWhenDepthLimitReached() = runTest {
        val sessionRepository = FakeSessionRepository()
        val runner = RecordingAgentTurnRunner(
            result = AgentTurnResult(newMessages = emptyList(), finalResponse = null)
        )
        val coordinator = SubagentCoordinator(sessionRepository, providerOf(runner))

        val result = coordinator.delegate(
            request = SubagentRequest(
                parentSessionId = "parent-session",
                task = "Nested delegation",
                subagentDepth = 1,
                maxSubagentDepth = 1
            ),
            config = AgentConfig(maxSubagentDepth = 1)
        )

        assertFalse(result.completed)
        assertTrue(result.summary.contains("maximum subagent depth"))
        assertTrue(sessionRepository.createdSessions.isEmpty())
        assertEquals(0, runner.invocationCount)
    }

    @Test
    fun propagatesCancellationFromChildRun() = runTest {
        val sessionRepository = FakeSessionRepository()
        val runner = object : AgentTurnRunner {
            override suspend fun runTurn(
                sessionId: String,
                history: List<ChatMessage>,
                userInput: String,
                attachments: List<Attachment>,
                config: AgentConfig,
                runContext: AgentRunContext,
                onProgress: suspend (AgentProgressEvent) -> Unit
            ): AgentTurnResult {
                throw CancellationException("cancelled")
            }
        }
        val coordinator = SubagentCoordinator(sessionRepository, providerOf(runner))

        var cancelled = false
        try {
            coordinator.delegate(
                request = SubagentRequest(
                    parentSessionId = "parent-session",
                    task = "Cancelable subtask"
                ),
                config = AgentConfig()
            )
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
    }

    @Test
    fun augmentsDelegatedTaskForSpecializedRoles() = runTest {
        val sessionRepository = FakeSessionRepository()
        val runner = RecordingAgentTurnRunner(
            result = AgentTurnResult(newMessages = emptyList(), finalResponse = null)
        )
        val coordinator = SubagentCoordinator(sessionRepository, providerOf(runner))

        coordinator.delegate(
            task = "Review the implementation for hidden risks",
            title = "Review",
            role = AgentRole.REVIEWER,
            config = AgentConfig(maxSubagentDepth = 3, maxParallelSubagents = 2),
            runContext = AgentRunContext.root(
                sessionId = "parent-session",
                maxSubagentDepth = 3,
                maxParallelSubagents = 2
            )
        )

        assertTrue(runner.recordedUserInput.orEmpty().startsWith(AgentRole.REVIEWER.systemPromptFragment))
        assertEquals(2, runner.recordedRunContext?.maxParallelSubagents)
    }

    private class RecordingAgentTurnRunner(
        private val result: AgentTurnResult
    ) : AgentTurnRunner {
        var invocationCount: Int = 0
        var recordedRunContext: AgentRunContext? = null
        var recordedUserInput: String? = null

        override suspend fun runTurn(
            sessionId: String,
            history: List<ChatMessage>,
            userInput: String,
            attachments: List<Attachment>,
            config: AgentConfig,
            runContext: AgentRunContext,
            onProgress: suspend (AgentProgressEvent) -> Unit
        ): AgentTurnResult {
            invocationCount += 1
            recordedRunContext = runContext
            recordedUserInput = userInput
            return result.copy(
                newMessages = result.newMessages.map { it.copy(sessionId = sessionId) },
                finalResponse = result.finalResponse?.copy(sessionId = sessionId)
            )
        }
    }

    private class FakeSessionRepository : SessionRepository {
        val sessions = mutableListOf<ChatSession>()
        val messages = mutableMapOf<String, MutableList<ChatMessage>>()
        val createdSessions = mutableListOf<ChatSession>()
        val selectedSessionIds = mutableListOf<String>()

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
            createdSessions += session
            if (makeCurrent) {
                selectedSessionIds += session.id
            }
            return session
        }

        override suspend fun upsertSession(session: ChatSession, makeCurrent: Boolean): ChatSession {
            sessions.removeAll { it.id == session.id }
            sessions += session
            if (makeCurrent) {
                selectedSessionIds += session.id
            }
            return session
        }

        override suspend fun selectSession(sessionId: String) {
            selectedSessionIds += sessionId
        }

        override suspend fun deleteSession(sessionId: String) {
            sessions.removeAll { it.id == sessionId }
            messages.remove(sessionId)
        }

        override suspend fun deleteSessionsOlderThan(cutoffMillis: Long) {
            val targetIds = sessions.filter { it.updatedAt < cutoffMillis }.map { it.id }.toSet()
            sessions.removeAll { it.id in targetIds }
            targetIds.forEach(messages::remove)
        }

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
