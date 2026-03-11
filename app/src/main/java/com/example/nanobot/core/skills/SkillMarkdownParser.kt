package com.example.nanobot.core.skills

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ParsedSkillDocument(
    val skill: SkillDefinition,
    val sectionBodies: Map<String, String>
)

@Singleton
class SkillMarkdownParser @Inject constructor() {
    fun parse(
        markdown: String,
        source: SkillSource,
        originLabel: String?,
        documentUri: String?,
        sourceTreeUri: String?,
        contentHash: String?
    ): ParsedSkillDocument {
        val normalized = markdown.replace("\r\n", "\n").trim()
        val (frontmatter, body) = extractFrontmatter(normalized)
        val metadata = parseFrontmatter(frontmatter)
        val sections = parseSections(body)
        val id = sanitizeSkillId(metadata["name"] ?: metadata["id"] ?: originLabel ?: "imported-skill")
        val title = metadata["title"]
            ?.takeIf { it.isNotBlank() }
            ?: metadata["name"]
                ?.takeIf { it.isNotBlank() }
                ?.split('-', '_', ' ')
                ?.joinToString(" ") { token -> token.replaceFirstChar { char -> char.titlecase(Locale.getDefault()) } }
            ?: id.replace('-', ' ').replaceFirstChar { it.titlecase(Locale.getDefault()) }
        val instructions = buildInstructionsBody(sections, body)
        val description = metadata["description"].orEmpty().ifBlank {
            sections["summary prompt"] ?: sections["when to use"] ?: title
        }
        val whenToUse = sections["when to use"].orEmpty()
        val summaryPrompt = sections["summary prompt"].orEmpty()
        val workflow = parseBulletOrNumberList(sections["workflow"])
        val constraints = parseBulletOrNumberList(sections["constraints"])
        val outputContract = sections["output contract"].orEmpty()
        val examples = parseBulletOrNumberList(sections["examples"])
        val recommendedTools = parseList(metadata["recommended_tools"])
        val activationKeywords = parseList(metadata["activation_keywords"])
        val priority = metadata["priority"]?.toIntOrNull() ?: 50
        val maxPromptChars = metadata["max_prompt_chars"]?.toIntOrNull()?.coerceAtLeast(200) ?: 1800

        return ParsedSkillDocument(
            skill = SkillDefinition(
                id = id,
                title = title,
                description = description,
                source = source,
                version = metadata["version"].orEmpty().ifBlank { "1.0.0" },
                tags = parseList(metadata["tags"]),
                instructions = instructions,
                whenToUse = whenToUse,
                summaryPrompt = summaryPrompt,
                workflow = workflow,
                constraints = constraints,
                outputContract = outputContract,
                examples = examples,
                recommendedTools = recommendedTools,
                activationKeywords = activationKeywords,
                priority = priority,
                maxPromptChars = maxPromptChars,
                originLabel = originLabel,
                documentUri = documentUri,
                sourceTreeUri = sourceTreeUri,
                contentHash = contentHash,
                legacyPromptFragment = body.trim().takeIf { sections.isEmpty() }.orEmpty()
            ),
            sectionBodies = sections
        )
    }

    private fun extractFrontmatter(markdown: String): Pair<String?, String> {
        if (!markdown.startsWith("---\n")) return null to markdown
        val closingIndex = markdown.indexOf("\n---", startIndex = 4)
        if (closingIndex == -1) return null to markdown
        val frontmatter = markdown.substring(4, closingIndex)
        val bodyStart = (closingIndex + 4).coerceAtMost(markdown.length)
        return frontmatter to markdown.substring(bodyStart).trimStart('\n')
    }

    private fun parseFrontmatter(frontmatter: String?): Map<String, String> {
        if (frontmatter.isNullOrBlank()) return emptyMap()
        return frontmatter.lines()
            .mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) return@mapNotNull null
                val key = line.substring(0, separator).trim().lowercase(Locale.getDefault())
                val value = line.substring(separator + 1).trim().trim('"', '\'')
                key to value
            }
            .toMap()
    }

    private fun parseSections(body: String): Map<String, String> {
        if (body.isBlank()) return emptyMap()
        val matches = SECTION_REGEX.findAll(body).toList()
        if (matches.isEmpty()) return emptyMap()
        return buildMap {
            matches.forEachIndexed { index, match ->
                val title = match.groupValues[1].trim().lowercase(Locale.getDefault())
                val start = match.range.last + 1
                val end = matches.getOrNull(index + 1)?.range?.first ?: body.length
                val sectionBody = body.substring(start, end).trim()
                put(title, sectionBody)
            }
        }
    }

    private fun buildInstructionsBody(sections: Map<String, String>, body: String): String {
        if (sections.isEmpty()) return body.trim()
        return sections["instructions"].orEmpty().ifBlank { body.trim() }
    }

    private fun parseList(raw: String?): List<String> {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return emptyList()
        if (value.startsWith('[') && value.endsWith(']')) {
            return value.substring(1, value.length - 1)
                .split(',')
                .map { it.trim().trim('"', '\'') }
                .filter { it.isNotBlank() }
        }
        return listOf(value.trim('"', '\''))
    }

    private fun sanitizeSkillId(raw: String): String {
        val normalized = raw.trim().lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return normalized.ifBlank { "imported-skill" }
    }

    private fun parseBulletOrNumberList(raw: String?): List<String> {
        val value = raw.orEmpty().trim()
        if (value.isBlank()) return emptyList()
        return value.lines()
            .map { line -> line.trim().removePrefix("- ").removePrefix("* ").replace(NUMBERED_PREFIX_REGEX, "") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private companion object {
        val SECTION_REGEX = Regex("(?m)^##\\s+(.+?)\\s*$")
        val NUMBERED_PREFIX_REGEX = Regex("^\\d+[.)]\\s+")
    }
}
