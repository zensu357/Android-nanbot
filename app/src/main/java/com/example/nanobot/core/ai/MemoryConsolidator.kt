package com.example.nanobot.core.ai

import com.example.nanobot.core.memory.MemoryFactGovernance
import com.example.nanobot.core.memory.VisualMemoryExtractor
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AttachmentType
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.DEFAULT_MEMORY_CONFIDENCE
import com.example.nanobot.core.model.MemoryCandidateFact
import com.example.nanobot.core.model.LlmChatRequest
import com.example.nanobot.core.model.LlmMessageDto
import com.example.nanobot.core.model.MemoryConsolidationResult
import com.example.nanobot.core.model.MemoryFact
import com.example.nanobot.core.model.MemoryProvenance
import com.example.nanobot.core.model.MemorySummary
import com.example.nanobot.domain.repository.ChatRepository
import com.example.nanobot.domain.repository.MemoryRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

class MemoryConsolidator @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val chatRepository: ChatRepository,
    private val memoryPromptBuilder: MemoryPromptBuilder,
    private val visualMemoryExtractor: VisualMemoryExtractor
) {
    private val parserJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun shouldConsolidate(
        sessionId: String,
        historySize: Int,
        config: AgentConfig,
        minMessages: Int,
        minNewMessagesDelta: Int
    ): Boolean {
        if (!config.enableMemory) return false
        if (historySize < minMessages) return false

        val existingSummaryCount = getSummarySourceMessageCount(sessionId)
        return existingSummaryCount == null || historySize - existingSummaryCount >= minNewMessagesDelta
    }

    suspend fun consolidate(sessionId: String, history: List<ChatMessage>, config: AgentConfig): Boolean {
        if (!config.enableMemory) return false

        val boundedHistory = history.takeLast(config.memoryWindow.coerceAtLeast(10))
        if (boundedHistory.size < 4) return false

        val existingSummary = memoryRepository.getSummaryForSession(sessionId)
        val existingFacts = memoryRepository.getFacts().take(20)

        val prompt = memoryPromptBuilder.build(
            sessionId = sessionId,
            historyWindow = boundedHistory,
            existingSummary = existingSummary,
            existingFacts = existingFacts
        )

        val responseText = runCatching {
            chatRepository.completeChat(
                request = LlmChatRequest(
                    model = config.model,
                    messages = listOf(
                        LlmMessageDto(
                            role = "system",
                            content = JsonPrimitive(
                                "You are a memory consolidation engine. Return only valid JSON."
                            )
                        ),
                        LlmMessageDto(
                            role = "user",
                            content = JsonPrimitive(prompt)
                        )
                    ),
                    temperature = 0.1,
                    maxTokens = minOf(config.maxTokens, 1200),
                    tools = null,
                    toolChoice = null
                ),
                config = config
            ).content.orEmpty()
        }.getOrElse {
            return false
        }

        val result = parseResult(responseText) ?: return false
        val visualCandidates = extractVisualCandidates(
            config = config,
            history = boundedHistory,
            existingFacts = existingFacts
        )
        persistResult(
            sessionId = sessionId,
            messageCount = history.size,
            result = result,
            existingFacts = existingFacts,
            history = boundedHistory,
            visualCandidates = visualCandidates
        )
        return true
    }

    suspend fun buildMemoryContext(sessionId: String): String? {
        val summary = memoryRepository.getSummaryForSession(sessionId)
        val sessionFacts = memoryRepository.getFactsForSession(sessionId).take(3)
        val longTermFacts = memoryRepository.getFacts()
            .filter { it.sourceSessionId != sessionId }
            .take(5)

        if (summary == null && sessionFacts.isEmpty() && longTermFacts.isEmpty()) {
            return null
        }

        return buildString {
            appendLine("[Memory]")
            if (summary != null) {
                appendLine("Session summary:")
                appendLine(formatSummaryForContext(summary))
            }
            if (sessionFacts.isNotEmpty()) {
                appendLine()
                appendLine("Current session facts:")
                sessionFacts.forEach { appendLine("- ${formatFactForContext(it)}") }
            }
            if (longTermFacts.isNotEmpty()) {
                appendLine()
                appendLine("Long-term user facts:")
                longTermFacts.forEach { appendLine("- ${formatFactForContext(it)}") }
            }
        }.trim()
    }

    suspend fun getSummarySourceMessageCount(sessionId: String): Int? {
        return memoryRepository.getSummaryForSession(sessionId)?.sourceMessageCount
    }

    private suspend fun persistResult(
        sessionId: String,
        messageCount: Int,
        result: MemoryConsolidationResult,
        existingFacts: List<MemoryFact>,
        history: List<ChatMessage>,
        visualCandidates: List<PersistableMemoryCandidate>
    ) {
        if (result.updatedSummary.isNotBlank()) {
            memoryRepository.upsertSummary(
                MemorySummary(
                    sessionId = sessionId,
                    summary = result.updatedSummary.trim(),
                    updatedAt = System.currentTimeMillis(),
                    sourceMessageCount = messageCount,
                    confidence = normalizeConfidence(result.summaryConfidence),
                    provenance = MemoryProvenance(
                        messageIds = filterKnownMessageIds(result.summarySourceMessageIds, history),
                        evidenceExcerpt = result.summaryEvidenceExcerpt?.trim()?.takeIf { it.isNotBlank() }?.take(240)
                            ?: history.lastMeaningfulExcerpt(),
                        sourceKind = "conversation_summary",
                        extractor = "llm_memory_consolidator"
                    )
                )
            )
        }

        val now = System.currentTimeMillis()
        val mutableFacts = existingFacts.toMutableList()
        val existingByNormalized = existingFacts.associateBy { normalizeFact(it.fact) }.toMutableMap()
        val candidateFacts = buildCandidateFacts(result, history).map { candidate ->
            PersistableMemoryCandidate(
                fact = candidate.fact,
                confidence = candidate.confidence,
                evidenceExcerpt = candidate.evidenceExcerpt,
                sourceMessageIds = candidate.sourceMessageIds,
                sourceKind = CONVERSATION_FACT_SOURCE_KIND,
                extractor = CONVERSATION_FACT_EXTRACTOR
            )
        } + visualCandidates
        candidateFacts
            .map { candidate -> candidate.copy(fact = candidate.fact.trim()) }
            .filter { candidate -> candidate.fact.length >= 8 }
            .forEach { candidate ->
                val fact = candidate.fact
                val normalized = normalizeFact(fact)
                if (normalized.isBlank()) return@forEach

                val existing = existingByNormalized[normalized]
                    ?: MemoryFactGovernance.findReplacementCandidate(mutableFacts, fact)
                if (existing != null) {
                    val updatedFact = existing.copy(
                        fact = fact.take(220),
                        sourceSessionId = sessionId,
                        updatedAt = now,
                        confidence = normalizeConfidence(candidate.confidence),
                        provenance = MemoryProvenance(
                            messageIds = filterKnownMessageIds(candidate.sourceMessageIds, history),
                            evidenceExcerpt = candidate.evidenceExcerpt?.trim()?.takeIf { it.isNotBlank() }?.take(240)
                                ?: history.lastMeaningfulExcerpt(),
                            sourceKind = candidate.sourceKind,
                            extractor = candidate.extractor
                        )
                    )
                    mutableFacts.removeAll { it.id == existing.id }
                    mutableFacts += updatedFact
                    existingByNormalized.remove(normalizeFact(existing.fact))
                    existingByNormalized[normalized] = updatedFact
                    memoryRepository.upsertFact(updatedFact)
                } else {
                    val newFact = MemoryFact(
                        id = UUID.randomUUID().toString(),
                        fact = fact.take(220),
                        sourceSessionId = sessionId,
                        createdAt = now,
                        updatedAt = now,
                        confidence = normalizeConfidence(candidate.confidence),
                        provenance = MemoryProvenance(
                            messageIds = filterKnownMessageIds(candidate.sourceMessageIds, history),
                            evidenceExcerpt = candidate.evidenceExcerpt?.trim()?.takeIf { it.isNotBlank() }?.take(240)
                                ?: history.lastMeaningfulExcerpt(),
                            sourceKind = candidate.sourceKind,
                            extractor = candidate.extractor
                        )
                    )
                    mutableFacts += newFact
                    existingByNormalized[normalized] = newFact
                    memoryRepository.upsertFact(
                        newFact
                    )
                }
            }
        memoryRepository.pruneFacts(MAX_MEMORY_FACTS)
    }

    private suspend fun extractVisualCandidates(
        config: AgentConfig,
        history: List<ChatMessage>,
        existingFacts: List<MemoryFact>
    ): List<PersistableMemoryCandidate> {
        if (!config.enableVisualMemory) return emptyList()

        val screenshotMessage = history.asReversed().firstOrNull { message ->
            message.role == com.example.nanobot.core.model.MessageRole.TOOL &&
                message.attachments.any { it.type == AttachmentType.IMAGE }
        } ?: return emptyList()

        if (existingFacts.any { fact ->
                fact.provenance.sourceKind == VISUAL_FACT_SOURCE_KIND && screenshotMessage.id in fact.provenance.messageIds
            }
        ) {
            return emptyList()
        }

        val screenshot = screenshotMessage.attachments.lastOrNull { it.type == AttachmentType.IMAGE } ?: return emptyList()
        val extractedFacts = runCatching {
            visualMemoryExtractor.extractFacts(
                screenshot = screenshot,
                contextHint = buildVisualContextHint(history, screenshotMessage),
                config = config
            )
        }.getOrDefault(emptyList())

        return extractedFacts
            .filter { it.confidence >= VISUAL_FACT_MIN_CONFIDENCE }
            .map { fact ->
                PersistableMemoryCandidate(
                    fact = fact.fact,
                    confidence = fact.confidence.toFloat(),
                    evidenceExcerpt = screenshotMessage.content?.trim()?.takeIf { it.isNotBlank() } ?: history.lastMeaningfulExcerpt(),
                    sourceMessageIds = listOf(screenshotMessage.id),
                    sourceKind = VISUAL_FACT_SOURCE_KIND,
                    extractor = VISUAL_FACT_EXTRACTOR
                )
            }
    }

    private fun buildVisualContextHint(
        history: List<ChatMessage>,
        screenshotMessage: ChatMessage
    ): String {
        return history
            .filterNot { it.id == screenshotMessage.id }
            .takeLast(4)
            .mapNotNull { message ->
                val content = message.content?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                "${message.role.name.lowercase()}: ${content.take(200)}"
            }
            .plus(screenshotMessage.toolName?.takeIf { it.isNotBlank() }?.let { "tool: $it" })
            .joinToString("\n")
            .take(800)
    }

    private fun parseResult(raw: String): MemoryConsolidationResult? {
        return runCatching {
            parserJson.decodeFromString<MemoryConsolidationResult>(raw.trim())
        }.getOrNull()
    }

    private fun buildCandidateFacts(
        result: MemoryConsolidationResult,
        history: List<ChatMessage>
    ): List<MemoryCandidateFact> {
        val structured = result.structuredFacts
            .mapNotNull { candidate -> candidate.fact.takeIf { it.isNotBlank() }?.let { candidate.copy(fact = it) } }
        if (structured.isNotEmpty()) return structured
        return result.candidateFacts.map { fact ->
            MemoryCandidateFact(
                fact = fact,
                confidence = inferFallbackConfidence(fact, history),
                evidenceExcerpt = history.lastMeaningfulExcerpt(),
                sourceMessageIds = history.takeLast(2).map { it.id }
            )
        }
    }

    private fun inferFallbackConfidence(fact: String, history: List<ChatMessage>): Float {
        val normalizedFact = normalizeFact(fact)
        val supportCount = history.count { message ->
            val content = message.content.orEmpty().lowercase()
            normalizedFact.split(' ').count { token -> token.length >= 4 && token in content } >= 2
        }
        return when {
            supportCount >= 2 -> 0.8f
            supportCount == 1 -> 0.65f
            else -> DEFAULT_MEMORY_CONFIDENCE
        }
    }

    private fun filterKnownMessageIds(candidateIds: List<String>, history: List<ChatMessage>): List<String> {
        val knownIds = history.map { it.id }.toSet()
        return candidateIds.filter { it in knownIds }
    }

    private fun normalizeConfidence(value: Float): Float = value.coerceIn(0f, 1f)

    private fun normalizeFact(value: String): String = value.trim().lowercase()

    private fun formatFactForContext(fact: MemoryFact): String {
        val confidence = "confidence=${fact.confidence}"
        val provenance = fact.provenance.evidenceExcerpt?.let { "evidence=$it" } ?: "evidence=(none)"
        return "${fact.fact} [$confidence, $provenance]"
    }

    private fun formatSummaryForContext(summary: MemorySummary): String {
        val confidence = "confidence=${summary.confidence}"
        val provenance = summary.provenance.evidenceExcerpt?.let { "evidence=$it" } ?: "evidence=(none)"
        return "${summary.summary} [$confidence, $provenance]"
    }

    private fun List<ChatMessage>.lastMeaningfulExcerpt(): String? {
        return asReversed()
            .firstOrNull { !it.content.isNullOrBlank() }
            ?.content
            ?.trim()
            ?.take(240)
            ?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val MAX_MEMORY_FACTS = 200
        const val CONVERSATION_FACT_SOURCE_KIND = "conversation_fact"
        const val CONVERSATION_FACT_EXTRACTOR = "llm_memory_consolidator"
        const val VISUAL_FACT_SOURCE_KIND = "visual_extraction"
        const val VISUAL_FACT_EXTRACTOR = "visual_memory_extractor"
        const val VISUAL_FACT_MIN_CONFIDENCE = 0.7
    }
}

private data class PersistableMemoryCandidate(
    val fact: String,
    val confidence: Float,
    val evidenceExcerpt: String?,
    val sourceMessageIds: List<String>,
    val sourceKind: String,
    val extractor: String
)
