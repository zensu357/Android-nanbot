package com.example.nanobot.core.ai

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.skills.SkillDefinition
import com.example.nanobot.core.skills.SkillSource
import com.example.nanobot.core.tools.ToolAccessPolicy
import com.example.nanobot.testutil.FakeSkillRepository
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemPromptBuilderSkillTest {
    @Test
    fun enabledSkillIsInjectedIntoPrompt() {
        val builder = SystemPromptBuilder(
            promptPresetCatalog = PromptPresetCatalog(),
            skillRepository = FakeSkillRepository(),
            toolAccessPolicy = ToolAccessPolicy(),
            skillSelector = SkillSelector(),
            skillPromptAssembler = SkillPromptAssembler(),
            contextBudgetPlanner = ContextBudgetPlanner()
        )

        val prompt = kotlinx.coroutines.runBlocking {
            builder.build(
                AgentConfig(enabledSkillIds = listOf("coding_editor")),
                memoryContext = null,
                latestUserInput = "Please refactor this Kotlin code."
            )
        }

        assertTrue(prompt.contains("## Available Skills"))
        assertTrue(prompt.contains("## Activated Skill Instructions"))
        assertTrue(prompt.contains("- Coding Editor [coding_editor, builtin]"))
        assertTrue(prompt.contains("Prefer inspecting the relevant workspace files before editing."))
    }

    @Test
    fun disabledSkillDoesNotAppearInPrompt() {
        val builder = SystemPromptBuilder(
            promptPresetCatalog = PromptPresetCatalog(),
            skillRepository = FakeSkillRepository(),
            toolAccessPolicy = ToolAccessPolicy(),
            skillSelector = SkillSelector(),
            skillPromptAssembler = SkillPromptAssembler(),
            contextBudgetPlanner = ContextBudgetPlanner()
        )

        val prompt = kotlinx.coroutines.runBlocking {
            builder.build(
                AgentConfig(enabledSkillIds = emptyList()),
                memoryContext = null
            )
        }

        assertFalse(prompt.contains("Coding Editor [coding_editor, builtin]"))
    }

    @Test
    fun onlyMatchingSkillIsExpandedWhileOthersStayInCatalog() {
        val builder = SystemPromptBuilder(
            promptPresetCatalog = PromptPresetCatalog(),
            skillRepository = FakeSkillRepository(
                skills = listOf(
                    SkillDefinition(
                        id = "coding_editor",
                        title = "Coding Editor",
                        description = "Code edits",
                        source = SkillSource.BUILTIN,
                        instructions = "Edit code safely.",
                        whenToUse = "Use for code changes.",
                        summaryPrompt = "Safe coding.",
                        workflow = listOf("Inspect files", "Make the smallest useful change"),
                        activationKeywords = listOf("refactor")
                    ),
                    SkillDefinition(
                        id = "research_briefing",
                        title = "Research Briefing",
                        description = "Research work",
                        source = SkillSource.BUILTIN,
                        instructions = "Research carefully.",
                        whenToUse = "Use for research.",
                        summaryPrompt = "Evidence gathering.",
                        workflow = listOf("Gather evidence"),
                        activationKeywords = listOf("research")
                    )
                )
            ),
            toolAccessPolicy = ToolAccessPolicy(),
            skillSelector = SkillSelector(),
            skillPromptAssembler = SkillPromptAssembler(),
            contextBudgetPlanner = ContextBudgetPlanner()
        )

        val prompt = kotlinx.coroutines.runBlocking {
            builder.build(
                AgentConfig(enabledSkillIds = listOf("coding_editor", "research_briefing")),
                memoryContext = null,
                latestUserInput = "Please refactor this ViewModel."
            )
        }

        assertTrue(prompt.contains("Coding Editor [coding_editor, builtin]"))
        assertTrue(prompt.contains("Research Briefing [research_briefing, builtin]"))
        assertTrue(prompt.contains("Skill: Coding Editor"))
        assertFalse(prompt.contains("Skill: Research Briefing"))
    }
}
