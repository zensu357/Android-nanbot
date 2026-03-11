package com.example.nanobot.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_summaries")
data class MemorySummaryEntity(
    @PrimaryKey val sessionId: String,
    val summary: String,
    val updatedAt: Long,
    val sourceMessageCount: Int,
    val confidence: Float = 0.6f,
    val provenanceMessageIdsJson: String = "[]",
    val provenanceExcerpt: String? = null,
    val provenanceSourceKind: String = "conversation",
    val provenanceExtractor: String = "llm_memory_consolidator"
)
