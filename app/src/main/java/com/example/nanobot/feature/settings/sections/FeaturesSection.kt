package com.example.nanobot.feature.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nanobot.core.model.VoiceEngineType
import com.example.nanobot.feature.settings.SettingsUiState
import com.example.nanobot.ui.components.SettingsGroupCard
import com.example.nanobot.ui.components.nanobotTextFieldColors
import java.util.Locale

@Composable
fun FeaturesSection(
    state: SettingsUiState,
    onEnableToolsChange: (Boolean) -> Unit,
    onEnableMemoryChange: (Boolean) -> Unit,
    onEnableVisualMemoryChange: (Boolean) -> Unit,
    onEnableBackgroundWorkChange: (Boolean) -> Unit,
    onEnableTaskPlanningChange: (Boolean) -> Unit,
    onEnableBehaviorLearningChange: (Boolean) -> Unit,
    onVoiceInputEnabledChange: (Boolean) -> Unit,
    onVoiceAutoPlayChange: (Boolean) -> Unit,
    onVoiceEngineChange: (VoiceEngineType) -> Unit,
    onTtsSpeedChange: (String) -> Unit,
    onTtsLanguageChange: (String) -> Unit,
    onHeartbeatEnabledChange: (Boolean) -> Unit,
    onHeartbeatInstructionsChange: (String) -> Unit
) {
    SettingsGroupCard(title = "Features & Behavior") {
        SettingToggleRow(label = "Enable Tools", checked = state.enableTools, onCheckedChange = onEnableToolsChange)
        SettingToggleRow(label = "Enable Memory", checked = state.enableMemory, onCheckedChange = onEnableMemoryChange)
        SettingToggleRow(label = "Enable Visual Memory", checked = state.enableVisualMemory, onCheckedChange = onEnableVisualMemoryChange)
        SettingToggleRow(label = "Enable Background Work", checked = state.enableBackgroundWork, onCheckedChange = onEnableBackgroundWorkChange)
        SettingToggleRow(label = "Enable Task Planning", checked = state.enableTaskPlanning, onCheckedChange = onEnableTaskPlanningChange)
        SettingToggleRow(label = "Enable Behavior Learning", checked = state.enableBehaviorLearning, onCheckedChange = onEnableBehaviorLearningChange)
        SettingToggleRow(label = "Enable Voice Input", checked = state.voiceInputEnabled, onCheckedChange = onVoiceInputEnabledChange)
        SettingToggleRow(label = "Auto-play Assistant Voice", checked = state.voiceAutoPlay, onCheckedChange = onVoiceAutoPlayChange)
        VoiceEngineField(
            value = state.voiceEngine,
            onValueChange = onVoiceEngineChange,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.ttsLanguage,
            onValueChange = onTtsLanguageChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("TTS Language") },
            supportingText = { Text("BCP-47 language tag, e.g. zh-CN or en-US") },
            colors = nanobotTextFieldColors()
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "TTS Speed: ${state.ttsSpeed}",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = state.ttsSpeed.toFloatOrNull()?.coerceIn(0.5f, 2.0f) ?: 1.0f,
                onValueChange = { onTtsSpeedChange(String.format(Locale.US, "%.2f", it)) },
                valueRange = 0.5f..2.0f
            )
        }
        SettingToggleRow(label = "Enable Heartbeat", checked = state.heartbeatEnabled, onCheckedChange = onHeartbeatEnabledChange)
        if (state.heartbeatEnabled) {
            OutlinedTextField(
                value = state.heartbeatInstructions,
                onValueChange = onHeartbeatInstructionsChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                label = { Text("Heartbeat Instructions") },
                supportingText = { Text("Multi-line local instruction source used by the heartbeat decider.") },
                colors = nanobotTextFieldColors()
            )
        }
    }
}
