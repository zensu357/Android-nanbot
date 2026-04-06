@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.nanobot.feature.memory

import com.example.nanobot.core.ai.MemoryConsolidator
import com.example.nanobot.core.memory.VisualMemoryExtractor
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.ChatSession
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.LlmChatRequest
import com.example.nanobot.core.model.MemoryFact
import com.example.nanobot.core.model.MemorySummary
import com.example.nanobot.core.model.ProviderChatResult
import com.example.nanobot.core.model.MessageRole
import com.example.nanobot.core.preferences.SettingsConfigStore
import com.example.nanobot.domain.repository.MemoryRepository
import com.example.nanobot.domain.repository.ChatRepository
import com.example.nanobot.domain.repository.SessionRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
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

class MemoryViewModelTest {
    @Test
    fun saveFactEditUpdatesFactAndClearsEditor() = runMemoryTest {
        val memoryRepository = FakeMemoryRepository(
            facts = listOf(
                MemoryFact(
                    id = "fact-1",
                    fact = "The user prefers Kotlin.",
                    sourceSessionId = "session-1",
                    createdAt = 1L,
                    updatedAt = 2L,
                    confidence = 0.8f
                )
            )
        )
        val viewModel = createViewModel(memoryRepository)
        val collectionJob = backgroundScope.launch { viewModel.uiState.collect { } }

        advanceUntilIdle()
        val fact = viewModel.uiState.value.facts.single()
        viewModel.startEditingFact(fact)
        viewModel.updateFactDraft("The user prefers Java.")
        viewModel.saveFactEdit()
        advanceUntilIdle()

        assertEquals("The user prefers Java.", memoryRepository.facts.value.single().fact)
        assertEquals(0.8f, memoryRepository.facts.value.single().confidence)
        assertNull(viewModel.uiState.value.editor)
        collectionJob.cancel()
    }

    @Test
    fun deleteFactRemovesFactAndCancelsEditorIfNeeded() = runMemoryTest {
        val memoryRepository = FakeMemoryRepository(
            facts = listOf(
                MemoryFact(
                    id = "fact-1",
                    fact = "The user prefers Kotlin.",
                    sourceSessionId = "session-1",
                    createdAt = 1L,
                    updatedAt = 2L,
                    confidence = 0.8f
                )
            )
        )
        val viewModel = createViewModel(memoryRepository)
        val collectionJob = backgroundScope.launch { viewModel.uiState.collect { } }

        advanceUntilIdle()
        viewModel.startEditingFact(viewModel.uiState.value.facts.single())
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.editor)

        viewModel.deleteFact("fact-1")
        advanceUntilIdle()

