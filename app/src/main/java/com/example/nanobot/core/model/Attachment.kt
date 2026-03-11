package com.example.nanobot.core.model

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
enum class AttachmentType {
    IMAGE,
    FILE
}

@Serializable
data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val type: AttachmentType,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localPath: String
)
