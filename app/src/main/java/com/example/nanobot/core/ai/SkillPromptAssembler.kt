package com.example.nanobot.core.ai

import com.example.nanobot.core.skills.SkillDefinition
import javax.inject.Inject

class SkillPromptAssembler @Inject constructor() {
    fun buildCatalogSection(plan: SkillExposurePlan): PromptSection {
        return PromptSection(
            title = "Available Skills",
            body = plan.catalogSkills.map { skill ->
                val sourceLabel = if (skill.isImported) "imported" else "builtin"
                "- ${skill.title} [${skill.id}, $sourceLabel]: ${skill.catalogSummary()}"
            }
        )
    }

    fun buildExpandedSection(plan: SkillExposurePlan, latestUserInput: String): PromptSection {
        return PromptSection(
            title = "Activated Skill Instructions",
            body = buildList {
                plan.expandedSkills.forEach { skill ->
                    addAll(renderExpandedSkill(skill, latestUserInput))
                }
            }
        )
    }

    private fun renderExpandedSkill(skill: SkillDefinition, latestUserInput: String): List<String> {
        val sections = parseSections(skill.promptContent())
        val selectedSections = selectSections(sections, latestUserInput)
        val lines = buildList {
            add("Skill: ${skill.title}")
            skill.whenToUse.takeIf { it.isNotBlank() }?.let { add("When to use: $it") }
            skill.summaryPrompt.takeIf { it.isNotBlank() }?.let { add("Summary: $it") }
            if (selectedSections.isEmpty()) {
                add(trimToBudget(skill.promptContent(), skill.maxPromptChars))
            } else {
                selectedSections.forEach { (title, body) ->
                    add("$title:")
                    add(body)
                }
            }
        }
        return trimLinesToBudget(lines, skill.maxPromptChars)
    }

    private fun selectSections(
        sections: LinkedHashMap<String, String>,
        latestUserInput: String
    ): List<Pair<String, String>> {
        if (sections.isEmpty()) return emptyList()
        val input = latestUserInput.lowercase()
        val orderedKeys = buildList {
            if (sections.containsKey("Instructions")) add("Instructions")
            if (shouldIncludeWorkflow(input) && sections.containsKey("Workflow")) add("Workflow")
            if (shouldIncludeConstraints(input) && sections.containsKey("Constraints")) add("Constraints")
            if (shouldIncludeOutputContract(input) && sections.containsKey("Output Contract")) add("Output Contract")
            if (shouldIncludeExamples(input) && sections.containsKey("Examples")) add("Examples")
        }
        val fallbackKeys = listOf("Instructions", "Workflow", "Constraints")
            .filter { it in sections && it !in orderedKeys }
        return (orderedKeys + fallbackKeys)
            .distinct()
            .mapNotNull { key -> sections[key]?.takeIf { it.isNotBlank() }?.let { key to it } }
    }

    private fun shouldIncludeWorkflow(input: String): Boolean {
        return input.contains("plan") || input.contains("steps") || input.contains("workflow") || input.contains("implement") || input.contains("refactor")
    }

    private fun shouldIncludeConstraints(input: String): Boolean {
        return input.contains("fix") || input.contains("refactor") || input.contains("edit") || input.contains("change")
    }

    private fun shouldIncludeOutputContract(input: String): Boolean {
        return input.contains("report") || input.contains("review") || input.contains("format") || input.contains("output") || input.contains("summary")
    }

    private fun shouldIncludeExamples(input: String): Boolean {
        return input.contains("example") || input.contains("sample")
    }

    private fun parseSections(content: String): LinkedHashMap<String, String> {
        val normalized = content.replace("\r\n", "\n").trim()
        val matches = Regex("(?m)^##\\s+(.+?)\\s*$").findAll(normalized).toList()
        if (matches.isEmpty()) return linkedMapOf()
        return linkedMapOf<String, String>().apply {
            matches.forEachIndexed { index, match ->
                val title = match.groupValues[1].trim()
                val start = match.range.last + 1
                val end = matches.getOrNull(index + 1)?.range?.first ?: normalized.length
                val body = normalized.substring(start, end).trim()
                put(title, body)
            }
        }
    }

    private fun trimLinesToBudget(lines: List<String>, maxChars: Int): List<String> {
        val result = mutableListOf<String>()
        var used = 0
        lines.forEach { line ->
            val addition = if (result.isEmpty()) line.length else line.length + 1
            if (used + addition <= maxChars) {
                result += line
                used += addition
            } else if (result.isNotEmpty()) {
                val remaining = (maxChars - used - 4).coerceAtLeast(0)
                if (remaining > 0) {
                    result += line.take(remaining).trimEnd() + "..."
                }
                return result
            } else {
                return listOf(trimToBudget(line, maxChars))
            }
        }
        return result
    }

    private fun trimToBudget(content: String, maxChars: Int): String {
        if (content.length <= maxChars) return content
        return content.take(maxChars.coerceAtLeast(4) - 3).trimEnd() + "..."
    }
}
