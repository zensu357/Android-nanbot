package com.example.nanobot.core.model

data class AgentRunContext(
    val sessionId: String,
    val parentSessionId: String? = null,
    val subagentDepth: Int = 0,
    val maxSubagentDepth: Int = 3,
    val maxParallelSubagents: Int = 4,
    val activePlanId: String? = null,
    val allowedToolNames: Set<String>? = null,
    val unlockedToolNames: Set<String> = emptySet(),
    val supportsVision: Boolean = false
) {
    fun canDelegate(): Boolean = subagentDepth < maxSubagentDepth

    fun canParallel(count: Int): Boolean {
        return count > 0 && canDelegate() && count <= maxParallelSubagents
    }

    fun child(childSessionId: String): AgentRunContext = AgentRunContext(
        sessionId = childSessionId,
        parentSessionId = sessionId,
        subagentDepth = subagentDepth + 1,
        maxSubagentDepth = maxSubagentDepth,
        maxParallelSubagents = maxParallelSubagents,
        activePlanId = activePlanId,
        allowedToolNames = allowedToolNames,
        unlockedToolNames = unlockedToolNames,
        supportsVision = supportsVision
    )

    companion object {
        fun root(
            sessionId: String,
            maxSubagentDepth: Int = 3,
            maxParallelSubagents: Int = 4,
            activePlanId: String? = null,
            allowedToolNames: Set<String>? = null,
            unlockedToolNames: Set<String> = emptySet(),
            supportsVision: Boolean = false
        ): AgentRunContext = AgentRunContext(
            sessionId = sessionId,
            parentSessionId = null,
            subagentDepth = 0,
            maxSubagentDepth = maxSubagentDepth,
            maxParallelSubagents = maxParallelSubagents,
            activePlanId = activePlanId,
            allowedToolNames = allowedToolNames,
            unlockedToolNames = unlockedToolNames,
            supportsVision = supportsVision
        )
    }
}
