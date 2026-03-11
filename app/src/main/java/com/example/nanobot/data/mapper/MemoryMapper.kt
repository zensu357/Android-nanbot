package com.example.nanobot.data.mapper

import com.example.nanobot.core.database.entity.MemoryFactEntity
import com.example.nanobot.core.database.entity.MemorySummaryEntity
import com.example.nanobot.core.model.MemoryFact
import com.example.nanobot.core.model.MemoryProvenance
import com.example.nanobot.core.model.MemorySummary
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val memoryJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

fun MemoryFactEntity.toModel(): MemoryFact = MemoryFact(
    id = id,
    fact = fact,
    sourceSessionId = sourceSessionId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    confidence = confidence,
    provenance = MemoryProvenance(
        messageIds = decodeMessageIds(provenanceMessageIdsJson),
        evidenceExcerpt = provenanceExcerpt,
        sourceKind = provenanceSourceKind,
        extractor = provenanceExtractor
    )
)

fun MemoryFact.toEntity(): MemoryFactEntity = MemoryFactEntity(
    id = id,
    fact = fact,
    sourceSessionId = sourceSessionId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    confidence = confidence,
    provenanceMessageIdsJson = encodeMessageIds(provenance.messageIds),
    provenanceExcerpt = provenance.evidenceExcerpt,
    provenanceSourceKind = provenance.sourceKind,
    provenanceExtractor = provenance.extractor
)

fun MemorySummaryEntity.toModel(): MemorySummary = MemorySummary(
    sessionId = sessionId,
    summary = summary,
    updatedAt = updatedAt,
    sourceMessageCount = sourceMessageCount,
    confidence = confidence,
    provenance = MemoryProvenance(
        messageIds = decodeMessageIds(provenanceMessageIdsJson),
        evidenceExcerpt = provenanceExcerpt,
        sourceKind = provenanceSourceKind,
        extractor = provenanceExtractor
    )
)

fun MemorySummary.toEntity(): MemorySummaryEntity = MemorySummaryEntity(
    sessionId = sessionId,
    summary = summary,
    updatedAt = updatedAt,
    sourceMessageCount = sourceMessageCount,
    confidence = confidence,
    provenanceMessageIdsJson = encodeMessageIds(provenance.messageIds),
    provenanceExcerpt = provenance.evidenceExcerpt,
    provenanceSourceKind = provenance.sourceKind,
    provenanceExtractor = provenance.extractor
)

private fun encodeMessageIds(messageIds: List<String>): String {
    return memoryJson.encodeToString(ListSerializer(String.serializer()), messageIds)
}

private fun decodeMessageIds(raw: String): List<String> {
    return runCatching {
        memoryJson.decodeFromString(ListSerializer(String.serializer()), raw)
    }.getOrDefault(emptyList())
}
