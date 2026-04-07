package com.example.nanobot.feature.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nanobot.feature.settings.SettingsUiState
import com.example.nanobot.ui.components.GlassCard
import com.example.nanobot.ui.components.SettingsGroupCard
import com.example.nanobot.ui.theme.NanobotShapes
import com.example.nanobot.ui.theme.NanobotTheme

@Composable
fun SkillsSection(
    state: SettingsUiState,
    onImportDirectory: () -> Unit,
    onImportZip: () -> Unit,
    onTrustProjectSkillsChange: (Boolean) -> Unit,
    onRescanImportedSkills: () -> Unit,
    onRemoveSkillRoot: (String) -> Unit,
    onSkillToggle: (String, Boolean) -> Unit,
    onRemoveImportedSkill: (String) -> Unit
) {
    val ext = NanobotTheme.extendedColors

    SettingsGroupCard(title = "Skills") {
        SectionOutlinedButton(
            text = "Import Skills From Directory",
            onClick = onImportDirectory,
            modifier = Modifier.fillMaxWidth()
        )
        SectionOutlinedButton(
            text = "Import Skills From Zip",
            onClick = onImportZip,
            modifier = Modifier.fillMaxWidth()
        )
        SettingToggleRow(
            label = "Trust Project Skills",
            checked = state.trustProjectSkills,
            onCheckedChange = onTrustProjectSkillsChange,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Project skills discovered from workspace skills/ and .agents/skills/ stay hidden until you trust this workspace.",
            style = MaterialTheme.typography.bodySmall,
            color = ext.textSecondary
        )
        SectionOutlinedButton(
            text = "Rescan Imported Skills",
            onClick = onRescanImportedSkills,
            modifier = Modifier.fillMaxWidth()
        )

        state.skillRoots.forEach { root ->
            GlassCard(modifier = Modifier.fillMaxWidth(), shape = NanobotShapes.CardSmall, innerPadding = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = root.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ext.textPrimary
                    )
                    Text(
                        text = root.uri,
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.textSecondary
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
                color = ext.textSecondary
            )
        }
        state.skillImportStatus?.takeIf { it.isNotBlank() }?.let { status ->
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = ext.textSecondary
            )
        }

        if (state.skillDiagnostics.isNotEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth(), shape = NanobotShapes.CardSmall, innerPadding = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Skill Diagnostics",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ext.textPrimary
                    )
                    state.skillDiagnostics.forEach { section ->
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = ext.neon,
                            fontWeight = FontWeight.SemiBold
                        )
                        section.items.forEach { issue ->
                            Text(
                                text = "${issue.scopeLabel} | ${issue.levelLabel}: ${issue.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.textSecondary
                            )
                        }
                    }
                }
            }
        }

        state.skillOptions.forEach { skill ->
            GlassCard(modifier = Modifier.fillMaxWidth(), shape = NanobotShapes.CardSmall, innerPadding = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingToggleRow(
                        label = skill.title,
                        checked = skill.checked,
                        onCheckedChange = { onSkillToggle(skill.id, it) },
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = skill.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.textSecondary
                    )
                    if (skill.isImported) {
                        Text(
                            text = "Imported Skill",
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.neon
                        )
                    }
                    skill.scopeLabel?.takeIf { it.isNotBlank() }?.let { scope ->
                        Text(
                            text = "Scope: $scope${if (skill.trusted) "" else " (untrusted)"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.textSecondary
                        )
                    }
                    if (skill.tags.isNotEmpty()) {
                        Text(
                            text = "Tags: ${skill.tags.joinToString()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.textSecondary
                        )
                    }
                    skill.originLabel?.takeIf { it.isNotBlank() }?.let { origin ->
                        Text(
                            text = "Source: $origin",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.textSecondary
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
