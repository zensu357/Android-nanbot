package com.example.nanobot.core.ai

import com.example.nanobot.core.model.AgentConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContextBudgetPlannerTest {
    private val planner = ContextBudgetPlanner()

    @Test
    fun plannerTrimsLowPrioritySectionsWithinBudget() {
        val sections = planner.apply(
            config = AgentConfig(maxTokens = 256),
            sections = listOf(
                PlannedPromptSection(
                    section = PromptSection("Identity / Role", listOf("A".repeat(400))),
                    category = PromptSectionCategory.CORE,
                    priority = 100
                ),
                PlannedPromptSection(
                    section = PromptSection("Activated Skill Instructions", listOf("B".repeat(500))),
                    category = PromptSectionCategory.SKILL_EXPANDED,
                    priority = 80
                ),
                PlannedPromptSection(
                    section = PromptSection("Memory Context", listOf("C".repeat(500))),
                    category = PromptSectionCategory.MEMORY,
                    priority = 70
                )
            )
        )

        val rendered = SystemPromptContent(sections).render()
        assertTrue(rendered.contains("## Identity / Role"))
        assertTrue(rendered.length <= 2400)
        assertTrue(rendered.contains("..."))
    }

    @Test
    fun plannerDropsEmptySectionsAfterTrimming() {
        val sections = planner.apply(
            config = AgentConfig(maxTokens = 32),
            sections = listOf(
                PlannedPromptSection(
                    section = PromptSection("Identity / Role", listOf("core")),
                    category = PromptSectionCategory.CORE,
                    priority = 100
                ),
                PlannedPromptSection(
                    section = PromptSection("Memory Context", listOf("M".repeat(1000))),
                    category = PromptSectionCategory.MEMORY,
                    priority = 10
                )
            )
        )

        val titles = sections.map { it.title }
        assertTrue("Identity / Role" in titles)
        assertFalse(sections.any { it.title == "Memory Context" && it.body.isEmpty() })
    }
}
