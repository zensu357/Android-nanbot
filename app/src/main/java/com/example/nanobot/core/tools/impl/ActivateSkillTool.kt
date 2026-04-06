package com.example.nanobot.core.tools.impl

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.skills.ActivatedSkillSource
import com.example.nanobot.core.skills.ActivatedSkillSessionStore
import com.example.nanobot.core.skills.SkillActivationFormatter
import com.example.nanobot.core.tools.AgentTool
import com.example.nanobot.core.tools.ToolAccessCategory
import com.example.nanobot.domain.repository.SkillRepository
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class ActivateSkillTool @Inject constructor(
    private val skillRepository: SkillRepository,
    private val activatedSkillSessionStore: ActivatedSkillSessionStore,
    private val formatter: SkillActivationFormatter
) : AgentTool {
    override val name: String = "activate_skill"
    override val description: String = "Loads a discovered skill by name and returns its full instructions plus bundled resource names"
    override val accessCategory: ToolAccessCategory = ToolAccessCategory.LOCAL_READ_ONLY
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("name") {
                put("type", "string")
                put("description", "Skill name to activate")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("name")) })
    }

    override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
        val name = arguments["name"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        if (name.isBlank()) {
            return "The 'name' field is required for activate_skill."
        }
        val payload = skillRepository.activateSkill(name)
            ?: return "Skill '$name' was not found or is unavailable."
        val alreadyActivated = activatedSkillSessionStore.isActivated(
            sessionId = runContext.sessionId,
            skillName = payload.skill.primaryActivationName(),
            contentHash = payload.skill.contentHash
        )
        activatedSkillSessionStore.markActivated(
            sessionId = runContext.sessionId,
            skillName = payload.skill.primaryActivationName(),
            contentHash = payload.skill.contentHash,
            source = ActivatedSkillSource.MODEL
        )
        val effectiveAllowedTools = (
            payload.skill.allowedTools + skillRepository.getHiddenToolEntitlements(payload.skill)
            ).toSortedSet()
        return formatter.format(payload, alreadyActivated, effectiveAllowedTools)
    }
}
