package com.example.nanobot.feature.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nanobot.ui.components.GlassCard
import com.example.nanobot.ui.components.GlowButton
import com.example.nanobot.ui.components.NanobotTopBar
import com.example.nanobot.ui.components.nanobotTextFieldColors
import com.example.nanobot.ui.theme.CodeTextStyle
import com.example.nanobot.ui.theme.NanobotShapes
import com.example.nanobot.ui.theme.NanobotTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolDebugScreen(
    state: ToolDebugUiState,
    onArgumentsChange: (String, String) -> Unit,
    onRunTool: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val ext = NanobotTheme.extendedColors

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            NanobotTopBar(
                title = "Tool Debug",
                subtitle = "Inspect and execute visible tools",
                badgeText = if (state.isRunning) "Busy" else null,
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationContentDescription = "Back",
                onNavigationClick = onBackClick
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
                GlassCard(modifier = Modifier.fillMaxWidth(), innerPadding = 18.dp, highlighted = state.isRunning) {
                    Text(
                        text = "Inspect registered tools, including cached dynamic MCP tools, and run them manually with JSON arguments.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ext.textPrimary
                    )
                    Text(
                        text = "Tool policy: ${state.policySummary}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.textSecondary
                    )
                }
            }

            state.promptDiagnostics?.let { diagnostics ->
                item {
                    DiagnosticCard(
                        title = "Latest Prompt Diagnostics",
                        lines = listOf(
                            "System prompt: ${diagnostics.systemPromptChars} chars",
                            "Sections: ${diagnostics.systemPromptSections.joinToString()}",
                            "Skills catalog/expanded: ${diagnostics.catalogSkillIds.joinToString().ifBlank { "(none)" }} / ${diagnostics.expandedSkillIds.joinToString().ifBlank { "(none)" }}",
                            "Memory summary/scratch/session/long-term: ${diagnostics.memorySummaryIncluded} / ${diagnostics.memoryScratchEntryCount} / ${diagnostics.memorySessionFactCount} / ${diagnostics.memoryLongTermFactCount}",
                            "Runtime diagnostics: ${diagnostics.runtimeDiagnosticsEnabled} (${diagnostics.runtimeContextChars} chars)",
                            "History kept/original/truncated: ${diagnostics.historyKeptCount}/${diagnostics.historyOriginalCount}/${diagnostics.historyTruncatedMessageCount}"
                        )
                    )
                }
            }

            state.webDiagnostics?.let { diagnostics ->
                item {
                    DiagnosticCard(
                        title = "Latest Web Diagnostics",
                        lines = buildList {
                            add("Request kind: ${diagnostics.requestKind}")
                            add("Target: ${diagnostics.target}")
                            diagnostics.endpoint?.let { add("Endpoint: $it") }
                            add("Proxy configured: ${diagnostics.proxyConfigured}${diagnostics.proxyValue?.let { value -> " ($value)" } ?: ""}")
                            add("DNS resolution skipped due to proxy: ${diagnostics.dnsResolutionSkipped}")
                            if (diagnostics.allowlistedHosts.isNotEmpty()) {
                                add("Allowlisted endpoint hosts: ${diagnostics.allowlistedHosts.joinToString()}")
                            }
                        }
                    )
                }
            }

            if (state.restrictToWorkspace) {
                item {
                    Text(
                        text = "Workspace-restricted mode is enabled, so only local read-only tools, local orchestration tools, and workspace sandbox read/write tools are shown.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.neon
                    )
                }
            }

            state.errorMessage?.let { errorText ->
                item {
                    Text(
                        text = errorText,
                        color = ext.errorRed,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            items(state.tools, key = { it.name }) { tool ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = tool.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ext.neon
                    )
                    Text(
                        text = tool.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ext.textSecondary
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ext.codeBackground, NanobotShapes.CardSmall)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = tool.schema,
                            style = CodeTextStyle,
                            color = ext.textSecondary
                        )
                    }
                    OutlinedTextField(
                        value = tool.sampleArguments,
                        onValueChange = { onArgumentsChange(tool.name, it) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        textStyle = CodeTextStyle,
                        label = { Text("Arguments JSON") },
                        colors = nanobotTextFieldColors()
                    )
                    GlowButton(
                        text = if (state.isRunning) "Running..." else "Run Tool",
                        onClick = { onRunTool(tool.name) },
                        enabled = !state.isRunning,
                        modifier = Modifier.fillMaxWidth()
                    )
                    tool.lastResult?.let { result ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ext.codeBackground, NanobotShapes.CardSmall)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = result,
                                style = CodeTextStyle,
                                color = ext.successGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticCard(
    title: String,
    lines: List<String>
) {
    val ext = NanobotTheme.extendedColors

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = ext.textPrimary
        )
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = ext.textSecondary
            )
        }
    }
}
