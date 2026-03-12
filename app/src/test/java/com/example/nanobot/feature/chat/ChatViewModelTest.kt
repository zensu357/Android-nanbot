package com.example.nanobot.feature.chat

import com.example.nanobot.core.model.Attachment
import com.example.nanobot.core.model.AttachmentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatViewModelTest {
    @Test
    fun applySessionSelectionClearsPendingAttachmentsWhenSessionChanges() {
        val state = ChatUiState(
            sessionTitle = "Session One",
            pendingAttachments = listOf(
                Attachment(
                    id = "att-1",
                    type = AttachmentType.IMAGE,
                    displayName = "clipboard.png",
                    mimeType = "image/png",
                    sizeBytes = 10,
                    localPath = "attachments/images/clipboard.png"
                )
            ),
            errorMessage = "Old error"
        )

        val updated = state.applySessionSelection(sessionTitle = "Session Two", sessionChanged = true)

        assertEquals("Session Two", updated.sessionTitle)
        assertTrue(updated.pendingAttachments.isEmpty())
        assertNull(updated.errorMessage)
    }

    @Test
    fun applySessionSelectionKeepsPendingAttachmentsWithinSameSession() {
        val state = ChatUiState(
            sessionTitle = "Session One",
            pendingAttachments = listOf(
                Attachment(
                    id = "att-1",
                    type = AttachmentType.IMAGE,
                    displayName = "clipboard.png",
                    mimeType = "image/png",
                    sizeBytes = 10,
                    localPath = "attachments/images/clipboard.png"
                )
            ),
            errorMessage = "Old error"
        )

        val updated = state.applySessionSelection(sessionTitle = "Session One", sessionChanged = false)

        assertEquals(1, updated.pendingAttachments.size)
        assertEquals("Old error", updated.errorMessage)
    }

    @Test
    fun applySessionSelectionClearsExpandedToolMessagesWhenSessionChanges() {
        val state = ChatUiState(
            sessionTitle = "Session One",
            expandedToolMessageIds = setOf("tool-1", "tool-2")
        )

        val updated = state.applySessionSelection(sessionTitle = "Session Two", sessionChanged = true)

        assertTrue(updated.expandedToolMessageIds.isEmpty())
    }

    @Test
    fun toggleToolMessageExpansionAddsAndRemovesMessageId() {
        val initial = ChatUiState()

        val expanded = initial.toggleToolMessageExpansion("tool-1")
        val collapsed = expanded.toggleToolMessageExpansion("tool-1")

        assertEquals(setOf("tool-1"), expanded.expandedToolMessageIds)
        assertTrue(collapsed.expandedToolMessageIds.isEmpty())
    }
}
