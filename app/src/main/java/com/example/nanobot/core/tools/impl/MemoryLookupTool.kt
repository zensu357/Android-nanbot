package com.example.nanobot.core.tools.impl

import com.example.nanobot.core.memory.MemorySearchScorer
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.model.toConfidencePercent
import com.example.nanobot.core.model.MemorySummary
import com.example.nanobot.core.tools.AgentTool
import com.example.nanobot.core.tools.ToolAccessCategory
import com.example.nanobot.domain.repository.MemoryRepository
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class MemoryLookupTool @Inject constructor(
    private val memoryRepository: MemoryRepository
) : AgentTool {
    override val name: String = "memory_lookup"
    override val description: String = "Searches saved memory facts and summaries with ranked keyword matching"
    override val accessCategory: ToolAccessCategory = ToolAccessCategory.LOCAL_READ_ONLY
    override val availabilityHint: String = "Best used when memory is enabled"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put("description", "Keyword query to search within memory")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("query")) })
    }

    override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
        val query = arguments["query"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        if (query.isBlank()) {
            return "The 'query' field is required for memory_lookup."
        }

        val preferredSessionId = runContext.sessionId
        val facts = memoryRepository.getFactsForQuery(query)
            .sortedByDescending { fact ->
                MemorySearchScorer.score(
                    query = query,
                    text = fact.fact,
                    updatedAt = fact.updatedAt,
                    sourceSessionId = fact.sourceSessionId,
                    preferredSessionId = preferredSessionId
                )
            }
            .take(6)
        val summaries = memoryRepository.observeSummariesSnapshot()
            .map { summary ->
                summary to MemorySearchScorer.score(
                    query = query,
                    text = summary.summary,
                    updatedAt = summary.updatedAt,
                    sourceSessionId = summary.sessionId,
                    preferredSessionId = preferredSessionId
                )
            }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { (_, score) -> score }
            .map { (summary, _) -> summary }
            .take(3)

        if (facts.isEmpty() && summaries.isEmpty()) {
            return "No memory entries matched '$query'."
        }

        val sessionFacts = facts.filter { it.sourceSessionId == preferredSessionId }
        val otherFacts = facts.filterNot { it.sourceSessionId == preferredSessionId }
        val sessionSummaries = summaries.filter { it.sessionId == preferredSessionId }
        val otherSummaries = summaries.filterNot { it.sessionId == preferredSessionId }

        return buildString {
            appendSummarySection("Current session summaries", sessionSummaries)
            if (sessionFacts.isNotEmpty()) {
                if (isNotEmpty()) appendLine()
                appendLine("Current session facts:")
                sessionFacts.forEach { fact ->
                    appendLine("- ${formatFact(fact.fact.take(180), fact.confidence, fact.provenance.evidenceExcerpt)}")
                }
            }
            if (otherFacts.isNotEmpty()) {
                if (isNotEmpty()) appendLine()
                appendLine("Other matching facts:")
                otherFacts.forEach { fact ->
                    appendLine("- ${formatFact(fact.fact.take(180), fact.confidence, fact.provenance.evidenceExcerpt)}")
                }
            }
            if (otherSummaries.isNotEmpty()) {
                if (isNotEmpty()) appendLine()
                appendSummarySection("Other matching summaries", otherSummaries)
            }
        }.trim()
    }

    private fun StringBuilder.appendSummarySection(title: String, summaries: List<MemorySummary>) {
        if (summaries.isEmpty()) return
        appendLine(title)
        summaries.forEach { summary ->
            appendLine("- ${formatFact(summary.summary.take(220), summary.confidence, summary.provenance.evidenceExcerpt)}")
        }
    }

    private fun formatFact(text: String, confidence: Float, evidenceExcerpt: String?): String {
        val confidenceLabel = "confidence ${confidence.toConfidencePercent()}%"
        val evidenceLabel = evidenceExcerpt?.takeIf { it.isNotBlank() }?.let { "evidence: ${it.take(120)}" } ?: "evidence: none"
        return "$text ($confidenceLabel, $evidenceLabel)"
    }
}
