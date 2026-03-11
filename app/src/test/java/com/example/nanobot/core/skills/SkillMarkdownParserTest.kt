package com.example.nanobot.core.skills

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkillMarkdownParserTest {
    private val parser = SkillMarkdownParser()

    @Test
    fun parseStructuredSkillMarkdownMapsFrontmatterAndSections() {
        val parsed = parser.parse(
            markdown = """
                ---
                name: android-refactor
                description: Android/Kotlin small-step refactoring mode
                tags: [android, kotlin, refactor]
                activation_keywords: [refactor, cleanup, simplify]
                recommended_tools: [search_workspace, read_file, replace_in_file]
                priority: 60
                max_prompt_chars: 1800
                version: 1.2.0
                ---

                ## When To Use
                Use when the user asks for focused Android/Kotlin cleanup.

                ## Summary Prompt
                Minimal diff Android refactoring.

                ## Instructions
                Inspect relevant files first.

                ## Workflow
                1. Find files
                2. Make the smallest useful change

                ## Constraints
                - Keep architecture intact
            """.trimIndent(),
            source = SkillSource.IMPORTED,
            originLabel = "mobile/android/SKILL.md",
            documentUri = "content://skills/android-refactor",
            sourceTreeUri = "content://skills/tree",
            contentHash = "hash"
        )

        val skill = parsed.skill
        assertEquals("android-refactor", skill.id)
        assertEquals("Android/Kotlin small-step refactoring mode", skill.description)
        assertEquals(SkillSource.IMPORTED, skill.source)
        assertEquals("1.2.0", skill.version)
        assertEquals(listOf("android", "kotlin", "refactor"), skill.tags)
        assertEquals(listOf("refactor", "cleanup", "simplify"), skill.activationKeywords)
        assertEquals(listOf("search_workspace", "read_file", "replace_in_file"), skill.recommendedTools)
        assertEquals(listOf("Find files", "Make the smallest useful change"), skill.workflow)
        assertEquals(listOf("Keep architecture intact"), skill.constraints)
        assertEquals(60, skill.priority)
        assertEquals(1800, skill.maxPromptChars)
        assertEquals("Inspect relevant files first.", skill.instructions)
        assertEquals("Use when the user asks for focused Android/Kotlin cleanup.", skill.whenToUse)
        assertEquals("Minimal diff Android refactoring.", skill.summaryPrompt)
        assertEquals("mobile/android/SKILL.md", skill.originLabel)
    }

    @Test
    fun parseLegacySkillMarkdownFallsBackToBody() {
        val parsed = parser.parse(
            markdown = "Use this skill to produce concise release notes.",
            source = SkillSource.IMPORTED,
            originLabel = "release/SKILL.md",
            documentUri = null,
            sourceTreeUri = null,
            contentHash = null
        )

        assertEquals("release-skill-md", parsed.skill.id)
        assertEquals("Use this skill to produce concise release notes.", parsed.skill.instructions)
        assertEquals("Use this skill to produce concise release notes.", parsed.skill.legacyPromptFragment)
    }
}
