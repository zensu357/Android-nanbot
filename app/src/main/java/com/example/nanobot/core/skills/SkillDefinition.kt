package com.example.nanobot.core.skills

enum class SkillSource {
    BUILTIN,
    IMPORTED
}

data class SkillDefinition(
    val id: String,
    val title: String,
    val description: String,
    val source: SkillSource,
    val version: String = "1.0.0",
    val tags: List<String> = emptyList(),
    val instructions: String = "",
    val whenToUse: String = "",
    val summaryPrompt: String = "",
    val workflow: List<String> = emptyList(),
    val constraints: List<String> = emptyList(),
    val outputContract: String = "",
    val examples: List<String> = emptyList(),
    val recommendedTools: List<String> = emptyList(),
    val activationKeywords: List<String> = emptyList(),
    val priority: Int = 50,
    val maxPromptChars: Int = 1800,
    val originLabel: String? = null,
    val documentUri: String? = null,
    val sourceTreeUri: String? = null,
    val contentHash: String? = null,
    val legacyPromptFragment: String = ""
) {
    fun promptContent(): String {
        if (legacyPromptFragment.isNotBlank()) {
            return legacyPromptFragment.trim()
        }
        return buildString {
            instructions.takeIf { it.isNotBlank() }?.let {
                appendLine("## Instructions")
                appendLine(it)
            }
            if (workflow.isNotEmpty()) {
                if (isNotEmpty()) appendLine()
                appendLine("## Workflow")
                workflow.forEach { appendLine(it) }
            }
            if (constraints.isNotEmpty()) {
                if (isNotEmpty()) appendLine()
                appendLine("## Constraints")
                constraints.forEach { appendLine(it) }
            }
            outputContract.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) appendLine()
                appendLine("## Output Contract")
                appendLine(it)
            }
            if (examples.isNotEmpty()) {
                if (isNotEmpty()) appendLine()
                appendLine("## Examples")
                examples.forEach { appendLine(it) }
            }
        }.trim()
    }

    fun catalogSummary(): String {
        return whenToUse.ifBlank { summaryPrompt.ifBlank { description } }.trim()
    }

    val isImported: Boolean get() = source == SkillSource.IMPORTED
}

data class SkillImportResult(
    val importedCount: Int,
    val updatedCount: Int,
    val skippedCount: Int,
    val duplicateCount: Int,
    val errors: List<String> = emptyList()
)
