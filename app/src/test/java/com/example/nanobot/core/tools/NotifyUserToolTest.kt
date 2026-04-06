package com.example.nanobot.core.tools

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.model.Reminder
import com.example.nanobot.core.notifications.ReminderNotificationSink
import com.example.nanobot.core.tools.impl.NotifyUserTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class NotifyUserToolTest {
    @Test
    fun sendsNotificationImmediately() = runTest {
        val sink = RecordingReminderNotificationSink()
        val tool = NotifyUserTool(sink)

        val result = tool.execute(
            arguments = buildJsonObject { put("message", "Time to stand up") },
            config = AgentConfig(),
            runContext = AgentRunContext.root("session-1", 0)
        )

        val reminder = sink.lastReminder
        assertNotNull(reminder)
        assertEquals("Time to stand up", reminder.message)
        assertTrue(result.contains("User notification sent"))
    }

    @Test
    fun returnsFailureTextWhenNotificationCannotBeSent() = runTest {
        val tool = NotifyUserTool(FailingReminderNotificationSink())

        val result = tool.execute(
            arguments = buildJsonObject { put("message", "Time to stand up") },
            config = AgentConfig(),
            runContext = AgentRunContext.root("session-1", 0)
        )

        assertEquals("notifications disabled", result)
    }

    private class RecordingReminderNotificationSink : ReminderNotificationSink {
        var lastReminder: Reminder? = null

        override fun notify(reminder: Reminder): Boolean {
            lastReminder = reminder
            return true
        }
    }

    private class FailingReminderNotificationSink : ReminderNotificationSink {
        override fun notify(reminder: Reminder): Boolean {
            error("notifications disabled")
        }
    }
}
