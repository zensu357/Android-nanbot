package com.example.nanobot.domain.repository

import android.net.Uri
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.skills.SkillActivationPayload
import com.example.nanobot.core.skills.SkillDiscoveryIssue
import com.example.nanobot.core.skills.SkillDefinition
import com.example.nanobot.core.skills.SkillImportResult
import com.example.nanobot.core.skills.SkillResourceReadResult
import com.example.nanobot.core.skills.PhoneControlUnlockReceipt
import kotlinx.coroutines.flow.Flow

interface SkillRepository {
    fun observeSkills(): Flow<List<SkillDefinition>>
    fun observeDiscoveryIssues(): Flow<List<SkillDiscoveryIssue>>
    suspend fun listSkills(): List<SkillDefinition>
    suspend fun getEnabledSkills(config: AgentConfig): List<SkillDefinition>
    suspend fun getSkillByName(name: String): SkillDefinition?
    suspend fun activateSkill(name: String): SkillActivationPayload?
    suspend fun readSkillResource(name: String, relativePath: String, sessionId: String, maxChars: Int = 4000): SkillResourceReadResult?
    suspend fun importSkillsFromDirectory(uri: Uri): SkillImportResult
    suspend fun importSkillsFromZip(uri: Uri): SkillImportResult
    suspend fun removeImportedSkill(id: String)
    suspend fun rescanImportedSkills(): SkillImportResult?
    suspend fun getPhoneControlUnlockReceipt(packageId: String): PhoneControlUnlockReceipt?
    suspend fun getHiddenToolEntitlements(skill: SkillDefinition): Set<String>
}
