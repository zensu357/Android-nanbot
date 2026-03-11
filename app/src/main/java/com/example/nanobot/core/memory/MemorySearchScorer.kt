package com.example.nanobot.core.memory

object MemorySearchScorer {
    fun score(
        query: String,
        text: String,
        updatedAt: Long,
        confidence: Float = 0.6f,
        sourceSessionId: String? = null,
        preferredSessionId: String? = null
    ): Int {
        val normalizedQuery = query.trim().lowercase()
        val normalizedText = text.lowercase()
        if (normalizedQuery.isBlank() || normalizedText.isBlank()) return 0

        val tokens = normalizedQuery
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()

        val exactPhraseBonus = if (normalizedText.contains(normalizedQuery)) 100 else 0
        val tokenScore = tokens.fold(0) { acc, token ->
            acc + when {
                normalizedText.contains(" $token ") -> 24
                normalizedText.startsWith("$token ") || normalizedText.endsWith(" $token") -> 20
                normalizedText.contains(token) -> 12
                else -> 0
            }
        }
        if (exactPhraseBonus == 0 && tokenScore == 0) return 0
        val sessionBonus: Int = if (!preferredSessionId.isNullOrBlank() && preferredSessionId == sourceSessionId) 30 else 0
        val recencyBonus: Int = ((updatedAt / 1_000L) % 17L).toInt()
        val confidenceBonus: Int = (confidence.coerceIn(0f, 1f) * 20f).toInt()

        return exactPhraseBonus + tokenScore + sessionBonus + recencyBonus + confidenceBonus
    }
}
