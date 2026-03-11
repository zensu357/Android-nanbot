package com.example.nanobot.data.repository

import android.net.Uri
import com.example.nanobot.core.database.dao.CustomSkillDao
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.preferences.SettingsConfigStore
import com.example.nanobot.core.skills.SkillCatalog
import com.example.nanobot.core.skills.SkillDefinition
import com.example.nanobot.core.skills.SkillImportScanner
import com.example.nanobot.core.skills.SkillImportResult
import com.example.nanobot.data.mapper.toEntity
import com.example.nanobot.data.mapper.toModel
import com.example.nanobot.domain.repository.SkillRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
class SkillRepositoryImpl @Inject constructor(
    private val skillCatalog: SkillCatalog,
    private val customSkillDao: CustomSkillDao,
    private val skillDirectoryScanner: SkillImportScanner,
    private val settingsConfigStore: SettingsConfigStore
) : SkillRepository {

    override fun observeSkills(): Flow<List<SkillDefinition>> {
        return combine(
            flowOf(skillCatalog.skills),
            customSkillDao.observeSkills().map { entities -> entities.map { it.toModel() } }
        ) { builtin, imported ->
            mergeSkills(builtin, imported)
        }
    }

    override suspend fun listSkills(): List<SkillDefinition> {
        return mergeSkills(skillCatalog.skills, customSkillDao.getSkills().map { it.toModel() })
    }

    override suspend fun getEnabledSkills(config: AgentConfig): List<SkillDefinition> {
        val enabledIds = config.enabledSkillIds.toSet()
        return listSkills().filter { it.id in enabledIds }
    }

    override suspend fun importSkillsFromDirectory(uri: Uri): SkillImportResult {
        val scanned = skillDirectoryScanner.scan(uri)
        if (scanned.isEmpty()) {
            settingsConfigStore.saveSkillsDirectoryUri(uri.toString())
            return SkillImportResult(importedCount = 0, updatedCount = 0, skippedCount = 0, duplicateCount = 0, errors = listOf("No SKILL.md files were found."))
        }

        val builtinIds = skillCatalog.skills.map { it.id }.toSet()
        val existingSkills = customSkillDao.getSkills()
        val existingImported = existingSkills.associateBy { it.id }
        val existingFromTree = existingSkills.filter { it.sourceTreeUri == uri.toString() }
        val idsSeenInScan = mutableSetOf<String>()
        val errors = mutableListOf<String>()
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
        settingsConfigStore.saveSkillsDirectoryUri(uri.toString())
        cleanupEnabledSkillIds()

        return SkillImportResult(
            importedCount = importedCount,
            updatedCount = updatedCount,
            skippedCount = skippedCount,
            duplicateCount = duplicateCount,
            errors = errors
        )
    }

    override suspend fun removeImportedSkill(id: String) {
        customSkillDao.deleteById(id)
        cleanupEnabledSkillIds()
    }

    override suspend fun rescanImportedSkills(): SkillImportResult? {
        val uri = settingsConfigStore.skillsDirectoryUriFlow.first()?.takeIf { it.isNotBlank() } ?: return null
        return importSkillsFromDirectory(Uri.parse(uri))
    }

    private fun mergeSkills(
        builtin: List<SkillDefinition>,
        imported: List<SkillDefinition>
    ): List<SkillDefinition> {
        val builtinIds = builtin.map { it.id }.toSet()
        return (builtin + imported.filterNot { it.id in builtinIds })
            .sortedWith(compareBy<SkillDefinition> { it.source.name }.thenBy { it.title.lowercase() })
    }

    private suspend fun cleanupEnabledSkillIds() {
        val config = settingsConfigStore.configFlow.first()
        val validIds = listSkills().map { it.id }.toSet()
        val filtered = config.enabledSkillIds.filter { it in validIds }
        if (filtered != config.enabledSkillIds) {
            settingsConfigStore.save(config.copy(enabledSkillIds = filtered))
        }
    }
}
