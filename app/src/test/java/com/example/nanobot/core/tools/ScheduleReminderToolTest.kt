package com.example.nanobot.core.tools

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.model.Reminder
import com.example.nanobot.core.tools.impl.ScheduleReminderTool
import com.example.nanobot.core.worker.ReminderWorkScheduler
import com.example.nanobot.domain.repository.ReminderRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ScheduleReminderToolTest {
    @Test
    fun persistsReminderAndSchedulesDedicatedWork() = runTest {
        val repository = RecordingReminderRepository()
        val scheduler = RecordingReminderWorkScheduler()
        val tool = ScheduleReminderTool(repository, scheduler)

        val result = tool.execute(
            arguments = buildJsonObject {
                put("message", "Stand up now")
                put("delayMinutes", 1)
            },
            config = AgentConfig(),
            runContext = AgentRunContext.root("session-1", 0)
        )

        val reminder = repository.lastUpserted
        assertNotNull(reminder)
        assertEquals(reminder.id, scheduler.scheduledReminder?.id)
        assertEquals("session-1", scheduler.sessionId)
        assertTrue(result.contains("Reminder created."))
        assertTrue(result.contains("Stand up now"))
    }

    private class RecordingReminderRepository : ReminderRepository {
        var lastUpserted: Reminder? = null

        override fun observeReminders(): Flow<List<Reminder>> = flowOf(emptyList())

        override suspend fun getReminders(): List<Reminder> = emptyList()

        override suspend fun getReminder(id: String): Reminder? = null

        override suspend fun getDueReminders(now: Long): List<Reminder> = emptyList()

        override suspend fun markDelivered(id: String, deliveredAt: Long) = Unit

        override suspend fun markFailed(id: String, errorMessage: String) = Unit

        override suspend fun upsert(reminder: Reminder) {
            lastUpserted = reminder
        }
    }

    private class RecordingReminderWorkScheduler : ReminderWorkScheduler {
        var scheduledReminder: Reminder? = null
        var sessionId: String? = null

        override suspend fun scheduleReminder(reminder: Reminder, sessionId: String?) {
            scheduledReminder = reminder
            this.sessionId = sessionId
        }
    }
}
