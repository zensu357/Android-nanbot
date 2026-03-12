package com.example.nanobot.feature.tools

import com.example.nanobot.core.ai.PromptDiagnosticsSnapshot
import com.example.nanobot.core.web.WebDiagnosticsSnapshot

data class ToolDebugItem(
    val name: String,
    val description: String,
    val schema: String,
    val sampleArguments: String,
    val lastResult: String? = null
)

data class ToolDebugUiState(
    val tools: List<ToolDebugItem> = emptyList(),
    val policySummary: String = "",
    val restrictToWorkspace: Boolean = false,
    val promptDiagnostics: PromptDiagnosticsSnapshot? = null,
    val webDiagnostics: WebDiagnosticsSnapshot? = null,
    val isRunning: Boolean = false,
    val errorMessage: String? = null
)
