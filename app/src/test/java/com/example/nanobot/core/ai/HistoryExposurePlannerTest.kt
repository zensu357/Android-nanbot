package com.example.nanobot.core.ai

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.MessageRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryExposurePlannerTest {
    private val planner = HistoryExposurePlanner()

    @Test
    fun plannerKeepsMostRecentMessagesWithinBudget() {
        val history = listOf(
            ChatMessage(role = MessageRole.USER, content = "old-1 " + "A".repeat(900), sessionId = "session-1"),
            ChatMessage(role = MessageRole.ASSISTANT, content = "old-2 " + "B".repeat(900), sessionId = "session-1"),
            ChatMessage(role = MessageRole.USER, content = "recent-1", sessionId = "session-1"),
            ChatMessage(role = MessageRole.ASSISTANT, content = "recent-2", sessionId = "session-1")
        )

        val planned = planner.plan(AgentConfig(maxTokens = 256), history)

        assertTrue(planned.any { it.content.orEmpty().contains("recent-1") })
        assertTrue(planned.any { it.content.orEmpty().contains("recent-2") })
        assertTrue(planned.last().content.orEmpty().contains("recent-2"))
    }

    @Test
    fun plannerDropsLeadingNonUserMessagesAfterTrimming() {
        val history = listOf(
            ChatMessage(role = MessageRole.ASSISTANT, content = "assistant intro", sessionId = "session-1"),
            ChatMessage(role = MessageRole.TOOL, content = "tool intro", sessionId = "session-1"),
            ChatMessage(role = MessageRole.USER, content = "real user start", sessionId = "session-1")
        )

        val planned = planner.plan(AgentConfig(maxTokens = 256), history)

        assertEquals(MessageRole.USER, planned.first().role)
    }
}
