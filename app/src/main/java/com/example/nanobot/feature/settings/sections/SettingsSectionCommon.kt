package com.example.nanobot.feature.settings.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nanobot.core.mcp.McpAuthType
import com.example.nanobot.core.model.VoiceEngineType
import com.example.nanobot.ui.components.GlassCard
import com.example.nanobot.ui.components.nanobotSwitchColors
import com.example.nanobot.ui.components.nanobotTextFieldColors
import com.example.nanobot.ui.theme.NanobotShapes
import com.example.nanobot.ui.theme.NanobotTheme

@Composable
internal fun SectionOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val ext = NanobotTheme.extendedColors

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = NanobotShapes.TextField,
        border = BorderStroke(1.dp, ext.glassBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = ext.textPrimary,
            disabledContentColor = ext.textTertiary
        )
    ) {
        Text(text)
    }
}

@Composable
internal fun AccessStatusCard(
    title: String,
    status: String,
    supportingText: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    actionEnabled: Boolean = true
) {
    val ext = NanobotTheme.extendedColors

    GlassCard(modifier = Modifier.fillMaxWidth(), shape = NanobotShapes.CardSmall, innerPadding = 12.dp) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = ext.textPrimary
        )
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = ext.neon
        )
        Text(
            text = supportingText,
            style = MaterialTheme.typography.bodySmall,
            color = ext.textSecondary
        )
        SectionOutlinedButton(
            text = actionLabel,
            onClick = onActionClick,
            enabled = actionEnabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = NanobotTheme.extendedColors

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
            color = ext.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = nanobotSwitchColors()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoiceEngineField(
    value: VoiceEngineType,
    onValueChange: (VoiceEngineType) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value.wireValue,
            onValueChange = {},
            readOnly = true,
            label = { Text("Voice Engine") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = nanobotTextFieldColors(),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            VoiceEngineType.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.wireValue) },
                    onClick = {
                        expanded = false
                        onValueChange(option)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun McpAuthTypeField(
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
            colors = nanobotTextFieldColors(),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
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
