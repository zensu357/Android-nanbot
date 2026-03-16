package com.example.nanobot.core.skills

import kotlinx.serialization.Serializable

enum class SkillSource {
    BUILTIN,
    IMPORTED
}

enum class SkillScope {
    BUILTIN,
    IMPORTED,
    PROJECT,
    USER
}

@Serializable
enum class SkillValidationLevel {
    WARNING,
    ERROR
}

@Serializable
data class SkillValidationIssue(
    val level: SkillValidationLevel,
    val message: String
)

@Serializable
enum class SkillResourceType {
    SCRIPT,
    REFERENCE,
    ASSET,
    OTHER
}

@Serializable
data class SkillResourceEntry(
    val relativePath: String,
    val type: SkillResourceType,
    val documentUri: String? = null
)

data class SkillActivationPayload(
    val skill: SkillDefinition,
    val content: String,
    val resources: List<SkillResourceEntry>
)

data class SkillResourceReadResult(
    val skillName: String,
    val relativePath: String,
    val content: String,
    val totalBytes: Int,
    val truncated: Boolean
)

data class SkillDefinition(
    val id: String,
    val name: String = id,
    val title: String,
    val description: String,
    val source: SkillSource,
    val scope: SkillScope = when (source) {
        SkillSource.BUILTIN -> SkillScope.BUILTIN
        SkillSource.IMPORTED -> SkillScope.IMPORTED
    },
    val version: String = "1.0.0",
    val license: String? = null,
    val compatibility: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val allowedTools: List<String> = emptyList(),
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
    val isTrusted: Boolean = true,
    val originLabel: String? = null,
    val locationUri: String? = null,
    val documentUri: String? = null,
    val sourceTreeUri: String? = null,
    val skillRootUri: String? = null,
    val contentHash: String? = null,
    val rawFrontmatter: String = "",
    val bodyMarkdown: String = "",
    val resourceEntries: List<SkillResourceEntry> = emptyList(),
    val validationIssues: List<SkillValidationIssue> = emptyList(),
    val legacyPromptFragment: String = ""
) {
    fun promptContent(): String {
        if (legacyPromptFragment.isNotBlank()) {
            return legacyPromptFragment.trim()
        }
        if (bodyMarkdown.isNotBlank() && instructions.isBlank() && workflow.isEmpty() && constraints.isEmpty() && outputContract.isBlank() && examples.isEmpty()) {
            return bodyMarkdown.trim()
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

    fun primaryActivationName(): String = name.ifBlank { id }

    val isImported: Boolean get() = source == SkillSource.IMPORTED
}

data class SkillImportResult(
    val importedCount: Int,
    val updatedCount: Int,
    val skippedCount: Int,
    val duplicateCount: Int,
    val pendingConsentCount: Int = 0,
    val errors: List<String> = emptyList()
)
