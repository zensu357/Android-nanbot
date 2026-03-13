package com.example.nanobot.core.skills

import com.example.nanobot.domain.repository.WorkspaceRepository
import javax.inject.Inject
import javax.inject.Singleton

private enum class DiscoveryOrigin(val priority: Int, val label: String) {
    BUILTIN(0, "builtin"),
    USER(10, "user"),
    PROJECT(20, "project"),
    PROJECT_COMPAT(30, "project compatibility")
}

private data class DiscoveryCandidate(
    val skill: SkillDefinition,
    val origin: DiscoveryOrigin,
    val tieBreaker: String
)

data class SkillDiscoveryIssue(
    val message: String,
    val scope: SkillScope,
    val kind: SkillDiagnosticKind = SkillDiagnosticKind.WARNING,
    val level: SkillValidationLevel = SkillValidationLevel.WARNING
)

data class SkillDiscoverySnapshot(
    val skills: List<SkillDefinition>,
    val issues: List<SkillDiscoveryIssue>
)

@Singleton
class SkillDiscoveryService @Inject constructor(
    private val skillCatalog: SkillCatalog,
    private val workspaceRepository: WorkspaceRepository,
    private val skillMarkdownParser: SkillMarkdownParser,
    private val skillResourceIndexer: SkillResourceIndexer
) {
    suspend fun discover(
        importedSkills: List<SkillDefinition>,
        importedRoots: List<String>,
        projectSkillsEnabled: Boolean
    ): SkillDiscoverySnapshot {
        val issues = mutableListOf<SkillDiscoveryIssue>()
        val builtin = skillCatalog.skills.map { skill ->
            DiscoveryCandidate(
                skill = skill.copy(
                    name = skill.name.ifBlank { skill.id },
                    scope = SkillScope.BUILTIN,
                    isTrusted = true
                ),
                origin = DiscoveryOrigin.BUILTIN,
                tieBreaker = skill.primaryActivationName().lowercase()
            )
        }

        val imported = importedSkills.map { skill ->
            val resolvedScope = resolveImportedScope(skill, importedRoots)
            DiscoveryCandidate(
                skill = skill.copy(scope = resolvedScope),
                origin = DiscoveryOrigin.USER,
                tieBreaker = skill.sourceTreeUri.orEmpty().ifBlank {
                    skill.locationUri.orEmpty().ifBlank { skill.primaryActivationName().lowercase() }
                }
            )
        }

        if (importedRoots.isEmpty() && imported.any { it.skill.isImported }) {
            issues += SkillDiscoveryIssue(
                message = "Imported skills are present but no persisted imported root is registered.",
                scope = SkillScope.USER,
                kind = SkillDiagnosticKind.WARNING
            )
        }

        val projectSkills = if (projectSkillsEnabled) {
            discoverWorkspaceSkills(
                root = "skills",
                origin = DiscoveryOrigin.PROJECT,
                issues = issues
            ) + discoverWorkspaceSkills(
                root = ".agents/skills",
                origin = DiscoveryOrigin.PROJECT_COMPAT,
                issues = issues
            )
        } else {
            issues += SkillDiscoveryIssue(
                message = "Project-scoped skill discovery is disabled until the workspace is trusted. Hidden roots: skills/ and .agents/skills/.",
                scope = SkillScope.PROJECT,
                kind = SkillDiagnosticKind.BLOCKED
            )
            emptyList()
        }

        val merged = linkedMapOf<String, DiscoveryCandidate>()
        mergeInto(merged, builtin, issues)
        mergeInto(merged, imported, issues)
        mergeInto(merged, projectSkills, issues)

        return SkillDiscoverySnapshot(
            skills = merged.values
                .map { it.skill }
                .sortedWith(compareBy<SkillDefinition>({ scopeOrder(it.scope) }, { it.title.lowercase() }, { it.primaryActivationName().lowercase() })),
            issues = issues
        )
    }

    private suspend fun discoverWorkspaceSkills(
        root: String,
        origin: DiscoveryOrigin,
        issues: MutableList<SkillDiscoveryIssue>
    ): List<DiscoveryCandidate> {
        val files = runCatching { walkWorkspaceFiles(root) }
            .getOrElse { throwable ->
                val message = throwable.message ?: "workspace root '$root' is not available."
                if (!message.contains("does not exist", ignoreCase = true)) {
                    issues += SkillDiscoveryIssue(
                        message = "${origin.label.replaceFirstChar { it.uppercase() }} skill discovery skipped for '$root': $message",
                        scope = SkillScope.PROJECT,
                        kind = SkillDiagnosticKind.WARNING
                    )
                }
                return emptyList()
            }

        val skillPaths = files
            .filter { it.endsWith("/SKILL.md", ignoreCase = true) || it.equals("SKILL.md", ignoreCase = true) }
            .distinct()

        return skillPaths.mapNotNull { relativePath ->
            runCatching {
                val content = workspaceRepository.readText(relativePath, 12_000).content
                val skillRoot = relativePath.substringBeforeLast('/', missingDelimiterValue = "")
                val packageFiles = files.filter { filePath ->
                    filePath.equals(relativePath, ignoreCase = true) ||
                        (skillRoot.isNotBlank() && filePath.startsWith("$skillRoot/", ignoreCase = true))
                }
                val resourceEntries = skillResourceIndexer.index(
                    packageFiles.map { filePath ->
                        val relativeResourcePath = if (skillRoot.isBlank()) filePath else filePath.removePrefix("$skillRoot/")
                        relativeResourcePath to null
                    }
                )
                val parsed = skillMarkdownParser.parse(
                    markdown = content,
                    source = SkillSource.IMPORTED,
                    originLabel = relativePath,
                    documentUri = null,
                    sourceTreeUri = null,
                    contentHash = content.hashCode().toString(),
                    scope = SkillScope.PROJECT,
                    skillRootUri = skillRoot,
                    isTrusted = true,
                    resourceEntries = resourceEntries
                )
                DiscoveryCandidate(
                    skill = parsed.skill.copy(
                        source = SkillSource.IMPORTED,
                        scope = SkillScope.PROJECT,
                        isTrusted = true,
                        skillRootUri = skillRoot,
                        locationUri = relativePath,
                        bodyMarkdown = content
                    ),
                    origin = origin,
                    tieBreaker = relativePath.lowercase()
                ).also {
                    issues += SkillDiscoveryIssue(
                        message = "Loaded ${origin.label} skill '${parsed.skill.primaryActivationName()}' from '$relativePath'.",
                        scope = SkillScope.PROJECT,
                        kind = SkillDiagnosticKind.LOADED
                    )
                }
            }.getOrElse {
                issues += SkillDiscoveryIssue(
                    message = "Failed to parse ${origin.label} skill '$relativePath': ${it.message}",
                    scope = SkillScope.PROJECT,
                    kind = SkillDiagnosticKind.ERROR,
                    level = SkillValidationLevel.ERROR
                )
                null
            }
        }
    }

    private suspend fun walkWorkspaceFiles(root: String): List<String> {
        val files = mutableListOf<String>()

        suspend fun visit(path: String) {
            workspaceRepository.list(path, MAX_DIRECTORY_ENTRIES)
                .forEach { entry ->
                    if (entry.isDirectory) {
                        visit(entry.relativePath)
                    } else {
                        files += entry.relativePath
                    }
                }
        }

        visit(root)
        return files.distinct().sorted()
    }

    private fun resolveImportedScope(skill: SkillDefinition, importedRoots: List<String>): SkillScope {
        if (skill.scope == SkillScope.PROJECT || skill.scope == SkillScope.BUILTIN) {
            return skill.scope
        }
        val normalizedRoots = importedRoots.map { it.trim() }.filter { it.isNotBlank() }
        val matchesPersistedRoot = normalizedRoots.isEmpty() || normalizedRoots.any { root ->
            val treeUri = skill.sourceTreeUri.orEmpty()
            val location = skill.locationUri.orEmpty()
            treeUri.equals(root, ignoreCase = true) || location.startsWith(root, ignoreCase = true)
        }
        return if (matchesPersistedRoot) SkillScope.USER else SkillScope.IMPORTED
    }

    private fun mergeInto(
        target: MutableMap<String, DiscoveryCandidate>,
        skills: List<DiscoveryCandidate>,
        issues: MutableList<SkillDiscoveryIssue>
    ) {
        skills.sortedWith(compareBy<DiscoveryCandidate>({ it.origin.priority }, { it.tieBreaker }, { it.skill.primaryActivationName().lowercase() }))
            .forEach { candidate ->
                val skill = candidate.skill
                val key = skill.primaryActivationName().lowercase()
                val existing = target[key]
                if (existing == null) {
                    target[key] = candidate
                    return@forEach
                }

                when {
                    candidate.origin.priority > existing.origin.priority -> {
                        issues += SkillDiscoveryIssue(
                            message = "Skill '${skill.primaryActivationName()}' from ${candidate.origin.label} overrides ${existing.origin.label} scope.",
                            scope = skill.scope,
                            kind = SkillDiagnosticKind.OVERRIDDEN
                        )
                        target[key] = candidate
                    }

                    candidate.origin.priority == existing.origin.priority && candidate.tieBreaker < existing.tieBreaker -> {
                        issues += SkillDiscoveryIssue(
                            message = "Skill '${skill.primaryActivationName()}' from ${candidate.origin.label} replaces '${existing.skill.locationUri ?: existing.skill.originLabel ?: existing.skill.title}' by stable ordering.",
                            scope = skill.scope,
                            kind = SkillDiagnosticKind.OVERRIDDEN
                        )
                        target[key] = candidate
                    }

                    else -> {
                        issues += SkillDiscoveryIssue(
                            message = "Skill '${skill.primaryActivationName()}' from ${candidate.origin.label} was skipped because ${existing.origin.label} takes precedence.",
                            scope = skill.scope,
                            kind = SkillDiagnosticKind.SKIPPED
                        )
                    }
                }
            }
    }

    private fun scopeOrder(scope: SkillScope): Int {
        return when (scope) {
            SkillScope.BUILTIN -> 0
            SkillScope.USER -> 1
            SkillScope.IMPORTED -> 2
            SkillScope.PROJECT -> 3
        }
    }

    private companion object {
        const val MAX_DIRECTORY_ENTRIES = 200
    }
}
