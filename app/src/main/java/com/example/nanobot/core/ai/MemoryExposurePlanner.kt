package com.example.nanobot.core.ai

import com.example.nanobot.core.memory.MemorySearchScorer
import com.example.nanobot.core.model.MemoryFact
import com.example.nanobot.core.model.MemorySummary
import com.example.nanobot.domain.repository.MemoryRepository
import javax.inject.Inject

class MemoryExposurePlanner @Inject constructor(
    private val memoryRepository: MemoryRepository
) {
    suspend fun buildContext(sessionId: String, latestUserInput: String): String? {
        return buildWithDiagnostics(sessionId, latestUserInput).context
    }

    suspend fun buildWithDiagnostics(sessionId: String, latestUserInput: String): MemoryExposureResult {
        val summary = memoryRepository.getSummaryForSession(sessionId)
        val allFacts = memoryRepository.getFacts()
        val sessionFacts = selectSessionFacts(sessionId, latestUserInput, allFacts)
        val relevantLongTermFacts = selectLongTermFacts(sessionId, latestUserInput, allFacts)

        if (summary == null && sessionFacts.isEmpty() && relevantLongTermFacts.isEmpty()) {
            return MemoryExposureResult(
                context = null,
                summaryIncluded = false,
                sessionFactCount = 0,
                longTermFactCount = 0
            )
        }

        val context = buildString {
            appendLine("[Memory]")
            summary?.let {
                appendLine("Session summary:")
                appendLine(formatSummary(it))
            }
            if (sessionFacts.isNotEmpty()) {
                appendLine()
                appendLine("Relevant current session facts:")
                sessionFacts.forEach { appendLine("- ${formatFact(it)}") }
            }
            if (relevantLongTermFacts.isNotEmpty()) {
                appendLine()
                appendLine("Relevant long-term facts:")
                relevantLongTermFacts.forEach { appendLine("- ${formatFact(it)}") }
            }
        }.trim()
        return MemoryExposureResult(
            context = context,
            summaryIncluded = summary != null,
            sessionFactCount = sessionFacts.size,
            longTermFactCount = relevantLongTermFacts.size
        )
    }

    private fun selectSessionFacts(sessionId: String, latestUserInput: String, facts: List<MemoryFact>): List<MemoryFact> {
        val scoped = facts.filter { it.sourceSessionId == sessionId }
        return rankFacts(scoped, latestUserInput, preferredSessionId = sessionId)
            .take(MAX_SESSION_FACTS)
            .ifEmpty { scoped.sortedByDescending { it.updatedAt }.take(FALLBACK_SESSION_FACTS) }
    }

    private fun selectLongTermFacts(sessionId: String, latestUserInput: String, facts: List<MemoryFact>): List<MemoryFact> {
        return rankFacts(
            facts = facts.filter { it.sourceSessionId != sessionId },
            latestUserInput = latestUserInput,
            preferredSessionId = null
        ).take(MAX_LONG_TERM_FACTS)
    }

    private fun rankFacts(
        facts: List<MemoryFact>,
        latestUserInput: String,
        preferredSessionId: String?
    ): List<MemoryFact> {
        val normalizedInput = latestUserInput.trim().lowercase()
        return facts
            .map { fact ->
                fact to MemorySearchScorer.score(
                    query = normalizedInput,
                    text = fact.fact,
                    updatedAt = fact.updatedAt,
                    confidence = fact.confidence,
                    sourceSessionId = fact.sourceSessionId,
                    preferredSessionId = preferredSessionId
                )
            }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { (_, score) -> score }
            .map { (fact, _) -> fact }
    }

    private fun formatSummary(summary: MemorySummary): String {
        val evidence = summary.provenance.evidenceExcerpt?.takeIf { it.isNotBlank() }?.take(80) ?: "none"
        return "${summary.summary} [confidence=${summary.confidence}, evidence=$evidence]"
    }

    private fun formatFact(fact: MemoryFact): String {
        val evidence = fact.provenance.evidenceExcerpt?.takeIf { it.isNotBlank() }?.take(80) ?: "none"
        return "${fact.fact} [confidence=${fact.confidence}, evidence=$evidence]"
    }

    private companion object {
        const val MAX_SESSION_FACTS = 2
        const val FALLBACK_SESSION_FACTS = 1
        const val MAX_LONG_TERM_FACTS = 3
    }
}
