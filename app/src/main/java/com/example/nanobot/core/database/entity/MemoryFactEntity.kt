package com.example.nanobot.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_facts")
data class MemoryFactEntity(
    @PrimaryKey val id: String,
    val fact: String,
    val sourceSessionId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val confidence: Float = 0.6f,
    val provenanceMessageIdsJson: String = "[]",
    val provenanceExcerpt: String? = null,
    val provenanceSourceKind: String = "conversation",
    val provenanceExtractor: String = "llm_memory_consolidator"
)
