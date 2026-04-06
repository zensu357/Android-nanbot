package com.example.nanobot.core.skills

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillActivationFormatter @Inject constructor() {
    fun format(
        payload: SkillActivationPayload,
        alreadyActivated: Boolean,
        effectiveAllowedTools: Set<String> = payload.skill.allowedTools.toSet()
    ): String {
        return buildString {
            appendLine("<skill_content name=\"${payload.skill.primaryActivationName()}\">")
            appendLine("Description: ${payload.skill.description}")
            appendLine("Already Active: $alreadyActivated")
            payload.skill.compatibility?.takeIf { it.isNotBlank() }?.let {
                appendLine("Compatibility: $it")
            }
            if (effectiveAllowedTools.isNotEmpty()) {
                appendLine("Allowed Tools: ${effectiveAllowedTools.sorted().joinToString()}")
            }
            if (payload.skill.validationIssues.isNotEmpty()) {
                appendLine("Validation Warnings: ${payload.skill.validationIssues.joinToString { issue -> issue.message }}")
            }
            appendLine()
            appendLine(payload.content.trim())
            if (payload.resources.isNotEmpty()) {
                appendLine()
                appendLine("<skill_resources>")
                payload.resources.take(50).forEach { resource ->
                    appendLine("<file type=\"${resource.type.name.lowercase()}\">${resource.relativePath}</file>")
                }
                appendLine("</skill_resources>")
            }
            append("</skill_content>")
        }
    }
}
