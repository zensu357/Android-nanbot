package com.example.nanobot.core.skills

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillResourceIndexer @Inject constructor() {
    fun index(relativePaths: List<Pair<String, String?>>): List<SkillResourceEntry> {
        return relativePaths
            .map { (path, documentUri) -> path.trim().replace('\\', '/') to documentUri }
            .filter { (path, _) -> path.isNotBlank() && !path.endsWith("/SKILL.md", ignoreCase = true) && !path.equals("SKILL.md", ignoreCase = true) }
            .distinct()
            .sortedBy { it.first }
            .map { (path, documentUri) ->
                SkillResourceEntry(relativePath = path, type = classify(path), documentUri = documentUri)
            }
    }

    private fun classify(relativePath: String): SkillResourceType {
        val normalized = relativePath.lowercase(Locale.getDefault())
        return when {
            normalized.startsWith("scripts/") -> SkillResourceType.SCRIPT
            normalized.startsWith("references/") -> SkillResourceType.REFERENCE
            normalized.startsWith("assets/") -> SkillResourceType.ASSET
            else -> SkillResourceType.OTHER
        }
    }
}
