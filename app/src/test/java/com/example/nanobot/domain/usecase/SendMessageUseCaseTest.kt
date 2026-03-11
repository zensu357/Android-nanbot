package com.example.nanobot.domain.usecase

import com.example.nanobot.core.ai.AgentTurnRunner
import com.example.nanobot.core.ai.MemoryRefreshScheduler
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentProgressEvent
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.model.Attachment
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.ChatSession
import com.example.nanobot.core.model.MessageRole
import com.example.nanobot.core.model.AgentTurnResult
import com.example.nanobot.domain.repository.SessionRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class SendMessageUseCaseTest {
    @Test
    fun requestsRealtimeMemoryRefreshAfterSavingTurnMessages() = runTest {
        val sessionRepository = FakeSessionRepository()
        val scheduler = RecordingMemoryRefreshScheduler()
        val useCase = SendMessageUseCase(
            sessionRepository = sessionRepository,
            agentTurnRunner = FakeAgentTurnRunner(),
            memoryRefreshScheduler = scheduler
        )

        val messages = useCase(
            input = "Remember my Kotlin preference.",
            config = AgentConfig(enableMemory = true)
        )

        val request = scheduler.requests.singleOrNull()
        assertNotNull(request)
        assertEquals("session-1", request.sessionId)
        assertTrue(request.config.enableMemory)
        assertEquals(2, sessionRepository.savedMessages.size)
        assertEquals(MessageRole.USER, sessionRepository.savedMessages.first().role)
        assertEquals(MessageRole.ASSISTANT, sessionRepository.savedMessages.last().role)
        assertEquals(2, messages.size)
    }

    private class FakeAgentTurnRunner : AgentTurnRunner {
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
                newMessages = listOf(
                    ChatMessage(
                        sessionId = sessionId,
                        role = MessageRole.ASSISTANT,
                        content = "Noted."
                    )
                ),
                finalResponse = null
            )
        }
    }

    private class RecordingMemoryRefreshScheduler : MemoryRefreshScheduler {
        val requests = mutableListOf<Request>()

        override fun request(sessionId: String, config: AgentConfig) {
            requests += Request(sessionId, config)
        }

        data class Request(
            val sessionId: String,
            val config: AgentConfig
        )
    }

    private class FakeSessionRepository : SessionRepository {
        private val session = ChatSession(id = "session-1", title = "New Chat")
        private val sessions = MutableStateFlow(listOf(session))
        val savedMessages = mutableListOf<ChatMessage>()

        override fun observeCurrentSession(): Flow<ChatSession?> = flowOf(session)

        override fun observeSessions(): Flow<List<ChatSession>> = sessions

        override fun observeMessages(sessionId: String): Flow<List<ChatMessage>> = flowOf(savedMessages.filter { it.sessionId == sessionId })

        override suspend fun observeSessionsSnapshot(): List<ChatSession> = sessions.value

        override suspend fun getOrCreateCurrentSession(): ChatSession = session

        override suspend fun getSessionByTitle(title: String): ChatSession? = sessions.value.firstOrNull { it.title == title }

        override suspend fun createSession(
            title: String,
            makeCurrent: Boolean,
            parentSessionId: String?,
            subagentDepth: Int
        ): ChatSession = session

        override suspend fun upsertSession(session: ChatSession, makeCurrent: Boolean): ChatSession = session

        override suspend fun selectSession(sessionId: String) = Unit

        override suspend fun deleteSession(sessionId: String) = Unit

        override suspend fun deleteSessionsOlderThan(cutoffMillis: Long) = Unit

        override suspend fun getMessages(sessionId: String): List<ChatMessage> = savedMessages.filter { it.sessionId == sessionId }

        override suspend fun getHistoryForModel(sessionId: String, maxMessages: Int): List<ChatMessage> =
            savedMessages.filter { it.sessionId == sessionId }.takeLast(maxMessages)

        override suspend fun saveMessage(message: ChatMessage) {
            savedMessages += message
        }

        override suspend fun touchSession(session: ChatSession, makeCurrent: Boolean) = Unit
    }
}
