package com.example.nanobot.feature.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nanobot.core.mcp.McpAuthType
import com.example.nanobot.feature.settings.SettingsUiState
import com.example.nanobot.ui.components.GlassCard
import com.example.nanobot.ui.components.GlowButton
import com.example.nanobot.ui.components.SettingsGroupCard
import com.example.nanobot.ui.components.nanobotTextFieldColors
import com.example.nanobot.ui.theme.NanobotShapes
import com.example.nanobot.ui.theme.NanobotTheme

@Composable
fun McpSection(
    state: SettingsUiState,
    onDraftMcpLabelChange: (String) -> Unit,
    onDraftMcpEndpointChange: (String) -> Unit,
    onDraftMcpAuthTypeChange: (McpAuthType) -> Unit,
    onDraftMcpAuthTokenChange: (String) -> Unit,
    onDraftMcpAuthHeaderNameChange: (String) -> Unit,
    onDraftMcpAuthHeaderValueChange: (String) -> Unit,
    onDraftMcpConnectTimeoutChange: (String) -> Unit,
    onDraftMcpReadTimeoutChange: (String) -> Unit,
    onDraftMcpWriteTimeoutChange: (String) -> Unit,
    onDraftMcpCallTimeoutChange: (String) -> Unit,
    onDraftMcpMaxRetriesChange: (String) -> Unit,
    onDraftMcpBackoffBaseMsChange: (String) -> Unit,
    onMcpServerToggle: (String, Boolean) -> Unit,
    onAddMcpServer: () -> Unit,
    onRemoveMcpServer: (String) -> Unit,
    onRefreshMcpTools: () -> Unit
) {
    val ext = NanobotTheme.extendedColors

    SettingsGroupCard(title = "MCP Servers") {
        OutlinedTextField(
            value = state.draftMcpLabel,
            onValueChange = onDraftMcpLabelChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("MCP Server Label") },
            colors = nanobotTextFieldColors()
        )
        OutlinedTextField(
            value = state.draftMcpEndpoint,
            onValueChange = onDraftMcpEndpointChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("MCP Server Endpoint") },
            supportingText = { Text("Remote HTTP/HTTPS endpoint for dynamic MCP tool discovery via JSON-RPC") },
            colors = nanobotTextFieldColors()
        )
        McpAuthTypeField(
            value = state.draftMcpAuthType,
            onValueChange = onDraftMcpAuthTypeChange,
            modifier = Modifier.fillMaxWidth()
        )
        if (state.draftMcpAuthType == McpAuthType.BEARER) {
            OutlinedTextField(
                value = state.draftMcpAuthToken,
                onValueChange = onDraftMcpAuthTokenChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Bearer Token") },
                colors = nanobotTextFieldColors()
            )
        }
        if (state.draftMcpAuthType == McpAuthType.HEADER) {
            OutlinedTextField(
                value = state.draftMcpAuthHeaderName,
                onValueChange = onDraftMcpAuthHeaderNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Auth Header Name") },
                colors = nanobotTextFieldColors()
            )
            OutlinedTextField(
                value = state.draftMcpAuthHeaderValue,
                onValueChange = onDraftMcpAuthHeaderValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Auth Header Value") },
                colors = nanobotTextFieldColors()
            )
        }
        OutlinedTextField(
            value = state.draftMcpConnectTimeoutSeconds,
            onValueChange = onDraftMcpConnectTimeoutChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Connect Timeout Seconds") },
            colors = nanobotTextFieldColors()
        )
        OutlinedTextField(
            value = state.draftMcpReadTimeoutSeconds,
            onValueChange = onDraftMcpReadTimeoutChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Read Timeout Seconds") },
            colors = nanobotTextFieldColors()
        )
        OutlinedTextField(
            value = state.draftMcpWriteTimeoutSeconds,
            onValueChange = onDraftMcpWriteTimeoutChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Write Timeout Seconds") },
            colors = nanobotTextFieldColors()
        )
        OutlinedTextField(
            value = state.draftMcpCallTimeoutSeconds,
            onValueChange = onDraftMcpCallTimeoutChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Call Timeout Seconds") },
            colors = nanobotTextFieldColors()
        )
        OutlinedTextField(
            value = state.draftMcpMaxRetries,
            onValueChange = onDraftMcpMaxRetriesChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Max Retries") },
            colors = nanobotTextFieldColors()
        )
        OutlinedTextField(
            value = state.draftMcpBackoffBaseMs,
            onValueChange = onDraftMcpBackoffBaseMsChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Backoff Base Millis") },
            colors = nanobotTextFieldColors()
        )
        GlowButton(
            text = "Add MCP Server",
            onClick = onAddMcpServer,
            modifier = Modifier.fillMaxWidth()
        )
        SectionOutlinedButton(
            text = "Refresh MCP Tools",
            onClick = onRefreshMcpTools,
            modifier = Modifier.fillMaxWidth()
        )
        state.mcpStatus?.takeIf { it.isNotBlank() }?.let { status ->
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = ext.textSecondary
            )
        }
        state.mcpServers.forEach { server ->
            GlassCard(modifier = Modifier.fillMaxWidth(), shape = NanobotShapes.CardSmall, innerPadding = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingToggleRow(
                        label = server.label,
                        checked = server.enabled,
                        onCheckedChange = { onMcpServerToggle(server.id, it) }
                    )
                    Text(
                        text = server.endpoint,
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.textSecondary
                    )
                    Text(
                        text = "Discovered tools: ${server.discoveredToolCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.textSecondary
                    )
                    Text(
                        text = "Health: ${server.healthStatus.name.lowercase()} (${server.consecutiveFailures} failure(s))",
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.textSecondary
                    )
                    server.healthError?.takeIf { it.isNotBlank() }?.let { error ->
                        Text(
                            text = "Last error: $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.errorRed
                        )
                    }
                    Text(
                        text = "Timeouts ${server.connectTimeoutSeconds}/${server.readTimeoutSeconds}/${server.writeTimeoutSeconds}/${server.callTimeoutSeconds}s, retries ${server.maxRetries}, backoff ${server.backoffBaseMs}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.textSecondary
                    )
                    TextButton(onClick = { onRemoveMcpServer(server.id) }) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}
