package com.example.nanobot.data.mapper

import com.example.nanobot.core.database.entity.CustomSkillEntity
import com.example.nanobot.core.skills.SkillDefinition
import com.example.nanobot.core.skills.SkillSource
import kotlinx.serialization.builtins.ListSerializer
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
    title = title,
    description = description,
    source = SkillSource.IMPORTED,
    version = version,
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
    originLabel = originLabel,
    documentUri = documentUri,
    sourceTreeUri = sourceTreeUri,
    contentHash = contentHash,
    legacyPromptFragment = legacyPromptFragment
)

fun SkillDefinition.toEntity(importedAt: Long, updatedAt: Long): CustomSkillEntity = CustomSkillEntity(
    id = id,
    title = title,
    description = description,
    version = version,
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
    originLabel = originLabel,
    documentUri = documentUri.orEmpty(),
    sourceTreeUri = sourceTreeUri.orEmpty(),
    contentHash = contentHash.orEmpty(),
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
