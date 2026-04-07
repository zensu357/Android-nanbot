package com.example.nanobot.core.model

data class SubagentResult(
    val sessionId: String? = null,
    val parentSessionId: String,
    val subagentDepth: Int,
    val summary: String,
    val artifactPaths: List<String> = emptyList(),
    val completed: Boolean = true,
    val success: Boolean = completed
) {
    companion object {
        fun depthExceeded(parentSessionId: String, subagentDepth: Int): SubagentResult {
            return SubagentResult(
                sessionId = null,
                parentSessionId = parentSessionId,
                subagentDepth = subagentDepth,
                summary = "Subagent delegation is blocked because the maximum subagent depth has been reached.",
                completed = false,
                success = false
            )
        }

        fun timeout(parentSessionId: String, subagentDepth: Int, label: String): SubagentResult {
            return SubagentResult(
                sessionId = null,
                parentSessionId = parentSessionId,
                subagentDepth = subagentDepth,
                summary = "Subagent '$label' timed out before producing a result.",
                completed = false,
                success = false
            )
        }

        fun failed(
            parentSessionId: String,
            subagentDepth: Int,
            label: String,
            reason: String?
        ): SubagentResult {
            val detail = reason?.trim().orEmpty()
            val summary = if (detail.isBlank()) {
                "Subagent '$label' failed before producing a result."
            } else {
                "Subagent '$label' failed before producing a result. Reason: $detail"
            }
            return SubagentResult(
                sessionId = null,
                parentSessionId = parentSessionId,
                subagentDepth = subagentDepth,
                summary = summary,
                completed = false,
                success = false
            )
        }
    }
}
