package com.example.nanobot.data.repository

import android.net.Uri
import com.example.nanobot.core.database.dao.CustomSkillDao
import com.example.nanobot.core.database.entity.CustomSkillEntity
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.preferences.SettingsConfigStore
import com.example.nanobot.core.skills.ScannedSkill
import com.example.nanobot.core.skills.SkillCatalog
import com.example.nanobot.core.skills.SkillDefinition
import com.example.nanobot.core.skills.SkillImportResult
import com.example.nanobot.core.skills.SkillImportScanner
import com.example.nanobot.core.skills.SkillSource
import com.example.nanobot.data.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SkillRepositoryImplTest {
    @Test
    fun importSkillsMergesBuiltinAndImportedAndSupportsRescan() = runTest {
        val dao = FakeCustomSkillDao()
        val settingsStore = FakeSettingsStore()
        val scanner = FakeSkillScanner(
            mutableMapOf(
                "content://skills/tree" to listOf(
                    scannedSkill(
                        id = "android-refactor",
                        title = "Android Refactor",
                        description = "Refactor Android code",
                        documentUri = "content://skills/tree/android-refactor"
                    )
                )
            )
        )
        val repository = SkillRepositoryImpl(SkillCatalog(), dao, scanner, settingsStore)

        val importResult = repository.importSkillsFromDirectory(Uri.parse("content://skills/tree"))
        val merged = repository.listSkills()

        assertEquals(1, importResult.importedCount)
        assertTrue(merged.any { it.id == "coding_editor" })
        assertTrue(merged.any { it.id == "android-refactor" && it.isImported })

        scanner.skillsByUri["content://skills/tree"] = listOf(
            scannedSkill(
                id = "release-notes",
                title = "Release Notes",
                description = "Updated imported skill",
                documentUri = "content://skills/tree/release-notes"
            )
        )
        val rescanResult = repository.rescanImportedSkills()
        val rescanned = repository.listSkills().first { it.id == "release-notes" }
        val ids = repository.listSkills().map { it.id }

        assertNotNull(rescanResult)
        assertEquals(1, rescanResult.importedCount)
        assertEquals("Updated imported skill", rescanned.description)
        assertTrue("android-refactor" !in ids)
    }

    @Test
    fun importRejectsBuiltinIdCollisionsAndRemoveDeletesImportedSkill() = runTest {
        val dao = FakeCustomSkillDao()
        val settingsStore = FakeSettingsStore()
        val scanner = FakeSkillScanner(
            mutableMapOf(
                "content://skills/tree" to listOf(
                    scannedSkill(
                        id = "coding_editor",
                        title = "Conflicting Skill",
                        description = "Should be rejected",
                        documentUri = "content://skills/tree/conflict"
                    ),
                    scannedSkill(
                        id = "release-notes",
                        title = "Release Notes",
                        description = "Generate release notes",
                        documentUri = "content://skills/tree/release-notes"
                    )
                )
            )
        )
        val repository = SkillRepositoryImpl(SkillCatalog(), dao, scanner, settingsStore)
        settingsStore.save(AgentConfig(enabledSkillIds = listOf("release-notes", "coding_editor")))

        val result = repository.importSkillsFromDirectory(Uri.parse("content://skills/tree"))

        assertEquals(1, result.importedCount)
        assertEquals(1, result.skippedCount)
        assertTrue(result.errors.single().contains("builtin skill"))

        repository.removeImportedSkill("release-notes")
        val remainingIds = repository.observeSkills().first().map { it.id }
        val updatedConfig = settingsStore.configFlow.first()

        assertTrue("release-notes" !in remainingIds)
        assertTrue("coding_editor" in remainingIds)
        assertEquals(listOf("coding_editor"), updatedConfig.enabledSkillIds)
    }

    private fun scannedSkill(
        id: String,
        title: String,
        description: String,
        documentUri: String
    ): ScannedSkill {
        val skill = SkillDefinition(
            id = id,
            title = title,
            description = description,
            source = SkillSource.IMPORTED,
            instructions = "Follow this skill.",
            whenToUse = description,
            summaryPrompt = description,
            workflow = listOf("Step 1", "Step 2"),
            constraints = listOf("Stay concise"),
            documentUri = documentUri,
            sourceTreeUri = "content://skills/tree",
            contentHash = id
        )
        return ScannedSkill(skill, Uri.parse("content://skills/tree"), Uri.parse(documentUri))
    }

    private class FakeCustomSkillDao : CustomSkillDao {
        private val state = MutableStateFlow<List<CustomSkillEntity>>(emptyList())

        override fun observeSkills(): Flow<List<CustomSkillEntity>> = state

        override suspend fun getSkills(): List<CustomSkillEntity> = state.value

        override suspend fun upsertAll(skills: List<CustomSkillEntity>) {
            val map = state.value.associateBy { it.id }.toMutableMap()
            skills.forEach { map[it.id] = it }
            state.value = map.values.sortedBy { it.title }
        }

        override suspend fun deleteById(skillId: String) {
            state.value = state.value.filterNot { it.id == skillId }
        }

        override suspend fun deleteByTreeUri(treeUri: String) {
            state.value = state.value.filterNot { it.sourceTreeUri == treeUri }
        }
    }

    private class FakeSettingsStore : SettingsConfigStore {
        private val configState = MutableStateFlow(AgentConfig())
        private val skillsUriState = MutableStateFlow<String?>(null)

        override val configFlow: Flow<AgentConfig> = configState
        override val skillsDirectoryUriFlow: Flow<String?> = skillsUriState

        override suspend fun save(config: AgentConfig) {
            configState.value = config
        }

        override suspend fun saveSkillsDirectoryUri(uri: String?) {
            skillsUriState.value = uri
        }
    }

    private class FakeSkillScanner(
        val skillsByUri: MutableMap<String, List<ScannedSkill>>
    ) : SkillImportScanner {
        override suspend fun scan(treeUri: Uri): List<ScannedSkill> {
            return skillsByUri[treeUri.toString()].orEmpty()
        }
    }
}
