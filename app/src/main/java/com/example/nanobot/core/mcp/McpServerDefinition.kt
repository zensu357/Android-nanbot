package com.example.nanobot.core.mcp

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
enum class McpAuthType {
    NONE,
    BEARER,
    HEADER
}

@Serializable
data class McpAuthConfig(
    val type: McpAuthType = McpAuthType.NONE,
    val token: String = "",
    val headerName: String = "X-API-Key",
    val headerValue: String = ""
)

@Serializable
enum class McpHealthStatus {
    UNKNOWN,
    HEALTHY,
    DEGRADED,
    UNHEALTHY
}

@Serializable
data class McpServerHealth(
    val status: McpHealthStatus = McpHealthStatus.UNKNOWN,
    val lastCheckedAtEpochMs: Long? = null,
    val lastSuccessAtEpochMs: Long? = null,
    val lastFailureAtEpochMs: Long? = null,
    val consecutiveFailures: Int = 0,
    val lastError: String? = null
)

@Serializable
data class McpServerDefinition(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val endpoint: String,
    val enabled: Boolean = true,
    val auth: McpAuthConfig = McpAuthConfig(),
    val connectTimeoutSeconds: Int = 30,
    val readTimeoutSeconds: Int = 60,
    val writeTimeoutSeconds: Int = 30,
    val callTimeoutSeconds: Int = 90,
    val maxRetries: Int = 2,
    val backoffBaseMs: Long = 500,
    val health: McpServerHealth = McpServerHealth()
)

private const val MCP_UNHEALTHY_FAILURE_THRESHOLD = 2

fun McpServerDefinition.markHealthy(nowEpochMs: Long): McpServerDefinition {
    return copy(health = health.markHealthy(nowEpochMs))
}

fun McpServerDefinition.markFailure(nowEpochMs: Long, message: String?): McpServerDefinition {
    return copy(health = health.markFailure(nowEpochMs, message))
}

fun McpServerHealth.markHealthy(nowEpochMs: Long): McpServerHealth {
    return copy(
        status = McpHealthStatus.HEALTHY,
        lastCheckedAtEpochMs = nowEpochMs,
        lastSuccessAtEpochMs = nowEpochMs,
        consecutiveFailures = 0,
        lastError = null
    )
}

fun McpServerHealth.markFailure(nowEpochMs: Long, message: String?): McpServerHealth {
    val failureCount = consecutiveFailures + 1
    return copy(
        status = if (failureCount >= MCP_UNHEALTHY_FAILURE_THRESHOLD) {
            McpHealthStatus.UNHEALTHY
        } else {
            McpHealthStatus.DEGRADED
        },
        lastCheckedAtEpochMs = nowEpochMs,
        lastFailureAtEpochMs = nowEpochMs,
        consecutiveFailures = failureCount,
        lastError = message?.takeIf { it.isNotBlank() }
    )
}
