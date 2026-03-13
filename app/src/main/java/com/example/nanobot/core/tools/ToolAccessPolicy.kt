package com.example.nanobot.core.tools

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import javax.inject.Inject

enum class ToolAccessCategory {
    LOCAL_READ_ONLY,
    LOCAL_ORCHESTRATION,
    WORKSPACE_READ_ONLY,
    WORKSPACE_SIDE_EFFECT,
    EXTERNAL_READ_ONLY,
    LOCAL_SIDE_EFFECT,
    EXTERNAL_SIDE_EFFECT
}

data class ToolAccessDecision(
    val allowed: Boolean,
    val denialMessage: String? = null
)

class ToolAccessPolicy @Inject constructor() {
    fun filterVisibleTools(tools: Collection<AgentTool>, config: AgentConfig, runContext: AgentRunContext): List<AgentTool> {
        if (!config.enableTools) return emptyList()
        return tools
            .filter { isAllowedCategory(it.accessCategory, config) }
            .filter { isAllowedBySkill(it.name, runContext) }
            .sortedBy { it.name }
    }

    fun assertExecutable(tool: AgentTool, config: AgentConfig, runContext: AgentRunContext): ToolAccessDecision {
        if (!config.enableTools) {
            return ToolAccessDecision(
                allowed = false,
                denialMessage = "Tool execution is disabled in the current configuration."
            )
        }

        if (!isAllowedBySkill(tool.name, runContext)) {
            return ToolAccessDecision(
                allowed = false,
                denialMessage = "Tool '${tool.name}' is not allowed by the currently activated skill policy."
            )
        }

        return if (isAllowedCategory(tool.accessCategory, config)) {
            ToolAccessDecision(allowed = true)
        } else {
            ToolAccessDecision(
                allowed = false,
                denialMessage = "Tool '${tool.name}' is blocked by the current workspace-restricted mode policy. Only local read-only tools, local orchestration tools, and workspace sandbox read/write tools may execute while workspace-restricted mode is enabled. External web access, dynamic MCP tools, and non-workspace side-effect tools are blocked."
            )
        }
    }

    fun describe(config: AgentConfig): String {
        return when {
            !config.enableTools -> "All tools are disabled by settings."
            config.restrictToWorkspace -> "Workspace-restricted mode is active: local read-only tools, local orchestration tools, and workspace sandbox read/write tools are available; external web access, dynamic MCP tools, and non-workspace side-effect tools are blocked."
            else -> "Unrestricted mode is active: all registered tools are available."
        }
    }

    private fun isAllowedCategory(category: ToolAccessCategory, config: AgentConfig): Boolean {
        if (!config.restrictToWorkspace) {
            return true
        }
        return category == ToolAccessCategory.LOCAL_READ_ONLY ||
            category == ToolAccessCategory.LOCAL_ORCHESTRATION ||
            category == ToolAccessCategory.WORKSPACE_READ_ONLY ||
            category == ToolAccessCategory.WORKSPACE_SIDE_EFFECT
    }

    private fun isAllowedBySkill(toolName: String, runContext: AgentRunContext): Boolean {
        val allowed = runContext.allowedToolNames ?: return true
        if (allowed.isEmpty()) return true
        return toolName in allowed || toolName == "activate_skill" || toolName == "read_skill_resource"
    }
}
