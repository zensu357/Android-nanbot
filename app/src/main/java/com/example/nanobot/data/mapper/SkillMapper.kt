package com.example.nanobot.data.mapper

import com.example.nanobot.core.database.entity.CustomSkillEntity
import com.example.nanobot.core.skills.SkillDefinition
import com.example.nanobot.core.skills.SkillResourceEntry
import com.example.nanobot.core.skills.SkillScope
import com.example.nanobot.core.skills.SkillSource
import com.example.nanobot.core.skills.SkillValidationIssue
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val skillJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

fun CustomSkillEntity.toModel(): SkillDefinition = SkillDefinition(
    id = id,
    name = name,
    title = title,
    description = description,
    source = SkillSource.IMPORTED,
    scope = runCatching { SkillScope.valueOf(scope) }.getOrDefault(SkillScope.IMPORTED),
    version = version,
    license = license,
    compatibility = compatibility,
    metadata = decodeStringMap(metadataJson),
    allowedTools = decodeStringList(allowedToolsJson),
    tags = decodeStringList(tagsJson),
    instructions = instructions,
    whenToUse = whenToUse,
    summaryPrompt = summaryPrompt,
    workflow = decodeStringList(workflowJson),
    constraints = decodeStringList(constraintsJson),
    outputContract = outputContract,
    examples = decodeStringList(examplesJson),
    recommendedTools = decodeStringList(recommendedToolsJson),
    activationKeywords = decodeStringList(activationKeywordsJson),
    priority = priority,
    maxPromptChars = maxPromptChars,
    isTrusted = trusted,
    originLabel = originLabel,
    locationUri = locationUri.ifBlank { null },
    documentUri = documentUri,
    sourceTreeUri = sourceTreeUri,
    skillRootUri = skillRootUri.ifBlank { null },
    contentHash = contentHash,
    rawFrontmatter = rawFrontmatter,
    bodyMarkdown = bodyMarkdown,
    resourceEntries = decodeSkillResourceEntries(resourceEntriesJson),
    validationIssues = decodeSkillValidationIssues(validationIssuesJson),
    legacyPromptFragment = legacyPromptFragment
)

fun SkillDefinition.toEntity(importedAt: Long, updatedAt: Long): CustomSkillEntity = CustomSkillEntity(
    id = id,
    name = name,
    title = title,
    description = description,
    version = version,
    license = license,
    compatibility = compatibility,
    metadataJson = encodeStringMap(metadata),
    allowedToolsJson = encodeStringList(allowedTools),
    tagsJson = encodeStringList(tags),
    instructions = instructions,
    whenToUse = whenToUse,
    summaryPrompt = summaryPrompt,
    workflowJson = encodeStringList(workflow),
    constraintsJson = encodeStringList(constraints),
    outputContract = outputContract,
    examplesJson = encodeStringList(examples),
    recommendedToolsJson = encodeStringList(recommendedTools),
    activationKeywordsJson = encodeStringList(activationKeywords),
    priority = priority,
    maxPromptChars = maxPromptChars,
    scope = scope.name,
    trusted = isTrusted,
    originLabel = originLabel,
    locationUri = locationUri.orEmpty(),
    documentUri = documentUri.orEmpty(),
    sourceTreeUri = sourceTreeUri.orEmpty(),
    skillRootUri = skillRootUri.orEmpty(),
    contentHash = contentHash.orEmpty(),
    rawFrontmatter = rawFrontmatter,
    bodyMarkdown = bodyMarkdown,
    resourceEntriesJson = encodeSkillResourceEntries(resourceEntries),
    validationIssuesJson = encodeSkillValidationIssues(validationIssues),
    importedAt = importedAt,
    updatedAt = updatedAt,
    legacyPromptFragment = legacyPromptFragment
)

private fun encodeStringList(value: List<String>): String {
    return skillJson.encodeToString(ListSerializer(String.serializer()), value)
}

private fun decodeStringList(value: String): List<String> {
    return runCatching {
        skillJson.decodeFromString(ListSerializer(String.serializer()), value)
    }.getOrDefault(emptyList())
}

private fun encodeStringMap(value: Map<String, String>): String {
    return skillJson.encodeToString(MapSerializer(String.serializer(), String.serializer()), value)
}

private fun decodeStringMap(value: String): Map<String, String> {
    return runCatching {
        skillJson.decodeFromString(MapSerializer(String.serializer(), String.serializer()), value)
    }.getOrDefault(emptyMap())
}

private fun encodeSkillResourceEntries(value: List<SkillResourceEntry>): String {
    return skillJson.encodeToString(ListSerializer(SkillResourceEntry.serializer()), value)
}

private fun decodeSkillResourceEntries(value: String): List<SkillResourceEntry> {
    return runCatching {
        skillJson.decodeFromString(ListSerializer(SkillResourceEntry.serializer()), value)
    }.getOrDefault(emptyList())
}

private fun encodeSkillValidationIssues(value: List<SkillValidationIssue>): String {
    return skillJson.encodeToString(ListSerializer(SkillValidationIssue.serializer()), value)
}

private fun decodeSkillValidationIssues(value: String): List<SkillValidationIssue> {
    return runCatching {
        skillJson.decodeFromString(ListSerializer(SkillValidationIssue.serializer()), value)
    }.getOrDefault(emptyList())
}
