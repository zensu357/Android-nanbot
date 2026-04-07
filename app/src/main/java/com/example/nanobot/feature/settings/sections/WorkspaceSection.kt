package com.example.nanobot.feature.settings.sections

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.nanobot.feature.settings.SettingsUiState
import com.example.nanobot.ui.components.SettingsGroupCard
import com.example.nanobot.ui.components.nanobotTextFieldColors
import com.example.nanobot.ui.theme.NanobotTheme

@Composable
fun WorkspaceSection(
    state: SettingsUiState,
    onWebSearchApiKeyChange: (String) -> Unit,
    onWebProxyChange: (String) -> Unit,
    onRestrictToWorkspaceChange: (Boolean) -> Unit,
    onSystemPromptChange: (String) -> Unit
) {
    val ext = NanobotTheme.extendedColors

    SettingsGroupCard(title = "Workspace & Environment") {
        SettingToggleRow(
            label = "Restrict To Workspace",
            checked = state.restrictToWorkspace,
            onCheckedChange = onRestrictToWorkspaceChange
        )
        Text(
            text = "Workspace-restricted mode keeps local read-only tools, local orchestration tools, and workspace sandbox read/write tools available while blocking external web access, dynamic MCP tools, and non-workspace side effects.",
            style = MaterialTheme.typography.bodySmall,
            color = ext.textSecondary
        )
        OutlinedTextField(
            value = state.webSearchApiKey,
            onValueChange = onWebSearchApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Web Search API Key") },
            colors = nanobotTextFieldColors()
        )
        OutlinedTextField(
            value = state.webProxy,
            onValueChange = onWebProxyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Web Proxy") },
            supportingText = { Text("Optional. Supports 127.0.0.1:7890, http://127.0.0.1:7890, or socks5://127.0.0.1:7890") },
            colors = nanobotTextFieldColors()
        )
        OutlinedTextField(
            value = state.systemPrompt,
            onValueChange = onSystemPromptChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 5,
            label = { Text("Custom User Instructions") },
            colors = nanobotTextFieldColors()
        )
    }
}
