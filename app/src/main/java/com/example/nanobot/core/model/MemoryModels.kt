package com.example.nanobot.core.model

import kotlin.math.roundToInt

data class MemoryProvenance(
    val messageIds: List<String> = emptyList(),
    val evidenceExcerpt: String? = null,
    val sourceKind: String = DEFAULT_MEMORY_SOURCE_KIND,
    val extractor: String = DEFAULT_MEMORY_EXTRACTOR
)

data class MemoryFact(
    val id: String,
    val fact: String,
    val sourceSessionId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val confidence: Float = DEFAULT_MEMORY_CONFIDENCE,
    val provenance: MemoryProvenance = MemoryProvenance()
)

data class MemorySummary(
    val sessionId: String,
    val summary: String,
    val updatedAt: Long,
    val sourceMessageCount: Int = 0,
    val confidence: Float = DEFAULT_MEMORY_CONFIDENCE,
    val provenance: MemoryProvenance = MemoryProvenance()
)

const val DEFAULT_MEMORY_CONFIDENCE = 0.6f
const val DEFAULT_MEMORY_SOURCE_KIND = "conversation"
const val DEFAULT_MEMORY_EXTRACTOR = "llm_memory_consolidator"

fun Float.toConfidencePercent(): Int = (coerceIn(0f, 1f) * 100f).roundToInt()
