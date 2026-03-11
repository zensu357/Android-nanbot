package com.example.nanobot.core.ai

import com.example.nanobot.core.skills.SkillDefinition
import com.example.nanobot.core.skills.SkillSource
import kotlin.test.Test
import kotlin.test.assertEquals

class SkillSelectorTest {
    private val selector = SkillSelector()

    @Test
    fun selectExpandsOnlyMatchingSkills() {
        val plan = selector.select(
            enabledSkills = listOf(
                SkillDefinition(
                    id = "coding_editor",
                    title = "Coding Editor",
                    description = "Coding skill",
                    source = SkillSource.BUILTIN,
                    instructions = "edit safely",
                    activationKeywords = listOf("refactor", "fix"),
                    priority = 60
                ),
                SkillDefinition(
                    id = "research_briefing",
                    title = "Research Briefing",
                    description = "Research skill",
                    source = SkillSource.BUILTIN,
                    instructions = "research carefully",
                    activationKeywords = listOf("research", "compare"),
                    priority = 50
                )
            ),
            latestUserInput = "Please refactor this Kotlin screen."
        )

        assertEquals(2, plan.catalogSkills.size)
        assertEquals(listOf("coding_editor"), plan.expandedSkills.map { it.id })
    }
}
