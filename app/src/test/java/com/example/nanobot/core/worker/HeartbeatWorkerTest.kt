package com.example.nanobot.core.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.example.nanobot.core.ai.AgentTurnRunner
import com.example.nanobot.core.ai.HeartbeatDecisionEngine
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentProgressEvent
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.model.AgentTurnResult
import com.example.nanobot.core.model.Attachment
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.ChatSession
import com.example.nanobot.core.model.HeartbeatDecisionResult
import com.example.nanobot.core.model.MessageRole
import com.example.nanobot.core.notifications.HeartbeatNotificationSink
import com.example.nanobot.core.preferences.SettingsConfigStore
import com.example.nanobot.domain.repository.SessionRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HeartbeatWorkerTest {
    @Test
    fun skipsWhenBackgroundWorkIsDisabled() = runTest {
        val sessionRepository = FakeSessionRepository()
        val worker = newWorker(
            settingsDataStore = FakeSettingsConfigStore(AgentConfig(enableBackgroundWork = false)),
            heartbeatDecider = FakeHeartbeatDecisionEngine(HeartbeatDecisionResult(action = "run", tasks = "Should not run")),
            sessionRepository = sessionRepository,
            agentOrchestrator = FakeAgentTurnRunner(),
            heartbeatNotifier = RecordingHeartbeatNotifier()
        )

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(sessionRepository.savedMessages.isEmpty())
    }

    @Test
    fun skipsWhenHeartbeatDecisionHasNoTask() = runTest {
        val sessionRepository = FakeSessionRepository()
        val worker = newWorker(
            settingsDataStore = FakeSettingsConfigStore(AgentConfig(enableBackgroundWork = true)),
            heartbeatDecider = FakeHeartbeatDecisionEngine(HeartbeatDecisionResult(action = "skip", tasks = "")),
            sessionRepository = sessionRepository,
            agentOrchestrator = FakeAgentTurnRunner(),
            heartbeatNotifier = RecordingHeartbeatNotifier()
        )

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(sessionRepository.savedMessages.isEmpty())
    }

    @Test
    fun runsHeartbeatTaskAndPersistsMessagesWhenDecisionIsRun() = runTest {
        val sessionRepository = FakeSessionRepository()
        val notifier = RecordingHeartbeatNotifier()
        val worker = newWorker(
            settingsDataStore = FakeSettingsConfigStore(AgentConfig(enableBackgroundWork = true)),
            heartbeatDecider = FakeHeartbeatDecisionEngine(HeartbeatDecisionResult(action = "run", tasks = "Check pending tasks")),
            sessionRepository = sessionRepository,
            agentOrchestrator = FakeAgentTurnRunner(
                AgentTurnResult(
                    newMessages = listOf(
                        ChatMessage(sessionId = "heartbeat-session", role = MessageRole.ASSISTANT, content = "Done")
                    ),
                    finalResponse = ChatMessage(sessionId = "heartbeat-session", role = MessageRole.ASSISTANT, content = "Done")
                )
            ),
            heartbeatNotifier = notifier
        )

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(sessionRepository.savedMessages.any { it.role == MessageRole.USER && it.content == "Check pending tasks" })
        assertTrue(sessionRepository.savedMessages.any { it.role == MessageRole.ASSISTANT && it.content == "Done" })
        assertEquals("Done", notifier.executedSummary)
    }

    private fun appContext(): Context = ApplicationProvider.getApplicationContext()

    private fun newWorker(
        settingsDataStore: SettingsConfigStore,
        heartbeatDecider: HeartbeatDecisionEngine,
        sessionRepository: SessionRepository,
        agentOrchestrator: AgentTurnRunner,
        heartbeatNotifier: HeartbeatNotificationSink
    ): HeartbeatWorker {
        val workerFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker {
                return HeartbeatWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    settingsDataStore = settingsDataStore,
                    heartbeatDecider = heartbeatDecider,
                    sessionRepository = sessionRepository,
                    agentOrchestrator = agentOrchestrator,
                    heartbeatNotifier = heartbeatNotifier
                )
            }
        }
        return androidx.work.testing.TestListenableWorkerBuilder<HeartbeatWorker>(appContext())
            .setWorkerFactory(workerFactory)
            .build()
    }

    private class FakeSettingsConfigStore(config: AgentConfig) : SettingsConfigStore {
        override val configFlow: Flow<AgentConfig> = MutableStateFlow(config)
        override val skillsDirectoryUriFlow: Flow<String?> = MutableStateFlow(null)
        override val skillRootsFlow: Flow<List<String>> = MutableStateFlow(emptyList())
        override val trustProjectSkillsFlow: Flow<Boolean> = MutableStateFlow(false)
        override suspend fun save(config: AgentConfig) = Unit
        override suspend fun saveSkillsDirectoryUri(uri: String?) = Unit
        override suspend fun addSkillRootUri(uri: String) = Unit
        override suspend fun removeSkillRootUri(uri: String) = Unit
        override suspend fun setTrustProjectSkills(trusted: Boolean) = Unit
    }

    private class FakeHeartbeatDecisionEngine(
        private val decision: HeartbeatDecisionResult
    ) : HeartbeatDecisionEngine {
        override suspend fun decide(config: AgentConfig): HeartbeatDecisionResult = decision
    }

    private class FakeAgentTurnRunner(
        private val result: AgentTurnResult = AgentTurnResult(emptyList(), null)
    ) : AgentTurnRunner {
        override suspend fun runTurn(
            sessionId: String,
            history: List<ChatMessage>,
            userInput: String,
            attachments: List<Attachment>,
            config: AgentConfig,
            runContext: AgentRunContext,
            onProgress: suspend (AgentProgressEvent) -> Unit
        ): AgentTurnResult = result
    }

    private class RecordingHeartbeatNotifier : HeartbeatNotificationSink {
        var executedSummary: String? = null
        var failedSummary: String? = null

        override fun notifyExecuted(summary: String): Boolean {
            executedSummary = summary
            return true
        }

        override fun notifyFailed(summary: String): Boolean {
            failedSummary = summary
            return true
        }
    }

    private class FakeSessionRepository : SessionRepository {
        val savedMessages = mutableListOf<ChatMessage>()
        private val session = ChatSession(id = "heartbeat-session", title = "Heartbeat")

        override fun observeCurrentSession(): Flow<ChatSession?> = flowOf(session)
        override fun observeSessions(): Flow<List<ChatSession>> = flowOf(listOf(session))
        override fun observeMessages(sessionId: String): Flow<List<ChatMessage>> = flowOf(savedMessages)
        override suspend fun observeSessionsSnapshot(): List<ChatSession> = listOf(session)
        override suspend fun getOrCreateCurrentSession(): ChatSession = session
        override suspend fun getSessionByTitle(title: String): ChatSession? = session.takeIf { it.title == title }
        override suspend fun createSession(title: String, makeCurrent: Boolean, parentSessionId: String?, subagentDepth: Int): ChatSession = session
        override suspend fun upsertSession(session: ChatSession, makeCurrent: Boolean): ChatSession = session
        override suspend fun selectSession(sessionId: String) = Unit
        override suspend fun deleteSession(sessionId: String) = Unit
        override suspend fun deleteSessionsOlderThan(cutoffMillis: Long) = Unit
        override suspend fun getMessages(sessionId: String): List<ChatMessage> = savedMessages
        override suspend fun getHistoryForModel(sessionId: String, maxMessages: Int): List<ChatMessage> = savedMessages
        override suspend fun saveMessage(message: ChatMessage) { savedMessages += message }
        override suspend fun touchSession(session: ChatSession, makeCurrent: Boolean) = Unit
    }
}
