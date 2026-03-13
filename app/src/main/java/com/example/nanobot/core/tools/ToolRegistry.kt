package com.example.nanobot.core.tools

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.mcp.McpRegistry
import com.example.nanobot.core.mcp.McpToolAdapter
import com.example.nanobot.core.model.LlmToolDefinitionDto
import com.example.nanobot.core.model.LlmToolFunctionDto
import kotlinx.serialization.json.JsonObject

class ToolRegistry(
    private val validator: ToolValidator,
    private val accessPolicy: ToolAccessPolicy,
    private val mcpRegistry: McpRegistry? = null
) {
    private val tools = mutableMapOf<String, AgentTool>()

    fun register(tool: AgentTool) {
        tools[tool.name] = tool
    }

    suspend fun get(name: String): AgentTool? = all().firstOrNull { it.name == name }

    suspend fun all(): List<AgentTool> {
        val localTools = tools.values.toMutableList()
        val registry = mcpRegistry
        val dynamicTools = if (registry == null) {
            emptyList()
        } else {
            registry.listEnabledTools().map { descriptor ->
                McpToolAdapter(descriptor, registry)
            }
        }
        return (localTools + dynamicTools).sortedBy { it.name }
    }

    suspend fun visibleTools(
        config: AgentConfig,
        runContext: AgentRunContext = defaultRunContext(config)
    ): List<AgentTool> {
        return accessPolicy
            .filterVisibleTools(all(), config, runContext)
            .filter { it.isAvailable(config, runContext) }
    }

    suspend fun blockedTools(
        config: AgentConfig,
        runContext: AgentRunContext = defaultRunContext(config)
    ): List<AgentTool> {
        val allTools = all()
        val visibleNames = accessPolicy
            .filterVisibleTools(allTools, config, runContext)
            .filter { it.isAvailable(config, runContext) }
            .map { it.name }
            .toSet()
        return allTools.filterNot { it.name in visibleNames }
    }

    fun accessPolicySummary(config: AgentConfig): String = accessPolicy.describe(config)

    suspend fun getDefinitions(
        config: AgentConfig,
        runContext: AgentRunContext = defaultRunContext(config)
    ): List<LlmToolDefinitionDto> {
        return visibleTools(config, runContext).map { tool ->
            LlmToolDefinitionDto(
                type = "function",
                function = LlmToolFunctionDto(
                    name = tool.name,
                    description = tool.description,
                    parameters = tool.parametersSchema
                )
            )
        }
    }

    suspend fun execute(
        name: String,
        arguments: JsonObject,
        config: AgentConfig,
        runContext: AgentRunContext = defaultRunContext(config)
    ): String {
        val tool = get(name) ?: return "Tool '$name' is not registered."
        val accessDecision = accessPolicy.assertExecutable(tool, config, runContext)
        if (!accessDecision.allowed) {
            return accessDecision.denialMessage ?: "Tool '$name' is blocked by the current tool access policy."
        }
        if (!tool.isAvailable(config, runContext)) {
            return "Tool '$name' is unavailable in the current run context."
        }
        val validation = validator.validate(arguments, tool.parametersSchema)
        if (!validation.isValid) {
            return validation.errorMessage ?: "Invalid arguments for tool '$name'."
        }
        return tool.execute(arguments, config, runContext)
    }

    private fun defaultRunContext(config: AgentConfig): AgentRunContext {
        return AgentRunContext.root(sessionId = "tool-registry", maxSubagentDepth = config.maxSubagentDepth)
    }
}
