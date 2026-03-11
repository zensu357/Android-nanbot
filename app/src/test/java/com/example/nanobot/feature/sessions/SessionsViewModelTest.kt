@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.nanobot.feature.sessions

import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.ChatSession
import com.example.nanobot.domain.repository.SessionRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

class SessionsViewModelTest {
    @Test
    fun deleteFailureSetsErrorAndClearsBusyState() = runSessionsTest {
        val repository = FakeSessionRepository(deleteFailure = IllegalStateException("boom"))
        val viewModel = SessionsViewModel(repository)
        val collectJob = backgroundScope.launch { viewModel.uiState.collect { } }

        advanceUntilIdle()
        viewModel.deleteSession("session-1")
        advanceUntilIdle()

        assertEquals("boom", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isDeleting)
        collectJob.cancel()
    }

    @Test
    fun clearErrorRemovesVisibleErrorState() = runSessionsTest {
        val repository = FakeSessionRepository(createFailure = IllegalStateException("create failed"))
        val viewModel = SessionsViewModel(repository)
        val collectJob = backgroundScope.launch { viewModel.uiState.collect { } }

        advanceUntilIdle()
        viewModel.createSession()
        advanceUntilIdle()
        viewModel.clearError()
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isCreating)
        collectJob.cancel()
    }

    private fun runSessionsTest(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            runTest(dispatcher) {
                block()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeSessionRepository(
        private val createFailure: Throwable? = null,
        private val deleteFailure: Throwable? = null
    ) : SessionRepository {
        private val sessions = MutableStateFlow(listOf(ChatSession(id = "session-1", title = "Chat")))
        private val currentSession = MutableStateFlow(sessions.value.first())

        override fun observeCurrentSession(): Flow<ChatSession?> = currentSession

        override fun observeSessions(): Flow<List<ChatSession>> = sessions

        override fun observeMessages(sessionId: String): Flow<List<ChatMessage>> = flowOf(emptyList())

        override suspend fun observeSessionsSnapshot(): List<ChatSession> = sessions.value

        override suspend fun getOrCreateCurrentSession(): ChatSession = currentSession.value

        override suspend fun getSessionByTitle(title: String): ChatSession? = sessions.value.firstOrNull { it.title == title }

        override suspend fun createSession(
            title: String,
            makeCurrent: Boolean,
            parentSessionId: String?,
            subagentDepth: Int
        ): ChatSession {
            createFailure?.let { throw it }
            val session = ChatSession(title = title)
            sessions.value = sessions.value + session
            if (makeCurrent) {
                currentSession.value = session
            }
            return session
        }

        override suspend fun upsertSession(session: ChatSession, makeCurrent: Boolean): ChatSession {
            sessions.value = sessions.value.filterNot { it.id == session.id } + session
            if (makeCurrent) {
                currentSession.value = session
            }
            return session
        }

        override suspend fun selectSession(sessionId: String) {
            currentSession.value = sessions.value.first { it.id == sessionId }
        }

        override suspend fun deleteSession(sessionId: String) {
            deleteFailure?.let { throw it }
            sessions.value = sessions.value.filterNot { it.id == sessionId }
            currentSession.value = sessions.value.firstOrNull() ?: ChatSession(title = "New Chat")
        }

        override suspend fun deleteSessionsOlderThan(cutoffMillis: Long) = Unit

        override suspend fun getMessages(sessionId: String): List<ChatMessage> = emptyList()

        override suspend fun getHistoryForModel(sessionId: String, maxMessages: Int): List<ChatMessage> = emptyList()

        override suspend fun saveMessage(message: ChatMessage) = Unit

        override suspend fun touchSession(session: ChatSession, makeCurrent: Boolean) = Unit
    }
}
