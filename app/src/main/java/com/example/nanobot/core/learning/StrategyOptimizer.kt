package com.example.nanobot.core.learning

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StrategyOptimizer @Inject constructor(
    private val analyzer: BehaviorAnalyzer
) {
    suspend fun generateStrategyHints(): String? {
        val profile = analyzer.analyze() ?: return null

        return buildString {
            appendLine("## Learned User Preferences")
            appendLine()

            val topTools = profile.toolPreferences.take(5)
            if (topTools.isNotEmpty()) {
                appendLine("### Frequently Used Tools")
                topTools.forEach { pref ->
                    appendLine("- ${pref.toolName}: used ${pref.usageCount} times, success rate ${(pref.successRate * 100).toInt()}%")
                }
                appendLine()
            }

            val riskyTools = profile.toolPreferences.filter { it.successRate < 0.5 && it.usageCount >= 3 }
            if (riskyTools.isNotEmpty()) {
                appendLine("### Tools to Use Carefully")
                riskyTools.forEach { pref ->
                    appendLine("- ${pref.toolName}: low success rate (${(pref.successRate * 100).toInt()}%), consider alternative approaches")
                }
                appendLine()
            }

            val topPatterns = profile.commonTaskPatterns.take(3)
            if (topPatterns.isNotEmpty()) {
                appendLine("### Common Task Patterns")
                topPatterns.forEach { pattern ->
                    appendLine("- ${pattern.toolSequence.joinToString(" -> ")} (seen ${pattern.frequency} times)")
                }
                appendLine()
            }

            when (profile.preferredComplexity) {
                ComplexityPreference.CONCISE -> appendLine("User prefers concise, to-the-point responses.")
                ComplexityPreference.DETAILED -> appendLine("User prefers detailed, thorough explanations.")
                ComplexityPreference.BALANCED -> Unit
            }

            if (profile.feedbackTrends.correctionRate > 0.3) {
                appendLine()
                appendLine("Note: High correction rate detected (${(profile.feedbackTrends.correctionRate * 100).toInt()}%). Ask for confirmation before irreversible actions.")
            }
        }.trimEnd().ifBlank { null }
    }

    suspend fun recommendToolSequence(taskDescription: String): List<String>? {
        val profile = analyzer.analyze() ?: return null
        val keywords = taskDescription.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        return profile.commonTaskPatterns
            .maxByOrNull { pattern ->
                pattern.toolSequence.count { tool -> keywords.any { keyword -> tool.contains(keyword, ignoreCase = true) } }
            }
            ?.toolSequence
    }
}
