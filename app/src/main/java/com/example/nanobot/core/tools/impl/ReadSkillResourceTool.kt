package com.example.nanobot.core.tools.impl

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.tools.AgentTool
import com.example.nanobot.core.tools.ToolAccessCategory
import com.example.nanobot.domain.repository.SkillRepository
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class ReadSkillResourceTool @Inject constructor(
    private val skillRepository: SkillRepository
) : AgentTool {
    override val name: String = "read_skill_resource"
    override val description: String = "Reads a referenced file from an activated or discovered skill package using a skill-relative path"
    override val accessCategory: ToolAccessCategory = ToolAccessCategory.LOCAL_READ_ONLY
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("skillName") {
                put("type", "string")
                put("description", "Skill name owning the resource")
            }
            putJsonObject("relativePath") {
                put("type", "string")
                put("description", "Skill-relative resource path")
            }
            putJsonObject("maxChars") {
                put("type", "integer")
                put("description", "Optional max number of characters to return; defaults to 4000")
            }
        }
        put("required", buildJsonArray {
            add(JsonPrimitive("skillName"))
            add(JsonPrimitive("relativePath"))
        })
    }

    override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
        val skillName = arguments["skillName"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        val relativePath = arguments["relativePath"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        val maxChars = arguments["maxChars"]?.jsonPrimitive?.intOrNull ?: 4_000
        if (skillName.isBlank() || relativePath.isBlank()) {
            return "The 'skillName' and 'relativePath' fields are required for read_skill_resource."
        }
        val result = skillRepository.readSkillResource(skillName, relativePath, runContext.sessionId, maxChars)
            ?: return "Skill resource '$relativePath' for '$skillName' was not found or is not accessible."
        return buildString {
            appendLine("Skill: ${result.skillName}")
            appendLine("Resource: ${result.relativePath}")
            appendLine("Bytes: ${result.totalBytes}")
            appendLine("Truncated: ${result.truncated}")
            appendLine("Content:")
            append(result.content)
        }.trim()
    }
}
