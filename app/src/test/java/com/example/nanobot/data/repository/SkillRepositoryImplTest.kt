package com.example.nanobot.data.repository

import android.net.Uri
import com.example.nanobot.core.database.dao.CustomSkillDao
import com.example.nanobot.core.database.entity.CustomSkillEntity
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.preferences.SettingsConfigStore
import com.example.nanobot.core.skills.ActivatedSkillSource
import com.example.nanobot.core.skills.ActivatedSkillSessionStore
import com.example.nanobot.core.skills.ScannedSkill
import com.example.nanobot.core.skills.SkillContentStore
import com.example.nanobot.core.skills.SkillCatalog
import com.example.nanobot.core.skills.SkillDiscoveryService
import com.example.nanobot.core.skills.SkillDefinition
import com.example.nanobot.core.skills.SkillImportResult
import com.example.nanobot.core.skills.SkillImportScanner
import com.example.nanobot.core.skills.SkillMarkdownParser
import com.example.nanobot.core.skills.SkillResourceEntry
import com.example.nanobot.core.skills.SkillResourceIndexer
import com.example.nanobot.core.skills.SkillResourceType
import com.example.nanobot.core.skills.SkillScope
import com.example.nanobot.core.skills.SkillSource
import com.example.nanobot.core.skills.SkillZipImporter
import com.example.nanobot.core.workspace.WorkspaceFileContent
import com.example.nanobot.core.workspace.WorkspaceRoot
import com.example.nanobot.domain.repository.WorkspaceRepository
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
        val activatedSkillStore = ActivatedSkillSessionStore()
        val workspaceRepository = FakeWorkspaceRepository()
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
        val repository = SkillRepositoryImpl(SkillCatalog(), dao, scanner, settingsStore, FakeSkillContentStore(), discoveryService(workspaceRepository), zipImporter(), activatedSkillStore, workspaceRepository)

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
        val activatedSkillStore = ActivatedSkillSessionStore()
        val workspaceRepository = FakeWorkspaceRepository()
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
        val repository = SkillRepositoryImpl(SkillCatalog(), dao, scanner, settingsStore, FakeSkillContentStore(), discoveryService(workspaceRepository), zipImporter(), activatedSkillStore, workspaceRepository)
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

    @Test
    fun activateSkillReturnsContentAndReadSkillResourceUsesIndexedUris() = runTest {
        val dao = FakeCustomSkillDao()
        val settingsStore = FakeSettingsStore()
        val activatedSkillStore = ActivatedSkillSessionStore()
        val workspaceRepository = FakeWorkspaceRepository()
        val scanner = FakeSkillScanner(mutableMapOf())
        val contentStore = FakeSkillContentStore(
            mapOf(
                "content://skills/tree/release-notes" to "# Release Notes\nUse this skill.",
                "content://skills/tree/references/guide.md" to "Guide contents"
            )
        )
        val repository = SkillRepositoryImpl(SkillCatalog(), dao, scanner, settingsStore, contentStore, discoveryService(workspaceRepository), zipImporter(), activatedSkillStore, workspaceRepository)
        dao.upsertAll(
            listOf(
                SkillDefinition(
                    id = "release-notes",
                    name = "release-notes",
                    title = "Release Notes",
                    description = "Generate release notes",
                    source = SkillSource.IMPORTED,
                    bodyMarkdown = "# Release Notes\nUse this skill.",
                    documentUri = "content://skills/tree/release-notes",
                    sourceTreeUri = "content://skills/tree",
                    resourceEntries = listOf(
                        SkillResourceEntry(
                            relativePath = "references/guide.md",
                            type = SkillResourceType.REFERENCE,
                            documentUri = "content://skills/tree/references/guide.md"
                        )
                    ),
                    contentHash = "hash"
                ).toEntity(importedAt = 1L, updatedAt = 1L)
            )
        )

        activatedSkillStore.markActivated("session-1", "release-notes", "hash", ActivatedSkillSource.MODEL)
        val activation = repository.activateSkill("release-notes")
        val resource = repository.readSkillResource("release-notes", "references/guide.md", "session-1", 4000)

        assertEquals("# Release Notes\nUse this skill.", activation?.content)
        assertEquals("Guide contents", resource?.content)
    }

    @Test
    fun readSkillResourceRequiresActivatedSkill() = runTest {
        val dao = FakeCustomSkillDao()
        val settingsStore = FakeSettingsStore()
        val activatedSkillStore = ActivatedSkillSessionStore()
        val workspaceRepository = FakeWorkspaceRepository()
        val repository = SkillRepositoryImpl(
            SkillCatalog(),
            dao,
            FakeSkillScanner(mutableMapOf()),
            settingsStore,
            FakeSkillContentStore(mapOf("content://skills/tree/references/guide.md" to "Guide contents")),
            discoveryService(workspaceRepository),
            zipImporter(),
            activatedSkillStore,
            workspaceRepository
        )
        dao.upsertAll(
            listOf(
                SkillDefinition(
                    id = "release-notes",
                    name = "release-notes",
                    title = "Release Notes",
                    description = "Generate release notes",
                    source = SkillSource.IMPORTED,
                    documentUri = "content://skills/tree/release-notes",
                    resourceEntries = listOf(
                        SkillResourceEntry(
                            relativePath = "references/guide.md",
                            type = SkillResourceType.REFERENCE,
                            documentUri = "content://skills/tree/references/guide.md"
                        )
                    ),
                    contentHash = "hash"
                ).toEntity(importedAt = 1L, updatedAt = 1L)
            )
        )

        val resource = repository.readSkillResource("release-notes", "references/guide.md", "session-1", 4000)

        assertEquals(null, resource)
    }

    @Test
    fun projectSkillResourcesReadFromWorkspaceAndRejectTraversal() = runTest {
        val dao = FakeCustomSkillDao()
        val settingsStore = FakeSettingsStore()
        val activatedSkillStore = ActivatedSkillSessionStore()
        val workspaceRepository = FakeWorkspaceRepository(
            fileContents = mapOf(
                ".agents/skills/release-notes/references/guide.md" to "Workspace guide",
                ".agents/skills/release-notes/SKILL.md" to "---\nname: release-notes\ndescription: Generate release notes\n---\nUse this skill."
            )
        )
        val repository = SkillRepositoryImpl(
            SkillCatalog(),
            dao,
            FakeSkillScanner(mutableMapOf()),
            settingsStore,
            FakeSkillContentStore(),
            discoveryService(workspaceRepository),
            zipImporter(),
            activatedSkillStore,
            workspaceRepository
        )
        dao.upsertAll(
            listOf(
                SkillDefinition(
                    id = "release-notes",
                    name = "release-notes",
                    title = "Release Notes",
                    description = "Generate release notes",
                    source = SkillSource.IMPORTED,
                    scope = SkillScope.PROJECT,
                    locationUri = ".agents/skills/release-notes/SKILL.md",
                    skillRootUri = ".agents/skills/release-notes",
                    resourceEntries = listOf(
                        SkillResourceEntry(
                            relativePath = "references/guide.md",
                            type = SkillResourceType.REFERENCE,
                            documentUri = null
                        )
                    ),
                    contentHash = "project-hash"
                ).toEntity(importedAt = 1L, updatedAt = 1L)
            )
        )

        activatedSkillStore.markActivated("session-1", "release-notes", "project-hash", ActivatedSkillSource.MODEL)
        val resource = repository.readSkillResource("release-notes", "references/guide.md", "session-1", 4000)
        val traversal = repository.readSkillResource("release-notes", "../secrets.txt", "session-1", 4000)

        assertEquals("Workspace guide", resource?.content)
        assertEquals(null, traversal)
    }

    private fun scannedSkill(
        id: String,
        title: String,
        description: String,
        documentUri: String
    ): ScannedSkill {
        val skill = SkillDefinition(
            id = id,
            name = id,
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

        override suspend fun getSkillById(skillId: String): CustomSkillEntity? = state.value.firstOrNull { it.id == skillId }

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
        private val rootsState = MutableStateFlow<List<String>>(emptyList())
        private val trustState = MutableStateFlow(false)

        override val configFlow: Flow<AgentConfig> = configState
        override val skillsDirectoryUriFlow: Flow<String?> = skillsUriState
        override val skillRootsFlow: Flow<List<String>> = rootsState
        override val trustProjectSkillsFlow: Flow<Boolean> = trustState

        override suspend fun save(config: AgentConfig) {
            configState.value = config
        }

        override suspend fun saveSkillsDirectoryUri(uri: String?) {
            skillsUriState.value = uri
        }

        override suspend fun addSkillRootUri(uri: String) {
            rootsState.value = (rootsState.value + uri).distinct()
            skillsUriState.value = uri
        }

        override suspend fun removeSkillRootUri(uri: String) {
            rootsState.value = rootsState.value.filterNot { it == uri }
            if (skillsUriState.value == uri) {
                skillsUriState.value = rootsState.value.lastOrNull()
            }
        }

        override suspend fun setTrustProjectSkills(trusted: Boolean) {
            trustState.value = trusted
        }
    }

    private class FakeSkillScanner(
        val skillsByUri: MutableMap<String, List<ScannedSkill>>
    ) : SkillImportScanner {
        override suspend fun scan(treeUri: Uri): List<ScannedSkill> {
            return skillsByUri[treeUri.toString()].orEmpty()
        }
    }

    private class FakeSkillContentStore(
        private val contentByUri: Map<String, String> = emptyMap()
    ) : SkillContentStore(context = org.robolectric.RuntimeEnvironment.getApplication()) {
        override fun readText(documentUri: String, maxChars: Int): com.example.nanobot.core.skills.SkillTextContent {
            val text = contentByUri[documentUri].orEmpty()
            val truncated = text.length > maxChars
            val content = if (truncated) text.take(maxChars) else text
            return com.example.nanobot.core.skills.SkillTextContent(content, text.toByteArray().size, truncated)
        }
    }

    private fun discoveryService(workspaceRepository: WorkspaceRepository): SkillDiscoveryService {
        return SkillDiscoveryService(SkillCatalog(), workspaceRepository, SkillMarkdownParser(), SkillResourceIndexer())
    }

    private fun zipImporter(): SkillZipImporter {
        return SkillZipImporter(org.robolectric.RuntimeEnvironment.getApplication())
    }

    private class FakeWorkspaceRepository(
            private val listings: Map<String, List<com.example.nanobot.core.workspace.WorkspaceEntry>> = emptyMap(),
            private val fileContents: Map<String, String> = emptyMap(),
            private val searchHits: List<com.example.nanobot.core.workspace.WorkspaceSearchHit> = emptyList()
        ) : WorkspaceRepository {
        override suspend fun getWorkspaceRoot(): WorkspaceRoot = WorkspaceRoot(isAvailable = false)

        override suspend fun list(relativePath: String, limit: Int) = listings[relativePath].orEmpty().take(limit)
        override suspend fun readText(relativePath: String, maxChars: Int): WorkspaceFileContent {
            val text = fileContents[relativePath].orEmpty()
            val truncated = text.length > maxChars
            return WorkspaceFileContent(relativePath, if (truncated) text.take(maxChars) else text, truncated, text.toByteArray().size.toLong())
        }
        override suspend fun search(query: String, relativePath: String, limit: Int) = searchHits.take(limit)
        override suspend fun writeText(relativePath: String, content: String, overwrite: Boolean) = throw UnsupportedOperationException()
        override suspend fun replaceText(relativePath: String, find: String, replaceWith: String, expectedOccurrences: Int?) = throw UnsupportedOperationException()
    }
}
