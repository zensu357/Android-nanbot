package com.example.nanobot.domain.repository

import android.net.Uri
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.skills.SkillDefinition
import com.example.nanobot.core.skills.SkillImportResult
import kotlinx.coroutines.flow.Flow

interface SkillRepository {
    fun observeSkills(): Flow<List<SkillDefinition>>
    suspend fun listSkills(): List<SkillDefinition>
    suspend fun getEnabledSkills(config: AgentConfig): List<SkillDefinition>
    suspend fun importSkillsFromDirectory(uri: Uri): SkillImportResult
    suspend fun removeImportedSkill(id: String)
    suspend fun rescanImportedSkills(): SkillImportResult?
}
