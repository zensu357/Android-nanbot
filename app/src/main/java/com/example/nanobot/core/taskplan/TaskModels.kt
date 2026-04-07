package com.example.nanobot.core.taskplan

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class TaskPlan(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val title: String,
    val originalGoal: String,
    val steps: List<TaskStep>,
    val status: TaskPlanStatus = TaskPlanStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class TaskStep(
    val id: String = UUID.randomUUID().toString(),
    val index: Int,
    val description: String,
    val dependsOn: List<String> = emptyList(),
    val status: StepStatus = StepStatus.PENDING,
    val result: String? = null,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 2,
    val delegatable: Boolean = false,
    val estimatedComplexity: StepComplexity = StepComplexity.MEDIUM,
    val startedAt: Long? = null,
    val completedAt: Long? = null
)

@Serializable
enum class TaskPlanStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Serializable
enum class StepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    SKIPPED
}

@Serializable
enum class StepComplexity {
    LOW,
    MEDIUM,
    HIGH
}

data class TaskProgress(
    val planId: String,
    val currentStepIndex: Int,
    val totalSteps: Int,
    val currentStepDescription: String
)
