package com.example.nanobot.data.mapper

import com.example.nanobot.core.database.entity.MessageEntity
import com.example.nanobot.core.model.Attachment
import com.example.nanobot.core.model.AttachmentType
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.MessageRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatMapperTest {
    @Test
    fun chatMessageRoundTripsAttachmentsThroughEntity() {
        val message = ChatMessage(
            id = "message-1",
            sessionId = "session-1",
            role = MessageRole.USER,
            content = "Here is an image",
            attachments = listOf(
                Attachment(
                    id = "attachment-1",
                    type = AttachmentType.IMAGE,
                    displayName = "photo.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 2048,
                    localPath = "attachments/images/photo.jpg"
                )
            ),
            createdAt = 123L
        )

        val entity = message.toEntity()
        val restored = entity.toModel()

        assertTrue(entity.attachmentsJson.orEmpty().contains("photo.jpg"))
        assertEquals(message.attachments, restored.attachments)
        assertEquals(message.content, restored.content)
    }

    @Test
    fun entityWithoutAttachmentsMapsToEmptyAttachmentList() {
        val entity = MessageEntity(
            id = "message-2",
            sessionId = "session-1",
            role = MessageRole.ASSISTANT.name,
            content = "No attachment",
            attachmentsJson = null,
            toolCallId = null,
            toolName = null,
            toolCallsJson = null,
            protectedContext = false,
            finishReason = null,
            createdAt = 456L
        )

        val restored = entity.toModel()

        assertTrue(restored.attachments.isEmpty())
        assertEquals("No attachment", restored.content)
    }
}