        assertTrue(memoryRepository.facts.value.isEmpty())
        assertNull(viewModel.uiState.value.editor)
        collectionJob.cancel()
    }

    @Test
    fun deleteSummaryRemovesStoredSummary() = runMemoryTest {
        val memoryRepository = FakeMemoryRepository(
            summaries = listOf(
                MemorySummary(
                    sessionId = "session-1",
                    summary = "Existing summary",
                    updatedAt = 5L,
                    sourceMessageCount = 4,
                    confidence = 0.7f
                )
            )
        )
        val viewModel = createViewModel(memoryRepository)
        val collectionJob = backgroundScope.launch { viewModel.uiState.collect { } }

        advanceUntilIdle()
        viewModel.deleteSummary("session-1")
        advanceUntilIdle()

        assertTrue(memoryRepository.summaries.value.isEmpty())
        collectionJob.cancel()
    }

    @Test
    fun rebuildSummaryUpdatesSummaryAndClearsRebuildingState() = runMemoryTest {
        val memoryRepository = FakeMemoryRepository()
        val sessionRepository = FakeSessionRepository(
            messages = listOf(
                ChatMessage(sessionId = "session-1", role = MessageRole.USER, content = "I prefer Kotlin."),
                ChatMessage(sessionId = "session-1", role = MessageRole.ASSISTANT, content = "Noted."),
                ChatMessage(sessionId = "session-1", role = MessageRole.USER, content = "Please remember that."),
                ChatMessage(sessionId = "session-1", role = MessageRole.ASSISTANT, content = "Done.")
            )
        )
        val viewModel = createViewModel(memoryRepository, sessionRepository)
        val collectionJob = backgroundScope.launch { viewModel.uiState.collect { } }

        advanceUntilIdle()
        viewModel.rebuildSummary("session-1")
        advanceUntilIdle()

        assertEquals("The user prefers Kotlin.", memoryRepository.summaries.value.single().summary)
        assertTrue(viewModel.uiState.value.rebuildingSessionIds.isEmpty())
        collectionJob.cancel()
    }

    private fun runMemoryTest(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) {
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

    private fun createViewModel(
        memoryRepository: FakeMemoryRepository,
        sessionRepository: FakeSessionRepository = FakeSessionRepository()
    ): MemoryViewModel {
        return MemoryViewModel(
            memoryRepository = memoryRepository,
            sessionRepository = sessionRepository,
            settingsConfigStore = FakeSettingsConfigStore(),
            memoryConsolidator = MemoryConsolidator(
                memoryRepository = memoryRepository,
                chatRepository = FakeChatRepository(),
                memoryPromptBuilder = com.example.nanobot.core.ai.MemoryPromptBuilder(),
                visualMemoryExtractor = VisualMemoryExtractor(FakeChatRepository())
            )
        )
    }

    private class FakeMemoryRepository(
        facts: List<MemoryFact> = emptyList(),
        summaries: List<MemorySummary> = emptyList()
    ) : MemoryRepository {
        val facts = MutableStateFlow(facts)
        val summaries = MutableStateFlow(summaries)

        override fun observeFacts(): Flow<List<MemoryFact>> = facts

        override fun observeSummaries(): Flow<List<MemorySummary>> = summaries

        override suspend fun getFacts(): List<MemoryFact> = facts.value

        override suspend fun getFactsForSession(sessionId: String): List<MemoryFact> = facts.value.filter { it.sourceSessionId == sessionId }

        override suspend fun getAllSummaries(): List<MemorySummary> = summaries.value

        override suspend fun getFactsForQuery(query: String): List<MemoryFact> = facts.value.filter { it.fact.contains(query, ignoreCase = true) }

        override suspend fun observeSummariesSnapshot(): List<MemorySummary> = summaries.value

        override suspend fun getSummaryForSession(sessionId: String): MemorySummary? = summaries.value.firstOrNull { it.sessionId == sessionId }

        override suspend fun deleteFact(factId: String) {
            facts.value = facts.value.filterNot { it.id == factId }
        }

        override suspend fun deleteSummary(sessionId: String) {
            summaries.value = summaries.value.filterNot { it.sessionId == sessionId }
        }

        override suspend fun pruneFacts(maxFacts: Int) = Unit

        override suspend fun upsertFact(fact: MemoryFact) {
            facts.value = facts.value.filterNot { it.id == fact.id } + fact
        }

        override suspend fun upsertSummary(summary: MemorySummary) {
            summaries.value = summaries.value.filterNot { it.sessionId == summary.sessionId } + summary
        }
    }

    private class FakeSessionRepository(
        private val messages: List<ChatMessage> = emptyList()
    ) : SessionRepository {
        private val currentSession = MutableStateFlow(ChatSession(id = "session-1", title = "Chat"))

        override fun observeCurrentSession(): Flow<ChatSession?> = currentSession

        override fun observeSessions(): Flow<List<ChatSession>> = flowOf(listOf(currentSession.value))

        override fun observeMessages(sessionId: String): Flow<List<ChatMessage>> = flowOf(messages.filter { it.sessionId == sessionId })

        override suspend fun observeSessionsSnapshot(): List<ChatSession> = listOf(currentSession.value)

        override suspend fun getOrCreateCurrentSession(): ChatSession = currentSession.value

        override suspend fun getSessionByTitle(title: String): ChatSession? = currentSession.value.takeIf { it.title == title }

        override suspend fun createSession(
            title: String,
            makeCurrent: Boolean,
            parentSessionId: String?,
            subagentDepth: Int
        ): ChatSession = currentSession.value

        override suspend fun upsertSession(session: ChatSession, makeCurrent: Boolean): ChatSession = session

        override suspend fun selectSession(sessionId: String) = Unit

        override suspend fun deleteSession(sessionId: String) = Unit

        override suspend fun deleteSessionsOlderThan(cutoffMillis: Long) = Unit

        override suspend fun getMessages(sessionId: String): List<ChatMessage> = messages.filter { it.sessionId == sessionId }

        override suspend fun getHistoryForModel(sessionId: String, maxMessages: Int): List<ChatMessage> = messages.filter { it.sessionId == sessionId }.takeLast(maxMessages)

        override suspend fun saveMessage(message: ChatMessage) = Unit

        override suspend fun touchSession(session: ChatSession, makeCurrent: Boolean) = Unit
    }

    private class FakeSettingsConfigStore : SettingsConfigStore {
        override val configFlow: Flow<AgentConfig> = flowOf(AgentConfig(enableMemory = true))
        override val skillsDirectoryUriFlow: Flow<String?> = flowOf(null)
        override val skillRootsFlow: Flow<List<String>> = flowOf(emptyList())
        override val trustProjectSkillsFlow: Flow<Boolean> = flowOf(false)

        override suspend fun save(config: AgentConfig) = Unit

        override suspend fun saveSkillsDirectoryUri(uri: String?) = Unit

        override suspend fun addSkillRootUri(uri: String) = Unit

        override suspend fun removeSkillRootUri(uri: String) = Unit

        override suspend fun setTrustProjectSkills(trusted: Boolean) = Unit
    }

    private class FakeChatRepository : ChatRepository {
        override suspend fun completeChat(request: LlmChatRequest, config: AgentConfig): ProviderChatResult {
            return ProviderChatResult(
                content = """
                    {"updatedSummary":"The user prefers Kotlin.","candidateFacts":[]}
                """.trimIndent()
            )
        }
    }
}
