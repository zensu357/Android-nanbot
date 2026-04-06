package com.example.nanobot.core.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.ChatSession
import com.example.nanobot.core.model.MessageRole
import com.example.nanobot.core.model.Reminder
import com.example.nanobot.core.model.ReminderStatus
import com.example.nanobot.core.notifications.ReminderNotificationSink
import com.example.nanobot.domain.repository.ReminderRepository
import com.example.nanobot.domain.repository.SessionRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReminderWorkerTest {
    @Test
    fun deliversOnlyDueRemindersAndMarksFailures() = runTest {
        val repository = FakeReminderRepository(
            dueReminders = listOf(
                Reminder("due-1", "Title", "Now", 1L, ReminderStatus.SCHEDULED, 0L),
                Reminder("due-2", null, "Fails", 1L, ReminderStatus.SCHEDULED, 0L)
            )
        )
        val sessionRepository = FakeSessionRepository()
        val notifier = RecordingReminderNotifier(failIds = setOf("due-2"))
        val workerFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker {
                return ReminderWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    reminderRepository = repository,
                    reminderNotifier = notifier,
                    sessionRepository = sessionRepository
                )
            }
        }
        val worker = androidx.work.testing.TestListenableWorkerBuilder<ReminderWorker>(appContext())
            .setWorkerFactory(workerFactory)
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(repository.deliveredIds.contains("due-1"))
        assertTrue(repository.failedIds.contains("due-2"))
        assertEquals(listOf("due-1", "due-2"), notifier.notifiedIds)
    }

    @Test
    fun oneTimeReminderWorkAddsAssistantMessageToSession() = runTest {
        val reminder = Reminder("due-1", "Title", "Now", 1L, ReminderStatus.SCHEDULED, 0L)
        val repository = FakeReminderRepository(dueReminders = listOf(reminder))
        val sessionRepository = FakeSessionRepository()
        val notifier = RecordingReminderNotifier(failIds = emptySet())
        val workerFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker {
                return ReminderWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    reminderRepository = repository,
                    reminderNotifier = notifier,
                    sessionRepository = sessionRepository
                )
            }
        }
        val worker = androidx.work.testing.TestListenableWorkerBuilder<ReminderWorker>(appContext())
            .setWorkerFactory(workerFactory)
            .setInputData(
                ReminderWorker.buildInputData(reminder.id, "session-1")
            )
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(sessionRepository.savedMessages.any {
            it.sessionId == "session-1" &&
                it.role == MessageRole.ASSISTANT &&
                it.content.orEmpty().contains("提醒时间到了")
        })
        assertTrue(repository.deliveredIds.contains("due-1"))
    }

    private fun appContext(): Context = ApplicationProvider.getApplicationContext()

    private class FakeReminderRepository(
        private val dueReminders: List<Reminder>
    ) : ReminderRepository {
        val deliveredIds = mutableListOf<String>()
        val failedIds = mutableListOf<String>()

        override fun observeReminders(): Flow<List<Reminder>> = flowOf(dueReminders)
        override suspend fun getReminders(): List<Reminder> = dueReminders
        override suspend fun getReminder(id: String): Reminder? = dueReminders.firstOrNull { it.id == id }
        override suspend fun getDueReminders(now: Long): List<Reminder> = dueReminders
        override suspend fun markDelivered(id: String, deliveredAt: Long) { deliveredIds += id }
        override suspend fun markFailed(id: String, errorMessage: String) { failedIds += id }
        override suspend fun upsert(reminder: Reminder) = Unit
    }

    private class FakeSessionRepository : SessionRepository {
        val savedMessages = mutableListOf<ChatMessage>()
        private val session = ChatSession(id = "session-1", title = "Session")

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

    private class RecordingReminderNotifier(
        private val failIds: Set<String>
    ) : ReminderNotificationSink {
        val notifiedIds = mutableListOf<String>()

        override fun notify(reminder: Reminder): Boolean {
            notifiedIds += reminder.id
            if (reminder.id in failIds) {
                throw IllegalStateException("notification failed")
            }
            return true
        }
    }
}
