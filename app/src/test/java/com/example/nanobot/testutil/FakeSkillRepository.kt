package com.example.nanobot.testutil

import android.net.Uri
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.skills.SkillActivationPayload
import com.example.nanobot.core.skills.SkillCatalog
import com.example.nanobot.core.skills.SkillDiscoveryIssue
import com.example.nanobot.core.skills.SkillDefinition
import com.example.nanobot.core.skills.SkillImportResult
import com.example.nanobot.core.skills.PhoneControlUnlockReceipt
import com.example.nanobot.core.skills.SkillResourceReadResult
import com.example.nanobot.domain.repository.SkillRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeSkillRepository(
    private val skills: List<SkillDefinition> = SkillCatalog().skills,
    private val discoveryIssues: List<SkillDiscoveryIssue> = emptyList()
) : SkillRepository {
    override fun observeSkills(): Flow<List<SkillDefinition>> = flowOf(skills)

    override fun observeDiscoveryIssues(): Flow<List<SkillDiscoveryIssue>> = flowOf(discoveryIssues)

    override suspend fun listSkills(): List<SkillDefinition> = skills

    override suspend fun getEnabledSkills(config: AgentConfig): List<SkillDefinition> {
        val enabledIds = config.enabledSkillIds.toSet()
        return skills.filter { it.id in enabledIds }
    }

    override suspend fun getSkillByName(name: String): SkillDefinition? {
        return skills.firstOrNull { it.name.equals(name, ignoreCase = true) || it.id.equals(name, ignoreCase = true) }
    }

    override suspend fun activateSkill(name: String): SkillActivationPayload? {
        val skill = getSkillByName(name) ?: return null
        return SkillActivationPayload(
            skill = skill,
            content = skill.bodyMarkdown.ifBlank { skill.promptContent() },
            resources = skill.resourceEntries
        )
    }

    override suspend fun readSkillResource(name: String, relativePath: String, sessionId: String, maxChars: Int): SkillResourceReadResult? = null

    override suspend fun importSkillsFromDirectory(uri: Uri): SkillImportResult {
        return SkillImportResult(0, 0, 0, 0, errors = listOf("Not supported in unit fake."))
    }

    override suspend fun importSkillsFromZip(uri: Uri): SkillImportResult {
        return SkillImportResult(0, 0, 0, 0, errors = listOf("Not supported in unit fake."))
    }

    override suspend fun removeImportedSkill(id: String) = Unit

    override suspend fun rescanImportedSkills(): SkillImportResult? = null

    override suspend fun getPhoneControlUnlockReceipt(packageId: String): PhoneControlUnlockReceipt? = null

    override suspend fun getHiddenToolEntitlements(skill: SkillDefinition): Set<String> = emptySet()
}
