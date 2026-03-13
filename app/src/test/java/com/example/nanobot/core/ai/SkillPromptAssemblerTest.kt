package com.example.nanobot.core.ai

import com.example.nanobot.core.skills.SkillDefinition
import com.example.nanobot.core.skills.SkillSource
import kotlin.test.Test
import kotlin.test.assertTrue

class SkillPromptAssemblerTest {
    private val assembler = SkillPromptAssembler()

    @Test
    fun assemblerBuildsCatalogDisclosureWithoutExpandedSectionContent() {
        val skill = SkillDefinition(
            id = "android_refactor",
            name = "android_refactor",
            title = "Android Refactor",
            description = "Refactor Android code",
            source = SkillSource.IMPORTED,
            instructions = "Apply minimal diffs.\n" + "x".repeat(250),
            whenToUse = "Use for Android refactors.",
            summaryPrompt = "Minimal diffs.",
            workflow = listOf("Inspect files", "Refactor safely"),
            constraints = listOf("Preserve architecture"),
            examples = listOf("Example output"),
            maxPromptChars = 320
        )
        val plan = SkillExposurePlan(
            catalogSkills = listOf(skill),
            expandedSkills = listOf(skill)
        )

        val catalog = assembler.buildCatalogSection(plan).render()
        val expanded = assembler.buildExpandedSection(plan, "Please refactor this screen and keep the architecture intact.")

        assertTrue(catalog.contains("android_refactor [imported]: Refactor Android code"))
        assertTrue(catalog.contains("call `activate_skill` with the skill name"))
        assertTrue(expanded.isEmpty())
    }
}
