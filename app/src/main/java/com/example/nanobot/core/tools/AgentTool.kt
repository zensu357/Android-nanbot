package com.example.nanobot.core.tools

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import kotlinx.serialization.json.JsonObject

enum class ToolExposure {
    DEFAULT_VISIBLE,
    HIDDEN_UNLOCKABLE
}

interface AgentTool {
    val name: String
    val description: String
    val accessCategory: ToolAccessCategory
    val exposure: ToolExposure get() = ToolExposure.DEFAULT_VISIBLE
    val availabilityHint: String get() = "Always available"
    val parametersSchema: JsonObject
    fun isAvailable(config: AgentConfig, runContext: AgentRunContext): Boolean = true
    suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String

    suspend fun executeStructured(
        arguments: JsonObject,
        config: AgentConfig,
        runContext: AgentRunContext
    ): ToolResult = ToolResult.Text(execute(arguments, config, runContext))
}
