package com.example.nanobot.feature.chat

import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.MessageRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolMessagePresentationTest {
    @Test
    fun classifyWebSearchToolMessage() {
        val presentation = presentToolMessage(
            ChatMessage(
                sessionId = "session-1",
                role = MessageRole.TOOL,
                toolName = "web_search",
                content = "Query: SSE index"
            )
        )

        assertEquals(ToolMessageKind.WEB_SEARCH, presentation.kind)
        assertEquals("Web Search", presentation.badgeLabel)
    }

    @Test
    fun summaryTextUsesCategoryPrefixAndPreview() {
        val summary = toolSummaryText(
            ChatMessage(
                sessionId = "session-1",
                role = MessageRole.TOOL,
                toolName = "delegate_task",
                content = "Subagent Session ID: child-1\nSummary: Delegated report completed successfully."
            )
        )

        assertTrue(summary.startsWith("Subtask:"))
        assertTrue(summary.contains("Delegated report completed"))
    }
}
