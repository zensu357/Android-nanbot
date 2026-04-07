package com.example.nanobot.feature.settings.sections

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.nanobot.feature.settings.SettingsUiState
import com.example.nanobot.ui.components.SettingsGroupCard
import com.example.nanobot.ui.components.nanobotTextFieldColors

@Composable
fun ProviderSection(
    state: SettingsUiState,
    onProviderChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onMaxTokensChange: (String) -> Unit,
    onMaxToolIterationsChange: (String) -> Unit,
    onMemoryWindowChange: (String) -> Unit,
    onReasoningEffortChange: (String) -> Unit,
    onPresetChange: (String) -> Unit
) {
    SettingsGroupCard(title = "Provider Configuration") {
        OutlinedTextField(
            value = state.providerType,
            onValueChange = onProviderChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Provider") },
            supportingText = { Text("Use openai_compatible, openrouter, or azure_openai") },
            colors = nanobotTextFieldColors()
        )
        OutlinedTextField(
            value = state.apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API Key") },
            colors = nanobotTextFieldColors()
        )
        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = onBaseUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Base URL") },
            colors = nanobotTextFieldColors()
        )
        OutlinedTextField(
            value = state.model,
            onValueChange = onModelChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Model") },
            colors = nanobotTextFieldColors()
        )
        OutlinedTextField(
            value = state.maxTokens,
            onValueChange = onMaxTokensChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Max Tokens") },
            colors = nanobotTextFieldColors()
        )
        OutlinedTextField(
            value = state.maxToolIterations,
            onValueChange = onMaxToolIterationsChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Max Tool Iterations") },
            colors = nanobotTextFieldColors()
        )
        OutlinedTextField(
            value = state.memoryWindow,
            onValueChange = onMemoryWindowChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Memory Window") },
            colors = nanobotTextFieldColors()
        )
        OutlinedTextField(
            value = state.reasoningEffort,
            onValueChange = onReasoningEffortChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Reasoning Effort") },
            colors = nanobotTextFieldColors()
        )
        OutlinedTextField(
            value = state.presetId,
            onValueChange = onPresetChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Prompt Preset") },
            supportingText = { Text("Available: ${state.availablePresets.joinToString()}") },
            colors = nanobotTextFieldColors()
        )
    }
}
