package com.example.nanobot.core.skills

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillCatalog @Inject constructor() {
    val skills: List<SkillDefinition> = listOf(
        SkillDefinition(
            id = "coding_editor",
            title = "Coding Editor",
            description = "Biases Nanobot toward safe code editing, minimal diffs, and verification after changes.",
            source = SkillSource.BUILTIN,
            instructions = "Prefer inspecting the relevant workspace files before editing. When making code changes, keep diffs minimal, preserve existing style, and verify the result with the smallest useful follow-up check.",
            whenToUse = "Use when the user asks for code changes, refactors, or implementation work.",
            summaryPrompt = "Safe code editing, minimal diffs, and targeted verification.",
            workflow = listOf(
                "Inspect the relevant workspace files before editing.",
                "Apply the smallest useful change.",
                "Verify with the smallest meaningful check."
            ),
            constraints = listOf(
                "Preserve existing style and local conventions.",
                "Avoid unrelated rewrites."
            ),
            outputContract = "Explain the change briefly, reference touched files, and report verification status.",
            recommendedTools = listOf("list_workspace", "read_file", "search_workspace", "write_file", "replace_in_file"),
            tags = listOf("coding", "workspace"),
            activationKeywords = listOf("code", "edit", "refactor", "implement", "fix")
        ),
        SkillDefinition(
            id = "research_briefing",
            title = "Research Briefing",
            description = "Biases Nanobot toward concise evidence gathering and synthesis across local and web sources.",
            source = SkillSource.BUILTIN,
            instructions = "When researching, gather the smallest sufficient set of local or web facts, cite concrete findings in plain language, and separate confirmed facts from assumptions.",
            whenToUse = "Use when the user wants analysis, research, comparison, or evidence gathering.",
            summaryPrompt = "Concise evidence gathering and synthesis across local and web sources.",
            workflow = listOf(
                "Gather the smallest sufficient set of evidence.",
                "Separate confirmed findings from assumptions.",
                "Summarize the result in plain language."
            ),
            outputContract = "Present concise findings with clear evidence and note any uncertainty.",
            recommendedTools = listOf("search_workspace", "web_search", "web_fetch"),
            tags = listOf("research", "web"),
            activationKeywords = listOf("research", "compare", "investigate", "analyze")
        ),
        SkillDefinition(
            id = "planner_mode",
            title = "Planner Mode",
            description = "Biases Nanobot toward explicit execution plans before multi-step work.",
            source = SkillSource.BUILTIN,
            instructions = "For non-trivial tasks, first state a short step-by-step plan, then execute it while keeping the user informed about major state transitions.",
            whenToUse = "Use when the task is multi-step or execution order matters.",
            summaryPrompt = "Explicit step-by-step planning before non-trivial work.",
            workflow = listOf(
                "State a short execution plan first.",
                "Perform steps in order.",
                "Update the user at meaningful transitions."
            ),
            constraints = listOf(
                "Keep the plan short and actionable.",
                "Do not over-plan trivial tasks."
            ),
            recommendedTools = emptyList(),
            tags = listOf("planning", "workflow"),
            activationKeywords = listOf("plan", "steps", "roadmap", "strategy")
        )
    )

    fun getById(id: String): SkillDefinition? = skills.firstOrNull { it.id == id }
}
