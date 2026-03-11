package com.example.nanobot.core.ai

import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.MemoryFact
import com.example.nanobot.core.model.MemorySummary
import javax.inject.Inject

class MemoryPromptBuilder @Inject constructor() {
    fun build(
        sessionId: String,
        historyWindow: List<ChatMessage>,
        existingSummary: MemorySummary?,
        existingFacts: List<MemoryFact>
    ): String {
        return buildString {
            appendLine("You are consolidating memory for an Android-native assistant.")
            appendLine("Summarize the session and extract only stable, useful long-term facts.")
            appendLine("Do not include transient requests, one-off tool outputs, or speculative facts.")
            appendLine("For each summary/fact, include confidence and short evidence provenance grounded in the conversation.")
            appendLine()
            appendLine("Return valid JSON with this exact shape:")
            appendLine("{\"updatedSummary\":\"...\",\"summaryConfidence\":0.0,\"summaryEvidenceExcerpt\":\"...\",\"summarySourceMessageIds\":[\"...\"],\"structuredFacts\":[{\"fact\":\"...\",\"confidence\":0.0,\"evidenceExcerpt\":\"...\",\"sourceMessageIds\":[\"...\"]}],\"candidateFacts\":[\"...\"]}")
            appendLine()
            appendLine("Session ID: $sessionId")
            appendLine()
            appendLine("Existing summary:")
            appendLine(existingSummary?.summary ?: "(none)")
            existingSummary?.let {
                appendLine("Existing summary confidence: ${it.confidence}")
                appendLine("Existing summary provenance messages: ${it.provenance.messageIds.joinToString().ifBlank { "(none)" }}")
            }
            appendLine()
            appendLine("Existing long-term facts:")
            if (existingFacts.isEmpty()) {
                appendLine("(none)")
            } else {
                existingFacts.take(10).forEach {
                    appendLine("- ${it.fact} [confidence=${it.confidence}, evidence=${it.provenance.evidenceExcerpt ?: "(none)"}]")
                }
            }
            appendLine()
            appendLine("Recent session history:")
            historyWindow.forEach { message ->
                appendLine("- [${message.id}] ${message.role.name}: ${message.content.orEmpty().take(240)}")
            }
            appendLine()
            appendLine("Rules:")
            appendLine("- Keep updatedSummary concise but specific.")
            appendLine("- candidateFacts must contain only stable, reusable facts.")
            appendLine("- structuredFacts should mirror candidateFacts when possible and include confidence in the 0.0 to 1.0 range.")
            appendLine("- summarySourceMessageIds and sourceMessageIds must reference message ids from the history when evidence exists.")
            appendLine("- evidenceExcerpt should be a short quote or paraphrase from the supporting messages.")
            appendLine("- If a new fact updates an older preference, emit only the newer fact instead of both.")
            appendLine("- If no strong facts exist, return an empty array.")
            appendLine("- Output JSON only. No markdown fences.")
        }.trim()
    }
}
