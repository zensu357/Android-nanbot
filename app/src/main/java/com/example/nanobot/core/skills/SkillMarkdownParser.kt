package com.example.nanobot.core.skills

import android.net.Uri
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

data class ParsedSkillDocument(
    val skill: SkillDefinition,
    val sectionBodies: Map<String, String>,
    val validationIssues: List<SkillValidationIssue>
)

private data class ParsedFrontmatter(
    val values: Map<String, Any?>,
    val issues: List<SkillValidationIssue>
)

@Singleton
class SkillMarkdownParser @Inject constructor() {
    fun parse(
        markdown: String,
        source: SkillSource,
        originLabel: String?,
        documentUri: String?,
        sourceTreeUri: String?,
        contentHash: String?,
        scope: SkillScope = when (source) {
            SkillSource.BUILTIN -> SkillScope.BUILTIN
            SkillSource.IMPORTED -> SkillScope.IMPORTED
        },
        skillRootUri: String? = deriveSkillRootUri(documentUri),
        isTrusted: Boolean = true,
        resourceEntries: List<SkillResourceEntry> = emptyList()
    ): ParsedSkillDocument {
        val normalized = markdown.replace("\r\n", "\n").trim()
        val (frontmatter, body) = extractFrontmatter(normalized)
        val parsedFrontmatter = parseFrontmatter(frontmatter)
        val metadata = parsedFrontmatter.values
        val issues = parsedFrontmatter.issues + validateMetadata(metadata, originLabel)
        val sections = parseSections(body)

        val rawName = metadata.stringValue("name") ?: metadata.stringValue("id") ?: originLabel ?: "imported-skill"
        val skillName = sanitizeSkillId(rawName)
        val title = metadata.stringValue("title")
            ?.takeIf { it.isNotBlank() }
            ?: skillName.split('-')
                .joinToString(" ") { token -> token.replaceFirstChar { char -> char.titlecase(Locale.getDefault()) } }
        val description = metadata.stringValue("description").orEmpty().ifBlank {
            sections["summary prompt"] ?: sections["when to use"] ?: title
        }
        val instructions = buildInstructionsBody(sections, body)
        val whenToUse = sections["when to use"].orEmpty()
        val summaryPrompt = sections["summary prompt"].orEmpty()
        val workflow = parseBulletOrNumberList(sections["workflow"])
        val constraints = parseBulletOrNumberList(sections["constraints"])
        val outputContract = sections["output contract"].orEmpty()
        val examples = parseBulletOrNumberList(sections["examples"])
        val tags = parseList(metadata["tags"])
        val recommendedTools = parseList(metadata["recommended_tools"])
        val activationKeywords = parseList(metadata["activation_keywords"])
        val allowedTools = parseAllowedTools(metadata["allowed-tools"])
        val metadataMap = parseMetadataMap(metadata["metadata"], frontmatter)
        val priority = metadata.intValue("priority") ?: 50
        val maxPromptChars = metadata.intValue("max_prompt_chars")?.coerceAtLeast(200) ?: 1800
        val bodyMarkdown = body.trim()

        return ParsedSkillDocument(
            skill = SkillDefinition(
                id = skillName,
                name = skillName,
                title = title,
                description = description,
                source = source,
                scope = scope,
                version = metadata.stringValue("version").orEmpty().ifBlank { "1.0.0" },
                license = metadata.stringValue("license")?.ifBlank { null },
                compatibility = metadata.stringValue("compatibility")?.ifBlank { null },
                metadata = metadataMap,
                allowedTools = allowedTools,
                tags = tags,
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
                isTrusted = isTrusted,
                originLabel = originLabel,
                locationUri = documentUri,
                documentUri = documentUri,
                sourceTreeUri = sourceTreeUri,
                skillRootUri = skillRootUri,
                contentHash = contentHash,
                rawFrontmatter = frontmatter.orEmpty(),
                bodyMarkdown = bodyMarkdown,
                resourceEntries = resourceEntries,
                validationIssues = issues,
                legacyPromptFragment = bodyMarkdown.takeIf { sections.isEmpty() }.orEmpty()
            ),
            sectionBodies = sections,
            validationIssues = issues
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

    private fun parseFrontmatter(frontmatter: String?): ParsedFrontmatter {
        if (frontmatter.isNullOrBlank()) return ParsedFrontmatter(emptyMap(), emptyList())
        return runCatching {
            val loaded = Load(LoadSettings.builder().build()).loadFromString(frontmatter)
            when (loaded) {
                null -> ParsedFrontmatter(emptyMap(), emptyList())
                is Map<*, *> -> ParsedFrontmatter(normalizeYamlMap(loaded), emptyList())
                else -> ParsedFrontmatter(
                    values = parseLegacyFrontmatter(frontmatter),
                    issues = listOf(
                        SkillValidationIssue(
                            level = SkillValidationLevel.WARNING,
                            message = "Skill frontmatter must be a YAML mapping; using compatibility parser fallback."
                        )
                    )
                )
            }
        }.getOrElse { throwable ->
            ParsedFrontmatter(
                values = parseLegacyFrontmatter(frontmatter),
                issues = listOf(
                    SkillValidationIssue(
                        level = SkillValidationLevel.WARNING,
                        message = "Skill frontmatter YAML parsing failed; using compatibility parser fallback: ${throwable.message ?: "unknown parse error"}"
                    )
                )
            )
        }
    }

    private fun parseLegacyFrontmatter(frontmatter: String): Map<String, Any?> {
        val result = linkedMapOf<String, String>()
        frontmatter.lines().forEach { line ->
            if (line.isBlank() || line.trimStart().startsWith('#')) return@forEach
            if (line.startsWith(' ') || line.startsWith('\t')) return@forEach
            val separator = line.indexOf(':')
            if (separator <= 0) return@forEach
            val key = line.substring(0, separator).trim().lowercase(Locale.getDefault())
            val rawValue = line.substring(separator + 1).trim()
            val normalizedValue = normalizeYamlScalar(rawValue)
            result[key] = normalizedValue
        }
        return result
    }

    private fun normalizeYamlMap(input: Map<*, *>): Map<String, Any?> {
        return buildMap {
            input.forEach { (key, value) ->
                val normalizedKey = key?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
                if (normalizedKey.isBlank()) return@forEach
                put(normalizedKey, normalizeYamlValue(value))
            }
        }
    }

    private fun normalizeYamlValue(value: Any?): Any? {
        return when (value) {
            is Map<*, *> -> normalizeYamlMap(value)
            is List<*> -> value.map { normalizeYamlValue(it) }
            is String -> value.trim()
            else -> value
        }
    }

    private fun parseMetadataMap(raw: Any?, frontmatter: String?): Map<String, String> {
        if (raw is Map<*, *>) {
            return raw.entries
                .mapNotNull { (key, value) ->
                    val normalizedKey = key?.toString()?.trim().orEmpty()
                    val normalizedValue = scalarString(value)?.takeIf { it.isNotBlank() }
                    if (normalizedKey.isBlank() || normalizedValue == null) {
                        null
                    } else {
                        normalizedKey to normalizedValue
                    }
                }
                .toMap(linkedMapOf())
        }
        return parseLegacyNestedMetadata(frontmatter)
    }

    private fun parseLegacyNestedMetadata(frontmatter: String?): Map<String, String> {
        if (frontmatter.isNullOrBlank()) return emptyMap()
        val lines = frontmatter.lines()
        val metadataIndex = lines.indexOfFirst { it.trim().lowercase(Locale.getDefault()) == "metadata:" }
        if (metadataIndex == -1) return emptyMap()

        val result = linkedMapOf<String, String>()
        for (index in metadataIndex + 1 until lines.size) {
            val line = lines[index]
            if (line.isBlank()) continue
            if (!line.startsWith(' ') && !line.startsWith('\t')) break
            val trimmed = line.trim()
            val separator = trimmed.indexOf(':')
            if (separator <= 0) continue
            val key = trimmed.substring(0, separator).trim()
            val value = normalizeYamlScalar(trimmed.substring(separator + 1).trim())
            if (key.isNotBlank() && value.isNotBlank()) {
                result[key] = value
            }
        }
        return result
    }

    private fun normalizeYamlScalar(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith('[') && trimmed.endsWith(']')) return trimmed
        if ((trimmed.startsWith('"') && trimmed.endsWith('"')) || (trimmed.startsWith('\'') && trimmed.endsWith('\''))) {
            return trimmed.substring(1, trimmed.length - 1)
        }
        return trimmed
    }

    private fun validateMetadata(metadata: Map<String, Any?>, originLabel: String?): List<SkillValidationIssue> {
        val issues = mutableListOf<SkillValidationIssue>()
        val name = metadata.stringValue("name").orEmpty()
        val description = metadata.stringValue("description").orEmpty()

        if (name.isBlank()) {
            issues += SkillValidationIssue(SkillValidationLevel.ERROR, "Missing required frontmatter field 'name'.")
        } else {
            if (name.length > 64) {
                issues += SkillValidationIssue(SkillValidationLevel.WARNING, "Skill name exceeds 64 characters.")
            }
            if (!NAME_REGEX.matches(name)) {
                issues += SkillValidationIssue(SkillValidationLevel.WARNING, "Skill name should use lowercase letters, numbers, and single hyphens only.")
            }
            val expectedDirectory = originLabel
                ?.substringBeforeLast('/')
                ?.substringAfterLast('/')
                ?.trim()
                .orEmpty()
            if (expectedDirectory.isNotBlank() && expectedDirectory != name) {
                issues += SkillValidationIssue(
                    SkillValidationLevel.WARNING,
                    "Skill name '$name' does not match parent directory '$expectedDirectory'."
                )
            }
        }

        if (description.isBlank()) {
            issues += SkillValidationIssue(SkillValidationLevel.ERROR, "Missing required frontmatter field 'description'.")
        }
        return issues
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

    private fun parseList(raw: Any?): List<String> {
        return when (raw) {
            is List<*> -> raw.mapNotNull(::scalarString).filter { it.isNotBlank() }
            else -> {
                val value = scalarString(raw)
                if (value.isNullOrBlank()) {
                    emptyList()
                } else if (value.startsWith('[') && value.endsWith(']')) {
                    value.substring(1, value.length - 1)
                        .split(',')
                        .map { it.trim().trim('"', '\'') }
                        .filter { it.isNotBlank() }
                } else if (',' in value) {
                    value.split(',').map { it.trim().trim('"', '\'') }.filter { it.isNotBlank() }
                } else {
                    listOf(value.trim('"', '\''))
                }
            }
        }
    }

    private fun parseAllowedTools(raw: Any?): List<String> {
        if (raw is List<*>) {
            return raw.mapNotNull(::scalarString).filter { it.isNotBlank() }
        }
        val value = scalarString(raw).orEmpty().trim()
        if (value.isBlank()) return emptyList()
        if (value.startsWith('[') && value.endsWith(']')) {
            return parseList(value)
        }
        return value.split(Regex("[\\s,]+"))
            .map { it.trim().trim('"', '\'') }
            .filter { it.isNotBlank() }
    }

    private fun scalarString(value: Any?): String? {
        return when (value) {
            null -> null
            is String -> value.trim()
            is Number, is Boolean -> value.toString()
            else -> value.toString().trim()
        }
    }

    private fun Map<String, Any?>.stringValue(key: String): String? = scalarString(this[key])

    private fun Map<String, Any?>.intValue(key: String): Int? {
        val value = this[key] ?: return null
        return when (value) {
            is Int -> value
            is Long -> value.toInt()
            is Double -> value.toInt()
            is Float -> value.toInt()
            else -> scalarString(value)?.toIntOrNull()
        }
    }

    private fun sanitizeSkillId(raw: String): String {
        val normalized = raw.trim().lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9]+"), "-")
            .replace(Regex("-{2,}"), "-")
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

    private fun deriveSkillRootUri(documentUri: String?): String? {
        val uri = documentUri?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val parsed = Uri.parse(uri)
            parsed.buildUpon().path(parsed.path?.substringBeforeLast('/') ?: parsed.path).build().toString()
        }.getOrNull()
    }

    private companion object {
        val SECTION_REGEX = Regex("(?m)^##\\s+(.+?)\\s*$")
        val NUMBERED_PREFIX_REGEX = Regex("^\\d+[.)]\\s+")
        val NAME_REGEX = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")
    }
}
