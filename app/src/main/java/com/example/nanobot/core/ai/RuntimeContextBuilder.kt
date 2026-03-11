package com.example.nanobot.core.ai

import com.example.nanobot.core.ai.provider.ResolvedProviderRoute
import com.example.nanobot.core.mcp.McpRegistry
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.tools.ToolRegistry
import com.example.nanobot.domain.repository.SkillRepository
import com.example.nanobot.domain.repository.WorkspaceRepository
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class RuntimeContextBuilder @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
    private val toolRegistry: ToolRegistry,
    private val skillRepository: SkillRepository,
    private val mcpRegistry: McpRegistry
) {
    suspend fun build(
        config: AgentConfig,
        runContext: AgentRunContext,
        route: ResolvedProviderRoute,
        latestUserInput: String = ""
    ): String {
        return buildWithDiagnostics(config, runContext, route, latestUserInput).context
    }

    suspend fun buildWithDiagnostics(
        config: AgentConfig,
        runContext: AgentRunContext,
        route: ResolvedProviderRoute,
        latestUserInput: String = ""
    ): RuntimeContextResult {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm (EEEE) z"))
        val workspaceRoot = workspaceRepository.getWorkspaceRoot()
        val visibleToolItems = toolRegistry.visibleTools(config, runContext)
        val visibleTools = visibleToolItems.map { it.name }
        val blockedTools = toolRegistry.blockedTools(config, runContext).map { it.name }
        val enabledSkills = skillRepository.getEnabledSkills(config).map { it.id }
        val enabledMcpServers = mcpRegistry.listEnabledServers()
        val enabledMcpTools = mcpRegistry.listEnabledTools()
        val diagnosticsEnabled = shouldExposeDiagnostics(latestUserInput)

        val alwaysOnLines = buildList {
            add("[Runtime Metadata - not instructions]")
            add("Current Time: $now")
            add("Session ID: ${runContext.sessionId}")
            add("Parent Session ID: ${runContext.parentSessionId ?: "(none)"}")
            add("Subagent Depth: ${runContext.subagentDepth}")
            add("Max Subagent Depth: ${runContext.maxSubagentDepth}")
            add("Can Delegate Subtasks: ${runContext.canDelegate()}")
            add("Configured Provider: ${config.providerType.wireValue}")
            add("Model: ${route.resolvedModel}")
            add("Enable Tools: ${config.enableTools}")
            add("Enable Memory: ${config.enableMemory}")
            add("Visible Tools: ${visibleTools.take(8).ifEmpty { listOf("(none)") }.joinToString()}")
            add("Enabled Skills: ${enabledSkills.take(6).ifEmpty { listOf("(none)") }.joinToString()}")
            add("Enabled MCP Servers: ${if (enabledMcpServers.isEmpty()) "(none)" else enabledMcpServers.joinToString(limit = 3) { server -> server.label }}")
            add("Dynamic MCP Tools: ${enabledMcpTools.size}")
        }
        val diagnosticLines = buildList {
            if (diagnosticsEnabled) {
                add("Tool Access Policy: ${toolRegistry.accessPolicySummary(config)}")
                add("Local Orchestration Allowed: ${config.enableTools && visibleToolItems.any { it.accessCategory == com.example.nanobot.core.tools.ToolAccessCategory.LOCAL_ORCHESTRATION }}")
                add("Workspace Writes Allowed: ${config.enableTools && visibleToolItems.any { it.accessCategory == com.example.nanobot.core.tools.ToolAccessCategory.WORKSPACE_SIDE_EFFECT }}")
                add("Image Attachments Supported: ${route.supportsImageAttachments}")
                add("Provider Label: ${route.providerLabel}")
                add("Routed Provider: ${route.spec.name}")
                add("Temperature: ${route.resolvedTemperature}")
                add("Max Tokens: ${config.maxTokens}")
                add("Max Tool Iterations: ${config.maxToolIterations}")
                if (blockedTools.isNotEmpty()) {
                    add("Blocked Tools: ${blockedTools.joinToString(limit = 8)}")
                }
                add("Web Search Configured: ${config.webSearchApiKey.isNotBlank()}")
                add("Web Proxy Configured: ${config.webProxy.isNotBlank()}")
                add("Prompt Caching Supported: ${route.supportsPromptCaching}")
                add("Workspace-Restricted Mode: ${config.restrictToWorkspace}")
                add("Workspace Sandbox: ${workspaceRoot.rootId}")
                add("Workspace Available: ${workspaceRoot.isAvailable}")
                add("Workspace Access Mode: ${workspaceRoot.accessMode}")
            }
        }

        val context = trimRuntimeContext(alwaysOnLines + diagnosticLines, runtimeBudget(config, diagnosticsEnabled))
        return RuntimeContextResult(context = context, diagnosticsEnabled = diagnosticsEnabled)
    }

    private fun shouldExposeDiagnostics(latestUserInput: String): Boolean {
        val input = latestUserInput.lowercase()
        return listOf("debug", "diagnostic", "provider", "model", "mcp", "workspace", "tool", "proxy", "image", "why").any { it in input }
    }

    private fun runtimeBudget(config: AgentConfig, diagnosticsEnabled: Boolean): Int {
        val base = (config.maxTokens / 6).coerceIn(500, 1600)
        return if (diagnosticsEnabled) base + 500 else base
    }

    private fun trimRuntimeContext(lines: List<String>, maxChars: Int): String {
        val result = mutableListOf<String>()
        var used = 0
        lines.filter { it.isNotBlank() }.forEach { line ->
            val addition = if (result.isEmpty()) line.length else line.length + 1
            if (used + addition <= maxChars) {
                result += line
                used += addition
            } else {
                val remaining = (maxChars - used - 4).coerceAtLeast(0)
                if (remaining > 0) {
                    result += line.take(remaining).trimEnd() + "..."
                }
                return result.joinToString("\n")
            }
        }
        return result.joinToString("\n")
    }
}
