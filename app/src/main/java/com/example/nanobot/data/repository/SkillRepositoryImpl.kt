package com.example.nanobot.data.repository

import android.net.Uri
import com.example.nanobot.core.database.dao.CustomSkillDao
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.preferences.SettingsConfigStore
import com.example.nanobot.core.skills.ActivatedSkillSessionStore
import com.example.nanobot.core.skills.SkillActivationPayload
import com.example.nanobot.core.skills.SkillCatalog
import com.example.nanobot.core.skills.SkillContentStore
import com.example.nanobot.core.skills.SkillDiscoveryIssue
import com.example.nanobot.core.skills.SkillDiscoveryService
import com.example.nanobot.core.skills.PhoneControlUnlockReceipt
import com.example.nanobot.core.skills.PhoneControlUnlockProcessor
import com.example.nanobot.core.skills.PhoneControlUnlockProfileRegistry
import com.example.nanobot.core.skills.PhoneControlUnlockStore
import com.example.nanobot.core.skills.SkillDefinition
import com.example.nanobot.core.skills.SkillImportScanner
import com.example.nanobot.core.skills.SkillImportResult
import com.example.nanobot.core.skills.SkillResourceReadResult
import com.example.nanobot.core.skills.SkillScope
import com.example.nanobot.core.skills.SkillZipImporter
import com.example.nanobot.data.mapper.toEntity
import com.example.nanobot.data.mapper.toModel
import com.example.nanobot.domain.repository.SkillRepository
import com.example.nanobot.domain.repository.WorkspaceRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class SkillRepositoryImpl @Inject constructor(
    private val skillCatalog: SkillCatalog,
    private val customSkillDao: CustomSkillDao,
    private val skillDirectoryScanner: SkillImportScanner,
    private val settingsConfigStore: SettingsConfigStore,
    private val skillContentStore: SkillContentStore,
    private val skillDiscoveryService: SkillDiscoveryService,
    private val skillZipImporter: SkillZipImporter,
    private val activatedSkillSessionStore: ActivatedSkillSessionStore,
    private val workspaceRepository: WorkspaceRepository,
    private val phoneControlUnlockProcessor: PhoneControlUnlockProcessor,
    private val phoneControlUnlockStore: PhoneControlUnlockStore,
    private val phoneControlUnlockProfileRegistry: PhoneControlUnlockProfileRegistry
) : SkillRepository {

    override fun observeSkills(): Flow<List<SkillDefinition>> {
        return combine(
            customSkillDao.observeSkills().map { entities -> entities.map { it.toModel() } },
            settingsConfigStore.skillRootsFlow,
            settingsConfigStore.trustProjectSkillsFlow
        ) { imported, roots, trustProjectSkills ->
            skillDiscoveryService.discover(
                importedSkills = imported,
                importedRoots = roots,
                projectSkillsEnabled = trustProjectSkills
            ).skills
        }
    }

    override fun observeDiscoveryIssues(): Flow<List<SkillDiscoveryIssue>> {
        return combine(
            customSkillDao.observeSkills().map { entities -> entities.map { it.toModel() } },
            settingsConfigStore.skillRootsFlow,
            settingsConfigStore.trustProjectSkillsFlow
        ) { imported, roots, trustProjectSkills ->
            skillDiscoveryService.discover(
                importedSkills = imported,
                importedRoots = roots,
                projectSkillsEnabled = trustProjectSkills
            ).issues
        }
    }

    override suspend fun listSkills(): List<SkillDefinition> {
        return skillDiscoveryService.discover(
            importedSkills = customSkillDao.getSkills().map { it.toModel() },
            importedRoots = settingsConfigStore.skillRootsFlow.first(),
            projectSkillsEnabled = settingsConfigStore.trustProjectSkillsFlow.first()
        ).skills
    }

    override suspend fun getEnabledSkills(config: AgentConfig): List<SkillDefinition> {
        val enabledIds = config.enabledSkillIds.toSet()
        return listSkills().filter { it.id in enabledIds }
    }

    override suspend fun getSkillByName(name: String): SkillDefinition? {
        val normalized = name.trim()
        if (normalized.isBlank()) return null
        return listSkills().firstOrNull { skill ->
            skill.name.equals(normalized, ignoreCase = true) ||
                skill.id.equals(normalized, ignoreCase = true)
        }
    }

    override suspend fun activateSkill(name: String): SkillActivationPayload? {
        val skill = getSkillByName(name) ?: return null
        val content = if (!skill.documentUri.isNullOrBlank()) {
            skillContentStore.readText(skill.documentUri).content.ifBlank { skill.promptContent() }
        } else {
            skill.bodyMarkdown.ifBlank { skill.promptContent() }
        }
        return SkillActivationPayload(
            skill = skill,
            content = content,
            resources = skill.resourceEntries
        )
    }

    override suspend fun readSkillResource(name: String, relativePath: String, sessionId: String, maxChars: Int): SkillResourceReadResult? {
        val skill = getSkillByName(name) ?: return null
        if (!activatedSkillSessionStore.isActivated(sessionId, skill.primaryActivationName(), skill.contentHash)) {
            return null
        }
        val normalized = normalizeRelativePath(relativePath) ?: return null
        if (skill.scope == SkillScope.PROJECT) {
            return readProjectSkillResource(skill, normalized, maxChars)
        }
        if (normalized.equals("SKILL.md", ignoreCase = true)) {
            val documentUri = skill.documentUri ?: return null
            val content = skillContentStore.readText(documentUri, maxChars)
            return SkillResourceReadResult(skill.primaryActivationName(), normalized, content.content, content.totalBytes, content.truncated)
        }
        val matchingResource = skill.resourceEntries.firstOrNull { it.relativePath.equals(normalized, ignoreCase = true) } ?: return null
        val documentUri = matchingResource.documentUri ?: return null
        val content = skillContentStore.readText(documentUri, maxChars)
        return SkillResourceReadResult(skill.primaryActivationName(), matchingResource.relativePath, content.content, content.totalBytes, content.truncated)
    }

    override suspend fun importSkillsFromDirectory(uri: Uri): SkillImportResult {
        val processed = phoneControlUnlockProcessor.process(skillDirectoryScanner.scan(uri))
        val scanned = processed.map { it.scannedSkill }
        if (scanned.isEmpty()) {
            settingsConfigStore.addSkillRootUri(uri.toString())
            return SkillImportResult(
                importedCount = 0,
                updatedCount = 0,
                skippedCount = 0,
                duplicateCount = 0,
                errors = processed.flatMapTo(mutableListOf()) { it.warnings }.ifEmpty {
                    listOf("No SKILL.md files were found.")
                }
            )
        }

        val builtinIds = skillCatalog.skills.map { it.id }.toSet()
        val existingSkills = customSkillDao.getSkills()
        val existingImported = existingSkills.associateBy { it.id }
        val existingFromTree = existingSkills.filter { it.sourceTreeUri == uri.toString() }
        val idsSeenInScan = mutableSetOf<String>()
        val errors = processed.flatMapTo(mutableListOf()) { it.warnings }
        val entities = mutableListOf<com.example.nanobot.core.database.entity.CustomSkillEntity>()
        var importedCount = 0
        var updatedCount = 0
        var skippedCount = 0
        var duplicateCount = 0
        val now = System.currentTimeMillis()

        scanned.forEach { scannedSkill ->
            val skill = scannedSkill.skill
            if (skill.id in builtinIds) {
                skippedCount += 1
                errors += "${skill.title}: skill id '${skill.id}' conflicts with a builtin skill."
                return@forEach
            }
            if (!idsSeenInScan.add(skill.id)) {
                duplicateCount += 1
                errors += "${skill.title}: duplicate skill id '${skill.id}' in the same import."
                return@forEach
            }

            val existing = existingImported[skill.id]
            entities += skill.toEntity(
                importedAt = existing?.importedAt ?: now,
                updatedAt = now
            )
            if (existing == null) {
                importedCount += 1
            } else {
                updatedCount += 1
            }
        }

        if (entities.isNotEmpty()) {
            customSkillDao.upsertAll(entities)
        }
        val scannedIds = entities.map { it.id }.toSet()
        existingFromTree.filterNot { it.id in scannedIds }.forEach { stale ->
            customSkillDao.deleteById(stale.id)
        }
        settingsConfigStore.addSkillRootUri(uri.toString())
        cleanupEnabledSkillIds()

        return SkillImportResult(
            importedCount = importedCount,
            updatedCount = updatedCount,
            skippedCount = skippedCount,
            duplicateCount = duplicateCount,
            errors = errors
        )
    }

    override suspend fun importSkillsFromZip(uri: Uri): SkillImportResult {
        val extractedRoot = skillZipImporter.importArchive(uri)
        return importSkillsFromDirectory(extractedRoot)
    }

    override suspend fun getPhoneControlUnlockReceipt(packageId: String): PhoneControlUnlockReceipt? {
        return phoneControlUnlockStore.findReceipt(packageId)
    }

    override suspend fun getHiddenToolEntitlements(skill: SkillDefinition): Set<String> {
        val packageId = skill.metadata[UNLOCK_PACKAGE_ID_KEY]?.takeIf { it.isNotBlank() } ?: return emptySet()
        val receipt = phoneControlUnlockStore.findReceipt(packageId) ?: return emptySet()
        if (!receipt.skillId.equals(skill.id, ignoreCase = true)) return emptySet()
        if (!receipt.skillSha256.equals(skill.contentHash.orEmpty(), ignoreCase = true)) return emptySet()
        return phoneControlUnlockProfileRegistry.resolveTools(receipt.unlockProfiles)
    }

    override suspend fun removeImportedSkill(id: String) {
        customSkillDao.deleteById(id)
        cleanupEnabledSkillIds()
    }

    override suspend fun rescanImportedSkills(): SkillImportResult? {
        val roots = settingsConfigStore.skillRootsFlow.first()
        if (roots.isEmpty()) return null
        val aggregateErrors = mutableListOf<String>()
        var importedCount = 0
        var updatedCount = 0
        var skippedCount = 0
        var duplicateCount = 0
        roots.forEach { root ->
            val result = importSkillsFromDirectory(Uri.parse(root))
            importedCount += result.importedCount
            updatedCount += result.updatedCount
            skippedCount += result.skippedCount
            duplicateCount += result.duplicateCount
            aggregateErrors += result.errors
        }
        return SkillImportResult(importedCount, updatedCount, skippedCount, duplicateCount, aggregateErrors)
    }
    private suspend fun cleanupEnabledSkillIds() {
        val config = settingsConfigStore.configFlow.first()
        val validIds = listSkills().map { it.id }.toSet()
        val filtered = config.enabledSkillIds.filter { it in validIds }
        if (filtered != config.enabledSkillIds) {
            settingsConfigStore.save(config.copy(enabledSkillIds = filtered))
        }
    }

    private fun normalizeRelativePath(relativePath: String): String? {
        val normalized = relativePath.trim().replace('\\', '/')
        if (normalized.isBlank()) return null
        if (normalized.startsWith("/") || normalized.contains("..")) return null
        return normalized
    }

    private suspend fun readProjectSkillResource(skill: SkillDefinition, relativePath: String, maxChars: Int): SkillResourceReadResult? {
        val resolvedPath = when {
            relativePath.equals("SKILL.md", ignoreCase = true) -> projectSkillDocumentPath(skill)
            else -> {
                val matchingResource = skill.resourceEntries.firstOrNull { it.relativePath.equals(relativePath, ignoreCase = true) } ?: return null
                resolveProjectSkillPath(skill, matchingResource.relativePath)
            }
        } ?: return null

        val content = workspaceRepository.readText(resolvedPath, maxChars)
        return SkillResourceReadResult(
            skillName = skill.primaryActivationName(),
            relativePath = relativePath,
            content = content.content,
            totalBytes = content.totalBytes.toInt(),
            truncated = content.truncated
        )
    }

    private fun projectSkillDocumentPath(skill: SkillDefinition): String? {
        skill.locationUri?.takeIf { it.isNotBlank() }?.let { return normalizeWorkspacePath(it) }
        return resolveProjectSkillPath(skill, "SKILL.md")
    }

    private fun resolveProjectSkillPath(skill: SkillDefinition, relativePath: String): String? {
        val root = skill.skillRootUri?.let(::normalizeWorkspacePath) ?: skill.locationUri
            ?.substringBeforeLast('/', missingDelimiterValue = "")
            ?.let(::normalizeWorkspacePath)
            ?: return null
        val normalizedRelativePath = normalizeWorkspacePath(relativePath)
        val candidate = listOf(root, normalizedRelativePath)
            .filter { it.isNotBlank() }
            .joinToString("/")
        return candidate.takeIf { it == root || it.startsWith("$root/") }
    }

    private fun normalizeWorkspacePath(path: String): String {
        val normalized = path.trim().replace('\\', '/')
        val parts = normalized.split('/')
            .filter { it.isNotBlank() && it != "." }
        require(!normalized.startsWith("/")) { "Absolute workspace paths are not allowed for skill resources." }
        require(parts.none { it == ".." }) { "Parent path traversal is not allowed for skill resources." }
        return parts.joinToString("/")
    }

    private companion object {
        const val UNLOCK_PACKAGE_ID_KEY = "hidden_unlock_package_id"
    }
}
