package com.example.nanobot.core.learning

import com.example.nanobot.core.ai.FakeBehaviorEventDao
import com.example.nanobot.core.database.entity.BehaviorEventEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class BehaviorAnalyzerTest {
    @Test
    fun returnsNullWhenNotEnoughEventsExist() = runTest {
        val analyzer = BehaviorAnalyzer(FakeBehaviorEventDao())

        val profile = analyzer.analyze()

        assertNull(profile)
    }

    @Test
    fun buildsProfileFromSufficientRecentEvents() = runTest {
        val now = System.currentTimeMillis()
        val events = buildList {
            repeat(12) { index ->
                add(
                    BehaviorEventEntity(
                        type = EventType.TOOL_USAGE.name,
                        key = if (index < 8) "write_file" else "read_file",
                        sessionId = "session-1",
                        metadata = "{\"success\":${index != 10},\"duration_ms\":${100 + index}}",
                        timestamp = now - index
                    )
                )
            }
            repeat(4) {
                add(
                    BehaviorEventEntity(
                        type = EventType.TASK_COMPLETION.name,
                        key = "refactor",
                        sessionId = "session-1",
                        metadata = "{\"tool_sequence\":[\"read_file\",\"write_file\"],\"success\":true}",
                        timestamp = now - 100 - it
                    )
                )
            }
            repeat(4) { index ->
                add(
                    BehaviorEventEntity(
                        type = EventType.FEEDBACK.name,
                        key = if (index < 3) FeedbackSignal.POSITIVE.name else FeedbackSignal.IMPLICIT_CORRECTION.name,
                        sessionId = "session-1",
                        metadata = if (index < 3) {
                            "{\"signal\":\"POSITIVE\",\"response_style\":\"detailed\"}"
                        } else {
                            "{\"signal\":\"IMPLICIT_CORRECTION\",\"response_style\":\"concise\"}"
                        },
                        timestamp = now - 200 - index
                    )
                )
            }
        }
        val analyzer = BehaviorAnalyzer(FakeBehaviorEventDao(events))

        val profile = analyzer.analyze()

        assertNotNull(profile)
        assertEquals("write_file", profile.toolPreferences.first().toolName)
        assertTrue(profile.commonTaskPatterns.isNotEmpty())
        assertEquals(ComplexityPreference.DETAILED, profile.preferredComplexity)
        assertTrue(profile.feedbackTrends.totalFeedbacks >= 4)
    }
}
