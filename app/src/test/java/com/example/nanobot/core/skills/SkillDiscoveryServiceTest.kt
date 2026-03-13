package com.example.nanobot.core.skills

import com.example.nanobot.core.workspace.WorkspaceEntry
import com.example.nanobot.core.workspace.WorkspaceFileContent
import com.example.nanobot.core.workspace.WorkspaceRoot
import com.example.nanobot.domain.repository.WorkspaceRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SkillDiscoveryServiceTest {
    @Test
    fun projectSkillsAreHiddenUntilWorkspaceIsTrusted() = runTest {
        val service = SkillDiscoveryService(
            skillCatalog = SkillCatalog(),
            workspaceRepository = FakeWorkspaceRepository(
                fileContents = mapOf(
                    ".agents/skills/release-notes/SKILL.md" to "---\nname: release-notes\ndescription: Generate release notes\n---\nUse this skill.",
                    "skills/local-plan/SKILL.md" to "---\nname: local-plan\ndescription: Local project planning\n---\nUse this project skill."
                ),
                listings = mapOf(
                    ".agents/skills" to listOf(
                        WorkspaceEntry(".agents/skills/release-notes", isDirectory = true)
                    ),
                    ".agents/skills/release-notes" to listOf(
                        WorkspaceEntry(".agents/skills/release-notes/SKILL.md", isDirectory = false)
                    ),
                    "skills" to listOf(
                        WorkspaceEntry("skills/local-plan", isDirectory = true)
                    ),
                    "skills/local-plan" to listOf(
                        WorkspaceEntry("skills/local-plan/SKILL.md", isDirectory = false)
                    )
                )
            ),
            skillMarkdownParser = SkillMarkdownParser(),
            skillResourceIndexer = SkillResourceIndexer()
        )

        val untrusted = service.discover(emptyList(), emptyList(), projectSkillsEnabled = false)
        val trusted = service.discover(emptyList(), emptyList(), projectSkillsEnabled = true)

        assertFalse(untrusted.skills.any { it.scope == SkillScope.PROJECT })
        assertTrue(untrusted.issues.any { it.message.contains("disabled") })
        assertTrue(trusted.skills.any { it.scope == SkillScope.PROJECT && it.name == "release-notes" })
        assertTrue(trusted.skills.any { it.scope == SkillScope.PROJECT && it.name == "local-plan" })
    }

    @Test
    fun importedSkillsOverrideBuiltinNamesDeterministically() = runTest {
        val service = SkillDiscoveryService(SkillCatalog(), FakeWorkspaceRepository(), SkillMarkdownParser(), SkillResourceIndexer())
        val imported = listOf(
            SkillDefinition(
                id = "coding_editor",
                name = "coding_editor",
                title = "Custom Coding Editor",
                description = "Imported override",
                source = SkillSource.IMPORTED,
                scope = SkillScope.IMPORTED
            )
        )

        val snapshot = service.discover(imported, listOf("content://skills"), projectSkillsEnabled = false)
        val resolved = snapshot.skills.first { it.primaryActivationName().equals("coding_editor", ignoreCase = true) }

        assertEquals("Custom Coding Editor", resolved.title)
        assertTrue(snapshot.issues.any { it.message.contains("overrides") })
    }

    @Test
    fun importedSkillsArePromotedToUserScopeWhenRootMatches() = runTest {
        val service = SkillDiscoveryService(SkillCatalog(), FakeWorkspaceRepository(), SkillMarkdownParser(), SkillResourceIndexer())
        val imported = listOf(
            SkillDefinition(
                id = "release-notes",
                name = "release-notes",
                title = "Release Notes",
                description = "Imported user skill",
                source = SkillSource.IMPORTED,
                scope = SkillScope.IMPORTED,
                sourceTreeUri = "content://skills/root"
            )
        )

        val snapshot = service.discover(imported, listOf("content://skills/root"), projectSkillsEnabled = false)

        assertEquals(SkillScope.USER, snapshot.skills.first { it.name == "release-notes" }.scope)
    }

    @Test
    fun compatibilityRootOverridesProjectRootWithStablePriority() = runTest {
        val service = SkillDiscoveryService(
            skillCatalog = SkillCatalog(),
            workspaceRepository = FakeWorkspaceRepository(
                fileContents = mapOf(
                    "skills/shared/SKILL.md" to "---\nname: shared\ndescription: Project root version\n---\nProject version.",
                    ".agents/skills/shared/SKILL.md" to "---\nname: shared\ndescription: Compatibility root version\n---\nCompatibility version."
                ),
                listings = mapOf(
                    "skills" to listOf(WorkspaceEntry("skills/shared", isDirectory = true)),
                    "skills/shared" to listOf(WorkspaceEntry("skills/shared/SKILL.md", isDirectory = false)),
                    ".agents/skills" to listOf(WorkspaceEntry(".agents/skills/shared", isDirectory = true)),
                    ".agents/skills/shared" to listOf(WorkspaceEntry(".agents/skills/shared/SKILL.md", isDirectory = false))
                )
            ),
            skillMarkdownParser = SkillMarkdownParser(),
            skillResourceIndexer = SkillResourceIndexer()
        )

        val snapshot = service.discover(emptyList(), emptyList(), projectSkillsEnabled = true)
        val shared = snapshot.skills.first { it.name == "shared" }

        assertEquals("Compatibility root version", shared.description)
        assertTrue(snapshot.issues.any { it.message.contains("project compatibility overrides project") })
    }

    private class FakeWorkspaceRepository(
        private val fileContents: Map<String, String> = emptyMap(),
        private val listings: Map<String, List<WorkspaceEntry>> = emptyMap()
    ) : WorkspaceRepository {
        override suspend fun getWorkspaceRoot(): WorkspaceRoot = WorkspaceRoot(isAvailable = true)

        override suspend fun list(relativePath: String, limit: Int): List<WorkspaceEntry> {
            return listings[relativePath].orEmpty().take(limit)
        }

        override suspend fun readText(relativePath: String, maxChars: Int): WorkspaceFileContent {
            val content = fileContents[relativePath].orEmpty().take(maxChars)
            return WorkspaceFileContent(relativePath, content, false, content.length.toLong())
        }

        override suspend fun search(query: String, relativePath: String, limit: Int): List<com.example.nanobot.core.workspace.WorkspaceSearchHit> {
            return emptyList()
        }

        override suspend fun writeText(relativePath: String, content: String, overwrite: Boolean) = throw UnsupportedOperationException()

        override suspend fun replaceText(relativePath: String, find: String, replaceWith: String, expectedOccurrences: Int?) = throw UnsupportedOperationException()
    }
}
