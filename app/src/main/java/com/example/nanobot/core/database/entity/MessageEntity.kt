package com.example.nanobot.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val content: String?,
    val attachmentsJson: String?,
    val toolCallId: String?,
    val toolName: String?,
    val toolCallsJson: String?,
    val protectedContext: Boolean,
    val finishReason: String?,
    val createdAt: Long
)
