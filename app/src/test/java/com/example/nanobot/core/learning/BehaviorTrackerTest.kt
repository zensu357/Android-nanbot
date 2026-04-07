package com.example.nanobot.core.learning

import com.example.nanobot.core.ai.FakeBehaviorEventDao
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class BehaviorTrackerTest {
    @Test
    fun recordsToolUsageWithSanitizedArguments() = runTest {
        val dao = FakeBehaviorEventDao()
        val tracker = BehaviorTracker(dao)

        tracker.trackToolUsage(
            ToolUsageEvent(
                toolName = "write_file",
                sessionId = "session-1",
                arguments = "{\"content\":\"${"x".repeat(300)}\"}",
                success = true,
                durationMs = 120,
                turnIndex = 2
            )
        )

        val event = dao.allEvents().single()
        assertEquals(EventType.TOOL_USAGE.name, event.type)
        assertEquals("write_file", event.key)
        assertTrue(event.metadata.contains("\"success\":true"))
        assertTrue(event.metadata.length < 320)
    }

    @Test
    fun recordsFeedbackAndTaskCompletionEvents() = runTest {
        val dao = FakeBehaviorEventDao()
        val tracker = BehaviorTracker(dao)

        tracker.trackTaskCompletion(
            TaskCompletionEvent(
                sessionId = "session-1",
                taskType = "refactor",
                toolSequence = listOf("read_file", "write_file"),
                totalTurns = 3,
                success = true
            )
        )
        tracker.trackFeedback(
            FeedbackEvent(
                sessionId = "session-1",
                messageId = "msg-1",
                signal = FeedbackSignal.POSITIVE,
                responseStyle = ResponseStyle.DETAILED
            )
        )

        assertEquals(2, dao.allEvents().size)
        assertEquals(EventType.TASK_COMPLETION.name, dao.allEvents()[0].type)
        assertEquals(EventType.FEEDBACK.name, dao.allEvents()[1].type)
    }
}
