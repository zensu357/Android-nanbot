package com.example.nanobot.core.tools.impl

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.model.SubagentRequest
import com.example.nanobot.core.subagent.SubagentCoordinator
import com.example.nanobot.core.tools.AgentTool
import com.example.nanobot.core.tools.ToolAccessCategory
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class DelegateTaskTool @Inject constructor(
    private val subagentCoordinator: SubagentCoordinator
) : AgentTool {
    override val name: String = "delegate_task"
    override val description: String = "Delegates one focused subtask into an isolated child session and returns a compact summary"
    override val accessCategory: ToolAccessCategory = ToolAccessCategory.LOCAL_ORCHESTRATION
    override val availabilityHint: String = "Delegates one focused subtask; blocked once max subagent depth is reached"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("task") {
                put("type", "string")
                put("description", "The focused subtask that the child run should complete")
            }
            putJsonObject("title") {
                put("type", "string")
                put("description", "Optional child session title for debugging")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("task")) })
    }

    override fun isAvailable(config: AgentConfig, runContext: AgentRunContext): Boolean {
        return runContext.canDelegate()
    }

    override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
        val task = arguments["task"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        val title = arguments["title"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }
        if (task.isBlank()) {
            return "The 'task' field is required for delegate_task."
        }

        val result = subagentCoordinator.delegate(
            request = SubagentRequest(
                parentSessionId = runContext.sessionId,
                task = task,
                title = title,
                subagentDepth = runContext.subagentDepth,
                maxSubagentDepth = runContext.maxSubagentDepth,
                maxParallelSubagents = runContext.maxParallelSubagents,
                allowedToolNames = runContext.allowedToolNames,
                unlockedToolNames = runContext.unlockedToolNames,
                supportsVision = runContext.supportsVision
            ),
            config = config
        )

        return buildString {
            appendLine("Subagent delegation completed: ${result.completed}")
            appendLine("Parent Session ID: ${result.parentSessionId}")
            appendLine("Subagent Session ID: ${result.sessionId ?: "(not created)"}")
            appendLine("Subagent Depth: ${result.subagentDepth}")
            appendLine("Summary: ${result.summary}")
            if (result.artifactPaths.isNotEmpty()) {
                appendLine("Artifacts: ${result.artifactPaths.joinToString()}")
            }
        }.trim()
    }
}
