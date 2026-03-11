package com.example.nanobot.core.ai

import com.example.nanobot.core.skills.SkillDefinition
import javax.inject.Inject

data class SkillExposurePlan(
    val catalogSkills: List<SkillDefinition>,
    val expandedSkills: List<SkillDefinition>
)

class SkillSelector @Inject constructor() {
    fun select(enabledSkills: List<SkillDefinition>, latestUserInput: String): SkillExposurePlan {
        if (enabledSkills.isEmpty()) {
            return SkillExposurePlan(emptyList(), emptyList())
        }
        val normalizedInput = latestUserInput.lowercase()
        val scored = enabledSkills.map { skill -> skill to score(skill, normalizedInput) }
        val expanded = scored
            .filter { (_, score) -> score > 0 }
            .sortedWith(
                compareByDescending<Pair<SkillDefinition, Int>> { it.second }
                    .thenByDescending { it.first.priority }
                    .thenBy { it.first.title.lowercase() }
            )
            .take(MAX_EXPANDED_SKILLS)
            .map { it.first }
            .ifEmpty {
                if (enabledSkills.size == 1) enabledSkills.take(1) else emptyList()
            }

        return SkillExposurePlan(
            catalogSkills = enabledSkills.sortedBy { it.title.lowercase() },
            expandedSkills = expanded
        )
    }

    private fun score(skill: SkillDefinition, input: String): Int {
        if (input.isBlank()) return 0
        var score = 0
        val title = skill.title.lowercase()
        val id = skill.id.lowercase().replace('_', '-')
        if (title in input || id in input || id.replace('-', ' ') in input) {
            score += 50
        }
        score += scoreTextOverlap(skill.whenToUse, input, 6)
        score += scoreTextOverlap(skill.summaryPrompt, input, 4)
        score += scoreTextOverlap(skill.description, input, 3)
        skill.activationKeywords.forEach { keyword ->
            if (keyword.lowercase() in input) {
                score += 10
            }
        }
        skill.tags.forEach { tag ->
            if (tag.lowercase() in input) {
                score += 5
            }
        }
        return score
    }

    private fun scoreTextOverlap(text: String, input: String, weight: Int): Int {
        if (text.isBlank()) return 0
        return tokenize(text).count { token -> token in input } * weight
    }

    private fun tokenize(text: String): Set<String> {
        return text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 4 }
            .toSet()
    }

    private companion object {
        const val MAX_EXPANDED_SKILLS = 2
    }
}
