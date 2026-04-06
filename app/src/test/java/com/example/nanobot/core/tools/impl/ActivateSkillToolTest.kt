package com.example.nanobot.core.tools.impl

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.skills.ActivatedSkillSessionStore
import com.example.nanobot.core.skills.SkillActivationFormatter
import com.example.nanobot.core.skills.SkillDefinition
import com.example.nanobot.core.skills.SkillSource
import com.example.nanobot.domain.repository.SkillRepository
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ActivateSkillToolTest {
    @Test
    fun includesHiddenEntitlementsInFormattedAllowedTools() = runTest {
        val skill = SkillDefinition(
            id = "phone-operator-basic",
            name = "phone-operator-basic",
            title = "Phone Operator",
            description = "Unlock phone control",
            source = SkillSource.IMPORTED,
            allowedTools = listOf("read_current_ui"),
            bodyMarkdown = "Use phone tools carefully."
        )
        val tool = ActivateSkillTool(
            skillRepository = object : SkillRepository {
                override fun observeSkills(): Flow<List<SkillDefinition>> = flowOf(listOf(skill))
                override fun observeDiscoveryIssues() = flowOf(emptyList<com.example.nanobot.core.skills.SkillDiscoveryIssue>())
                override suspend fun listSkills(): List<SkillDefinition> = listOf(skill)
                override suspend fun getEnabledSkills(config: AgentConfig): List<SkillDefinition> = listOf(skill)
                override suspend fun getSkillByName(name: String): SkillDefinition? = skill.takeIf { it.name == name || it.id == name }
                override suspend fun activateSkill(name: String) = com.example.nanobot.core.skills.SkillActivationPayload(skill, skill.bodyMarkdown, emptyList())
                override suspend fun readSkillResource(name: String, relativePath: String, sessionId: String, maxChars: Int) = null
                override suspend fun importSkillsFromDirectory(uri: android.net.Uri) = error("unused")
                override suspend fun importSkillsFromZip(uri: android.net.Uri) = error("unused")
                override suspend fun removeImportedSkill(id: String) = Unit
                override suspend fun rescanImportedSkills() = null
                override suspend fun getPhoneControlUnlockReceipt(packageId: String) = null
                override suspend fun listPendingPhoneControlUnlockConsents() = emptyList<com.example.nanobot.core.skills.PendingPhoneControlUnlockConsent>()
                override suspend fun acceptPendingPhoneControlUnlockConsent(packageId: String) = null
                override suspend fun rejectPendingPhoneControlUnlockConsent(packageId: String) = Unit
                override suspend fun getHiddenToolEntitlements(skill: SkillDefinition): Set<String> {
                    return setOf("analyze_screenshot", "visual_verify")
                }
            },
            activatedSkillSessionStore = ActivatedSkillSessionStore(),
            formatter = SkillActivationFormatter()
        )

        val result = tool.execute(
            arguments = buildJsonObject { put("name", "phone-operator-basic") },
            config = AgentConfig(),
            runContext = AgentRunContext.root("session-1")
        )

        assertTrue(result.contains("Allowed Tools:"))
        assertTrue(result.contains("analyze_screenshot"))
        assertTrue(result.contains("visual_verify"))
    }
}
