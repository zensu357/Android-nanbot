package com.example.nanobot.core.model

import kotlinx.serialization.Serializable

@Serializable
data class MemoryCandidateFact(
    val fact: String,
    val confidence: Float = DEFAULT_MEMORY_CONFIDENCE,
    val evidenceExcerpt: String? = null,
    val sourceMessageIds: List<String> = emptyList()
)

@Serializable
data class MemoryConsolidationResult(
    val updatedSummary: String,
    val candidateFacts: List<String> = emptyList(),
    val structuredFacts: List<MemoryCandidateFact> = emptyList(),
    val summaryConfidence: Float = DEFAULT_MEMORY_CONFIDENCE,
    val summaryEvidenceExcerpt: String? = null,
    val summarySourceMessageIds: List<String> = emptyList()
)
