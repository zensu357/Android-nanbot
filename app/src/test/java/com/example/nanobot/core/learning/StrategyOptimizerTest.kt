package com.example.nanobot.core.learning

import com.example.nanobot.core.ai.FakeBehaviorEventDao
import com.example.nanobot.core.database.entity.BehaviorEventEntity
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class StrategyOptimizerTest {
    @Test
    fun includesFrequentAndRiskyToolsInGeneratedHints() = runTest {
        val now = System.currentTimeMillis()
        val events = buildList {
            repeat(10) {
                add(
                    BehaviorEventEntity(
                        type = EventType.TOOL_USAGE.name,
                        key = "write_file",
                        sessionId = "session-1",
                        metadata = "{\"success\":true,\"duration_ms\":120}",
                        timestamp = now - it
                    )
                )
            }
            repeat(6) {
                add(
                    BehaviorEventEntity(
                        type = EventType.TOOL_USAGE.name,
                        key = "web_fetch",
                        sessionId = "session-1",
                        metadata = "{\"success\":false,\"duration_ms\":200}",
                        timestamp = now - 100 - it
                    )
                )
            }
            repeat(4) {
                add(
                    BehaviorEventEntity(
                        type = EventType.FEEDBACK.name,
                        key = FeedbackSignal.IMPLICIT_CORRECTION.name,
                        sessionId = "session-1",
                        metadata = "{\"signal\":\"IMPLICIT_CORRECTION\",\"response_style\":\"concise\"}",
                        timestamp = now - 200 - it
                    )
                )
            }
        }
        val optimizer = StrategyOptimizer(BehaviorAnalyzer(FakeBehaviorEventDao(events)))

        val hints = optimizer.generateStrategyHints()

        assertNotNull(hints)
        assertTrue(hints.contains("Frequently Used Tools"))
        assertTrue(hints.contains("write_file"))
        assertTrue(hints.contains("Tools to Use Carefully"))
        assertTrue(hints.contains("web_fetch"))
    }
}
