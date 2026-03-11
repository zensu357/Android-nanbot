package com.example.nanobot.core.ai

import com.example.nanobot.core.model.AgentConfig
import javax.inject.Inject

enum class PromptSectionCategory {
    CORE,
    SKILL_CATALOG,
    SKILL_EXPANDED,
    CUSTOM,
    MEMORY,
    CAPABILITIES
}

data class PlannedPromptSection(
    val section: PromptSection,
    val category: PromptSectionCategory,
    val priority: Int
)

class ContextBudgetPlanner @Inject constructor() {
    fun apply(config: AgentConfig, sections: List<PlannedPromptSection>): List<PromptSection> {
        val totalBudget = totalBudgetFor(config)
        val categoryBudgets = categoryBudgets(totalBudget)
        val trimmedByCategory = sections
            .groupBy { it.category }
            .flatMap { (category, categorySections) ->
                trimCategory(
                    sections = categorySections.sortedByDescending { it.priority },
                    maxChars = categoryBudgets[category] ?: totalBudget
                )
            }
            .associateBy { it.title }

        val ordered = sections.mapNotNull { trimmedByCategory[it.section.title] }
        return trimOverall(ordered, totalBudget)
    }

    private fun totalBudgetFor(config: AgentConfig): Int {
        return (config.maxTokens * 2)
            .coerceAtLeast(2_400)
            .coerceAtMost(8_000)
    }

    private fun categoryBudgets(totalBudget: Int): Map<PromptSectionCategory, Int> {
        return mapOf(
            PromptSectionCategory.CORE to (totalBudget * 0.34).toInt(),
            PromptSectionCategory.SKILL_CATALOG to (totalBudget * 0.10).toInt(),
            PromptSectionCategory.SKILL_EXPANDED to (totalBudget * 0.18).toInt(),
            PromptSectionCategory.CUSTOM to (totalBudget * 0.12).toInt(),
            PromptSectionCategory.MEMORY to (totalBudget * 0.14).toInt(),
            PromptSectionCategory.CAPABILITIES to (totalBudget * 0.12).toInt()
        )
    }

    private fun trimCategory(sections: List<PlannedPromptSection>, maxChars: Int): List<PromptSection> {
        val result = mutableListOf<PromptSection>()
        var used = 0
        sections.forEach { planned ->
            val trimmed = trimSectionToBudget(planned.section, (maxChars - used).coerceAtLeast(0))
            if (!trimmed.isEmpty()) {
                result += trimmed
                used += trimmed.render().length + 2
            }
        }
        return result
    }

    private fun trimOverall(sections: List<PromptSection>, totalBudget: Int): List<PromptSection> {
        val result = mutableListOf<PromptSection>()
        var used = 0
        sections.forEach { section ->
            val remaining = (totalBudget - used).coerceAtLeast(0)
            val trimmed = trimSectionToBudget(section, remaining)
            if (!trimmed.isEmpty()) {
                result += trimmed
                used += trimmed.render().length + 2
            }
        }
        return result
    }

    private fun trimSectionToBudget(section: PromptSection, maxChars: Int): PromptSection {
        if (section.isEmpty() || maxChars <= section.title.length + 6) return PromptSection(section.title, emptyList())
        val bodyBudget = maxChars - section.title.length - 6
        val trimmedBody = mutableListOf<String>()
        var used = 0
        section.body.filter { it.isNotBlank() }.forEach { line ->
            val addition = if (trimmedBody.isEmpty()) line.length else line.length + 1
            if (used + addition <= bodyBudget) {
                trimmedBody += line
                used += addition
            } else if (trimmedBody.isNotEmpty()) {
                val remaining = (bodyBudget - used - 4).coerceAtLeast(0)
                if (remaining > 0) {
                    trimmedBody += line.take(remaining).trimEnd() + "..."
                }
                return PromptSection(section.title, trimmedBody)
            } else {
                trimmedBody += line.take(bodyBudget.coerceAtLeast(4) - 3).trimEnd() + "..."
                return PromptSection(section.title, trimmedBody)
            }
        }
        return PromptSection(section.title, trimmedBody)
    }
}
