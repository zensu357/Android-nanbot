package com.example.nanobot.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolDebugScreen(
    state: ToolDebugUiState,
    onArgumentsChange: (String, String) -> Unit,
    onRunTool: (String) -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tool Debug") },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
                        Text("Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Inspect registered tools, including cached dynamic MCP tools, and run them manually with JSON arguments.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Text(
                    text = "Tool policy: ${state.policySummary}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            state.promptDiagnostics?.let { diagnostics ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Latest Prompt Diagnostics",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "System prompt: ${diagnostics.systemPromptChars} chars",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Sections: ${diagnostics.systemPromptSections.joinToString()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Skills catalog/expanded: ${diagnostics.catalogSkillIds.joinToString().ifBlank { "(none)" }} / ${diagnostics.expandedSkillIds.joinToString().ifBlank { "(none)" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Memory summary/session/long-term: ${diagnostics.memorySummaryIncluded} / ${diagnostics.memorySessionFactCount} / ${diagnostics.memoryLongTermFactCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Runtime diagnostics: ${diagnostics.runtimeDiagnosticsEnabled} (${diagnostics.runtimeContextChars} chars)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "History kept/original/truncated: ${diagnostics.historyKeptCount}/${diagnostics.historyOriginalCount}/${diagnostics.historyTruncatedMessageCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (state.restrictToWorkspace) {
                item {
                    Text(
                        text = "Workspace-restricted mode is enabled, so only local read-only tools, local orchestration tools, and workspace sandbox read/write tools are shown.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            state.errorMessage?.let { errorText ->
                item {
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            items(state.tools, key = { it.name }) { tool ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = tool.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = tool.description,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = tool.schema,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = tool.sampleArguments,
                            onValueChange = { onArgumentsChange(tool.name, it) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            label = { Text("Arguments JSON") }
                        )
                        Button(
                            onClick = { onRunTool(tool.name) },
                            enabled = !state.isRunning,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (state.isRunning) "Running..." else "Run Tool")
                        }
                        tool.lastResult?.let { result ->
                            Text(
                                text = result,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
