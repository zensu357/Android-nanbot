package com.example.nanobot.core.model

data class SubagentRequest(
    val parentSessionId: String,
    val task: String,
    val title: String? = null,
    val subagentDepth: Int = 0,
    val maxSubagentDepth: Int = 3,
    val maxParallelSubagents: Int = 4,
    val allowedToolNames: Set<String>? = null,
    val unlockedToolNames: Set<String> = emptySet(),
    val supportsVision: Boolean = false
)
