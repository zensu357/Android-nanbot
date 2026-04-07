package com.example.nanobot.core.learning

enum class FeedbackSignal {
    POSITIVE,
    NEGATIVE,
    IMPLICIT_CORRECTION,
    IMPLICIT_ACCEPTANCE
}

enum class EventType {
    TOOL_USAGE,
    TASK_COMPLETION,
    FEEDBACK,
    SKILL_ACTIVATION
}

data class ToolUsageEvent(
    val toolName: String,
    val sessionId: String,
    val arguments: String,
    val success: Boolean,
    val durationMs: Long,
    val turnIndex: Int
)

data class TaskCompletionEvent(
    val sessionId: String,
    val taskType: String,
    val toolSequence: List<String>,
    val totalTurns: Int,
    val success: Boolean
)

data class FeedbackEvent(
    val sessionId: String,
    val messageId: String,
    val signal: FeedbackSignal,
    val context: String? = null,
    val responseStyle: ResponseStyle? = null
)

enum class ResponseStyle {
    CONCISE,
    DETAILED
}

data class UserBehaviorProfile(
    val toolPreferences: List<ToolPreference>,
    val commonTaskPatterns: List<TaskPattern>,
    val preferredComplexity: ComplexityPreference,
    val feedbackTrends: FeedbackTrend,
    val activeHours: List<Int>,
    val analyzedAt: Long
)

data class ToolPreference(
    val toolName: String,
    val usageCount: Int,
    val successRate: Double,
    val avgDurationMs: Long
)

data class TaskPattern(
    val toolSequence: List<String>,
    val frequency: Int,
    val avgSuccessRate: Double
)

data class FeedbackTrend(
    val positiveRate: Double,
    val correctionRate: Double,
    val totalFeedbacks: Int
)

enum class ComplexityPreference {
    CONCISE,
    BALANCED,
    DETAILED
}
