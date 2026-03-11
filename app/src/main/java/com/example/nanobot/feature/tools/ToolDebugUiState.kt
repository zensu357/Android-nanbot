package com.example.nanobot.feature.tools

import com.example.nanobot.core.ai.PromptDiagnosticsSnapshot

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
    val isRunning: Boolean = false,
    val errorMessage: String? = null
)
