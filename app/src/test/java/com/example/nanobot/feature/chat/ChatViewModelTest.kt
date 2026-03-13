package com.example.nanobot.feature.chat

import com.example.nanobot.core.model.Attachment
import com.example.nanobot.core.model.AttachmentType
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.MessageRole
import com.example.nanobot.core.skills.ActivatedSkillSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
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

    @Test
    fun hiddenSkillActivationMessageIsFilteredFromTranscript() {
        val hidden = ChatMessage(
            sessionId = "session-1",
            role = MessageRole.ASSISTANT,
            content = "<skill_content>",
            toolName = "activate_skill:release-notes",
            protectedContext = true
        )
        val visible = ChatMessage(
            sessionId = "session-1",
            role = MessageRole.ASSISTANT,
            content = "Regular reply"
        )

        assertTrue(hidden.isHiddenSkillActivationMessage())
        assertFalse(visible.isHiddenSkillActivationMessage())
    }

    @Test
    fun activeSkillUiStateCarriesActivationSource() {
        val userSkill = ActiveSkillUiState(
            name = "release-notes",
            title = "Release Notes",
            source = ActivatedSkillSource.USER
        )
        val modelSkill = ActiveSkillUiState(
            name = "android-refactor",
            title = "Android Refactor",
            source = ActivatedSkillSource.MODEL
        )

        assertEquals(ActivatedSkillSource.USER, userSkill.source)
        assertEquals(ActivatedSkillSource.MODEL, modelSkill.source)
    }
}
