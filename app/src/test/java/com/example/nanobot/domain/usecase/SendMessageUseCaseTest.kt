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
import com.example.nanobot.core.skills.ActivatedSkillSource
import com.example.nanobot.core.skills.ActivatedSkillSessionStore
import com.example.nanobot.core.skills.SkillActivationPayload
import com.example.nanobot.core.skills.SkillDefinition
import com.example.nanobot.core.skills.SkillDiscoveryIssue
import com.example.nanobot.core.skills.SkillImportResult
import com.example.nanobot.core.skills.SkillResourceReadResult
import com.example.nanobot.core.skills.SkillSource
import com.example.nanobot.domain.repository.SessionRepository
import com.example.nanobot.domain.repository.SkillRepository
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
            skillRepository = FakeSkillRepository(),
            activatedSkillSessionStore = ActivatedSkillSessionStore(),
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

    @Test
    fun activatedSkillAllowedToolsRestrictRunContext() = runTest {
        val sessionRepository = FakeSessionRepository()
        val activatedStore = ActivatedSkillSessionStore().apply {
            markActivated("session-1", "release-notes", "hash", ActivatedSkillSource.MODEL)
        }
        val skillRepository = FakeSkillRepository(
            skills = listOf(
                SkillDefinition(
                    id = "release-notes",
                    name = "release-notes",
                    title = "Release Notes",
                    description = "Generate release notes",
                    source = SkillSource.IMPORTED,
                    allowedTools = listOf("read_skill_resource", "notify_user")
                )
            )
        )
        val runner = CapturingAgentTurnRunner()
        val useCase = SendMessageUseCase(
            sessionRepository = sessionRepository,
            agentTurnRunner = runner,
            skillRepository = skillRepository,
            activatedSkillSessionStore = activatedStore,
            memoryRefreshScheduler = RecordingMemoryRefreshScheduler()
        )

        useCase(input = "Write release notes", config = AgentConfig())

        assertEquals(setOf("read_skill_resource", "notify_user"), runner.lastRunContext?.allowedToolNames)
    }

    @Test
    fun deactivatedSkillInstructionsAreFilteredFromReplayHistory() = runTest {
        val sessionRepository = FakeSessionRepository().apply {
            savedMessages += ChatMessage(
                sessionId = "session-1",
                role = MessageRole.TOOL,
                content = "<skill_content name=\"release-notes\">...</skill_content>",
                toolName = "activate_skill:release-notes",
                protectedContext = true
            )
        }
        val runner = CapturingAgentTurnRunner()
        val useCase = SendMessageUseCase(
            sessionRepository = sessionRepository,
            agentTurnRunner = runner,
            skillRepository = FakeSkillRepository(),
            activatedSkillSessionStore = ActivatedSkillSessionStore(),
            memoryRefreshScheduler = RecordingMemoryRefreshScheduler()
        )

        useCase(input = "Continue", config = AgentConfig())

        assertTrue(runner.lastHistory.none { it.toolName == "activate_skill:release-notes" })
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

    private class CapturingAgentTurnRunner : AgentTurnRunner {
        var lastRunContext: AgentRunContext? = null
        var lastHistory: List<ChatMessage> = emptyList()

        override suspend fun runTurn(
            sessionId: String,
            history: List<ChatMessage>,
            userInput: String,
            attachments: List<Attachment>,
            config: AgentConfig,
            runContext: AgentRunContext,
            onProgress: suspend (AgentProgressEvent) -> Unit
        ): AgentTurnResult {
            lastRunContext = runContext
            lastHistory = history
            return AgentTurnResult(emptyList(), null)
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

    private class FakeSkillRepository(
        private val skills: List<SkillDefinition> = emptyList()
    ) : SkillRepository {
        override fun observeSkills(): Flow<List<SkillDefinition>> = flowOf(skills)
        override fun observeDiscoveryIssues(): Flow<List<SkillDiscoveryIssue>> = flowOf(emptyList())
        override suspend fun listSkills(): List<SkillDefinition> = skills
        override suspend fun getEnabledSkills(config: AgentConfig): List<SkillDefinition> = skills
        override suspend fun getSkillByName(name: String): SkillDefinition? = skills.firstOrNull { it.name == name || it.id == name }
        override suspend fun activateSkill(name: String): SkillActivationPayload? = null
        override suspend fun readSkillResource(name: String, relativePath: String, sessionId: String, maxChars: Int): SkillResourceReadResult? = null
        override suspend fun importSkillsFromDirectory(uri: android.net.Uri): SkillImportResult = SkillImportResult(0, 0, 0, 0, emptyList())
        override suspend fun importSkillsFromZip(uri: android.net.Uri): SkillImportResult = SkillImportResult(0, 0, 0, 0, emptyList())
        override suspend fun removeImportedSkill(id: String) = Unit
        override suspend fun rescanImportedSkills(): SkillImportResult? = null
    }
}
