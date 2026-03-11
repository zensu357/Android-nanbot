package com.example.nanobot.testutil

import android.net.Uri
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.skills.SkillCatalog
import com.example.nanobot.core.skills.SkillDefinition
import com.example.nanobot.core.skills.SkillImportResult
import com.example.nanobot.domain.repository.SkillRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeSkillRepository(
    private val skills: List<SkillDefinition> = SkillCatalog().skills
) : SkillRepository {
    override fun observeSkills(): Flow<List<SkillDefinition>> = flowOf(skills)

    override suspend fun listSkills(): List<SkillDefinition> = skills

    override suspend fun getEnabledSkills(config: AgentConfig): List<SkillDefinition> {
        val enabledIds = config.enabledSkillIds.toSet()
        return skills.filter { it.id in enabledIds }
    }

    override suspend fun importSkillsFromDirectory(uri: Uri): SkillImportResult {
        return SkillImportResult(0, 0, 0, 0, errors = listOf("Not supported in unit fake."))
    }

    override suspend fun removeImportedSkill(id: String) = Unit

    override suspend fun rescanImportedSkills(): SkillImportResult? = null
}
