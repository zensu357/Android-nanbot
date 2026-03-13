package com.example.nanobot.feature.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nanobot.core.mcp.McpAuthType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onProviderChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onMaxTokensChange: (String) -> Unit,
    onMaxToolIterationsChange: (String) -> Unit,
    onMemoryWindowChange: (String) -> Unit,
    onReasoningEffortChange: (String) -> Unit,
    onEnableToolsChange: (Boolean) -> Unit,
    onEnableMemoryChange: (Boolean) -> Unit,
    onEnableBackgroundWorkChange: (Boolean) -> Unit,
    onHeartbeatEnabledChange: (Boolean) -> Unit,
    onHeartbeatInstructionsChange: (String) -> Unit,
    onWebSearchApiKeyChange: (String) -> Unit,
    onWebProxyChange: (String) -> Unit,
    onRestrictToWorkspaceChange: (Boolean) -> Unit,
    onPresetChange: (String) -> Unit,
    onSkillToggle: (String, Boolean) -> Unit,
    onSkillDirectorySelected: (android.net.Uri) -> Unit,
    onSkillZipSelected: (android.net.Uri) -> Unit,
    onTrustProjectSkillsChange: (Boolean) -> Unit,
    onRescanImportedSkills: () -> Unit,
    onRemoveSkillRoot: (String) -> Unit,
    onRemoveImportedSkill: (String) -> Unit,
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
    onRefreshMcpTools: () -> Unit,
    onSystemPromptChange: (String) -> Unit,
    onOpenMemory: () -> Unit,
    onOpenTools: () -> Unit,
    onResetClick: () -> Unit,
    onSaveClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val skillsDirectoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            onSkillDirectorySelected(uri)
        }
    }
    val skillsZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onSkillZipSelected(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Settings")
                        Text(
                            text = "Scroll to the bottom to save",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            SettingsGroup(title = "About") {
                OutlinedButton(
                    onClick = { uriHandler.openUri("https://github.com/zensu357/Android-nanobot") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("GitHub Repository")
                }
                OutlinedButton(
                    onClick = { uriHandler.openUri("https://github.com/zensu357") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Author: zensu357")
                }
                OutlinedButton(
                    onClick = { uriHandler.openUri("https://t.me/***") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Telegram Group")
                }
            }

            // LLM Provider Group
            SettingsGroup(title = "Provider Configuration") {
                OutlinedTextField(
                    value = state.providerType,
                    onValueChange = onProviderChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Provider") },
                    supportingText = { Text("Use openai_compatible, openrouter, or azure_openai") }
                )
                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = onApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key") }
                )
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = onBaseUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL") }
                )
                OutlinedTextField(
                    value = state.model,
                    onValueChange = onModelChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Model") }
                )
                OutlinedTextField(
                    value = state.maxTokens,
                    onValueChange = onMaxTokensChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Max Tokens") }
                )
                OutlinedTextField(
                    value = state.maxToolIterations,
                    onValueChange = onMaxToolIterationsChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Max Tool Iterations") }
                )
                OutlinedTextField(
                    value = state.memoryWindow,
                    onValueChange = onMemoryWindowChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Memory Window") }
                )
                OutlinedTextField(
                    value = state.reasoningEffort,
                    onValueChange = onReasoningEffortChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Reasoning Effort") }
                )
                OutlinedTextField(
                    value = state.presetId,
                    onValueChange = onPresetChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Prompt Preset") },
                    supportingText = { Text("Available: ${state.availablePresets.joinToString()}") }
                )
            }

            // Skills Group
            if (state.skillOptions.isNotEmpty()) {
                SettingsGroup(title = "Skills") {
                    OutlinedButton(
                        onClick = { skillsDirectoryLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Import Skills From Directory")
                    }
                    OutlinedButton(
                        onClick = { skillsZipLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Import Skills From Zip")
                    }
                    SettingToggleRow(
                        label = "Trust Project Skills",
                        checked = state.trustProjectSkills,
                        onCheckedChange = onTrustProjectSkillsChange,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Project skills discovered from workspace skills/ and .agents/skills/ stay hidden until you trust this workspace.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onRescanImportedSkills,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Rescan Imported Skills")
                    }
                    state.skillRoots.forEach { root ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = root.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = root.uri,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = { onRemoveSkillRoot(root.uri) }) {
                                    Text("Remove Imported Root")
                                }
                            }
                        }
                    }
                    state.skillsDirectoryUri?.takeIf { it.isNotBlank() }?.let { uri ->
                        Text(
                            text = "Imported directory: $uri",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    state.skillImportStatus?.takeIf { it.isNotBlank() }?.let { status ->
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.skillDiagnostics.isNotEmpty()) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Skill Diagnostics",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                state.skillDiagnostics.forEach { section ->
                                    Text(
                                        text = section.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    section.items.forEach { issue ->
                                        Text(
                                            text = "${issue.scopeLabel} | ${issue.levelLabel}: ${issue.message}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    state.skillOptions.forEach { skill ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SettingToggleRow(
                                    label = skill.title,
                                    checked = skill.checked,
                                    onCheckedChange = { onSkillToggle(skill.id, it) },
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Text(
                                    text = skill.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (skill.isImported) {
                                    Text(
                                        text = "Imported Skill",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                skill.scopeLabel?.takeIf { it.isNotBlank() }?.let { scope ->
                                    Text(
                                        text = "Scope: $scope${if (skill.trusted) "" else " (untrusted)"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (skill.tags.isNotEmpty()) {
                                    Text(
                                        text = "Tags: ${skill.tags.joinToString()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                skill.originLabel?.takeIf { it.isNotBlank() }?.let { origin ->
                                    Text(
                                        text = "Source: $origin",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (skill.isImported) {
                                    TextButton(onClick = { onRemoveImportedSkill(skill.id) }) {
                                        Text("Remove Imported Skill")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // MCP Servers Group
            SettingsGroup(title = "MCP Servers") {
                OutlinedTextField(
                    value = state.draftMcpLabel,
                    onValueChange = onDraftMcpLabelChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("MCP Server Label") }
                )
                OutlinedTextField(
                    value = state.draftMcpEndpoint,
                    onValueChange = onDraftMcpEndpointChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("MCP Server Endpoint") },
                    supportingText = { Text("Remote HTTP/HTTPS endpoint for dynamic MCP tool discovery via JSON-RPC") }
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
                        label = { Text("Bearer Token") }
                    )
                }
                if (state.draftMcpAuthType == McpAuthType.HEADER) {
                    OutlinedTextField(
                        value = state.draftMcpAuthHeaderName,
                        onValueChange = onDraftMcpAuthHeaderNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Auth Header Name") }
                    )
                    OutlinedTextField(
                        value = state.draftMcpAuthHeaderValue,
                        onValueChange = onDraftMcpAuthHeaderValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Auth Header Value") }
                    )
                }
                OutlinedTextField(
                    value = state.draftMcpConnectTimeoutSeconds,
                    onValueChange = onDraftMcpConnectTimeoutChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Connect Timeout Seconds") }
                )
                OutlinedTextField(
                    value = state.draftMcpReadTimeoutSeconds,
                    onValueChange = onDraftMcpReadTimeoutChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Read Timeout Seconds") }
                )
                OutlinedTextField(
                    value = state.draftMcpWriteTimeoutSeconds,
                    onValueChange = onDraftMcpWriteTimeoutChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Write Timeout Seconds") }
                )
                OutlinedTextField(
                    value = state.draftMcpCallTimeoutSeconds,
                    onValueChange = onDraftMcpCallTimeoutChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Call Timeout Seconds") }
                )
                OutlinedTextField(
                    value = state.draftMcpMaxRetries,
                    onValueChange = onDraftMcpMaxRetriesChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Max Retries") }
                )
                OutlinedTextField(
                    value = state.draftMcpBackoffBaseMs,
                    onValueChange = onDraftMcpBackoffBaseMsChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Backoff Base Millis") }
                )
                Button(
                    onClick = onAddMcpServer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add MCP Server")
                }
                OutlinedButton(
                    onClick = onRefreshMcpTools,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Refresh MCP Tools")
                }
                state.mcpStatus?.takeIf { it.isNotBlank() }?.let { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                state.mcpServers.forEach { server ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SettingToggleRow(
                                label = server.label,
                                checked = server.enabled,
                                onCheckedChange = { onMcpServerToggle(server.id, it) },
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = server.endpoint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Discovered tools: ${server.discoveredToolCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Health: ${server.healthStatus.name.lowercase()} (${server.consecutiveFailures} failure(s))",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            server.healthError?.takeIf { it.isNotBlank() }?.let { error ->
                                Text(
                                    text = "Last error: $error",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Text(
                                text = "Timeouts ${server.connectTimeoutSeconds}/${server.readTimeoutSeconds}/${server.writeTimeoutSeconds}/${server.callTimeoutSeconds}s, retries ${server.maxRetries}, backoff ${server.backoffBaseMs}ms",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { onRemoveMcpServer(server.id) }) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }

            // Features & Behavior Group
            SettingsGroup(title = "Features & Behavior") {
                SettingToggleRow(
                    label = "Enable Tools",
                    checked = state.enableTools,
                    onCheckedChange = onEnableToolsChange
                )
                SettingToggleRow(
                    label = "Enable Memory",
                    checked = state.enableMemory,
                    onCheckedChange = onEnableMemoryChange
                )
                SettingToggleRow(
                    label = "Enable Background Work",
                    checked = state.enableBackgroundWork,
                    onCheckedChange = onEnableBackgroundWorkChange
                )
                SettingToggleRow(
                    label = "Enable Heartbeat",
                    checked = state.heartbeatEnabled,
                    onCheckedChange = onHeartbeatEnabledChange
                )
                if (state.heartbeatEnabled) {
                    OutlinedTextField(
                        value = state.heartbeatInstructions,
                        onValueChange = onHeartbeatInstructionsChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        label = { Text("Heartbeat Instructions") },
                        supportingText = {
                            Text("Multi-line local instruction source used by the heartbeat decider.")
                        }
                    )
                }
            }

            // Workspace & Environment Group
            SettingsGroup(title = "Workspace & Environment") {
                SettingToggleRow(
                    label = "Restrict To Workspace",
                    checked = state.restrictToWorkspace,
                    onCheckedChange = onRestrictToWorkspaceChange
                )
                Text(
                    text = "Workspace-restricted mode keeps local read-only tools, local orchestration tools, and workspace sandbox read/write tools available while blocking external web access, dynamic MCP tools, and non-workspace side effects.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = state.webSearchApiKey,
                    onValueChange = onWebSearchApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Web Search API Key") }
                )
                OutlinedTextField(
                    value = state.webProxy,
                    onValueChange = onWebProxyChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Web Proxy") },
                    supportingText = {
                        Text("Optional. Supports 127.0.0.1:7890, http://127.0.0.1:7890, or socks5://127.0.0.1:7890")
                    }
                )
                OutlinedTextField(
                    value = state.systemPrompt,
                    onValueChange = onSystemPromptChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    label = { Text("Custom User Instructions") }
                )
            }

            // Actions
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenMemory,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Open Memory")
                    }
                    Button(
                        onClick = onOpenTools,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Open Tools")
                    }
                }

                OutlinedButton(
                    onClick = onResetClick,
                    enabled = state.isDirty && !state.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset Draft")
                }
                Button(
                    onClick = onSaveClick,
                    enabled = state.isDirty && !state.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.isSaving) "Saving..." else "Save")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun McpAuthTypeField(
    value: McpAuthType,
    onValueChange: (McpAuthType) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Auth Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            McpAuthType.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        expanded = false
                        onValueChange(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
