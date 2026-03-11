package com.example.nanobot.core.mcp

import com.example.nanobot.core.preferences.McpServerConfigStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

interface McpRegistry {
    fun observeServers(): Flow<List<McpServerDefinition>>
    fun observeCachedTools(): Flow<List<McpToolDescriptor>>
    suspend fun listEnabledServers(): List<McpServerDefinition>
    suspend fun listEnabledTools(): List<McpToolDescriptor>
    suspend fun refreshTools(): McpRefreshResult
    suspend fun saveServers(servers: List<McpServerDefinition>)
    suspend fun callTool(toolName: String, arguments: JsonObject): String
    suspend fun getDiscoverySnapshot(): McpToolDiscoverySnapshot
}

data class McpToolDiscoverySnapshot(
    val enabledServers: List<McpServerDefinition>,
    val tools: List<McpToolDescriptor>
)

@Singleton
class McpRegistryImpl @Inject constructor(
    private val store: McpServerConfigStore,
    private val client: McpClient
) : McpRegistry {
    override fun observeServers(): Flow<List<McpServerDefinition>> = store.observeServers()

    override fun observeCachedTools(): Flow<List<McpToolDescriptor>> = store.observeCachedTools()

    override suspend fun listEnabledServers(): List<McpServerDefinition> =
        store.getServersSnapshot().filter { it.enabled }

    override suspend fun listEnabledTools(): List<McpToolDescriptor> {
        val enabledServers = listEnabledServers().associateBy { it.id }
        return store.getCachedToolsSnapshot().filter { it.serverId in enabledServers }
    }

    override suspend fun getDiscoverySnapshot(): McpToolDiscoverySnapshot {
        return McpToolDiscoverySnapshot(
            enabledServers = listEnabledServers(),
            tools = listEnabledTools()
        )
    }

    override suspend fun refreshTools(): McpRefreshResult {
        val allServers = store.getServersSnapshot()
        val servers = allServers.filter { it.enabled }
        val previousTools = store.getCachedToolsSnapshot()
        val retainedTools = previousTools.filter { tool -> servers.any { it.id == tool.serverId } }.toMutableList()
        val discoveredByServer = mutableMapOf<String, List<McpToolDescriptor>>()
        val updatedServers = allServers.associateBy { it.id }.toMutableMap()
        val errors = mutableListOf<String>()
        val now = System.currentTimeMillis()

        servers.forEach { server ->
            runCatching {
                client.discoverTools(server)
            }.onSuccess { tools ->
                discoveredByServer[server.id] = tools
                updatedServers[server.id] = server.markHealthy(now)
            }.onFailure { throwable ->
                updatedServers[server.id] = server.markFailure(now, throwable.message)
                errors += "${server.label}: ${throwable.message ?: "Discovery failed."}"
            }
        }

        val mergedTools = servers.flatMap { server ->
            discoveredByServer[server.id]
                ?: retainedTools.filter { it.serverId == server.id }
        }
        store.saveServers(allServers.map { server -> updatedServers[server.id] ?: server })
        store.saveCachedTools(mergedTools)
        val healthStatuses = servers.map { server -> (updatedServers[server.id] ?: server).health.status }
        return McpRefreshResult(
            enabledServerCount = servers.size,
            discoveredToolCount = discoveredByServer.values.sumOf { it.size },
            retainedToolCount = mergedTools.size,
            errors = errors,
            healthyServerCount = healthStatuses.count { it == McpHealthStatus.HEALTHY },
            degradedServerCount = healthStatuses.count { it == McpHealthStatus.DEGRADED },
            unhealthyServerCount = healthStatuses.count { it == McpHealthStatus.UNHEALTHY }
        )
    }

    override suspend fun saveServers(servers: List<McpServerDefinition>) {
        store.saveServers(servers)
    }

    override suspend fun callTool(toolName: String, arguments: JsonObject): String {
        val descriptor = listEnabledTools().firstOrNull { namespacedToolName(it) == toolName }
            ?: return "MCP tool '$toolName' was not found."
        val server = listEnabledServers().firstOrNull { it.id == descriptor.serverId }
            ?: return "MCP server '${descriptor.serverId}' is not enabled."
        val now = System.currentTimeMillis()
        return runCatching {
            client.callTool(server, descriptor.remoteName, arguments)
        }.onSuccess {
            updateServerHealth(server.id) { it.markHealthy(now) }
        }.onFailure { throwable ->
            updateServerHealth(server.id) { it.markFailure(now, throwable.message) }
        }.getOrThrow()
    }

    private suspend fun updateServerHealth(
        serverId: String,
        transform: (McpServerDefinition) -> McpServerDefinition
    ) {
        val updated = store.getServersSnapshot().map { server ->
            if (server.id == serverId) transform(server) else server
        }
        store.saveServers(updated)
    }

    companion object {
        fun namespacedToolName(tool: McpToolDescriptor): String {
            val safeServerId = tool.serverId.lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_')
            return "mcp.${safeServerId.ifBlank { "server" }}.${tool.name}"
        }
    }
}
