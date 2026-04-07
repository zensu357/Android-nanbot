package com.example.nanobot.data.mapper

import com.example.nanobot.core.database.entity.TaskPlanEntity
import com.example.nanobot.core.taskplan.StepStatus
import com.example.nanobot.core.taskplan.TaskPlan
import com.example.nanobot.core.taskplan.TaskPlanStatus
import com.example.nanobot.core.taskplan.TaskStep
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val taskPlanJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

fun TaskPlanEntity.toModel(): TaskPlan = TaskPlan(
    id = id,
    sessionId = sessionId,
    title = title,
    originalGoal = originalGoal,
    steps = decodeSteps(stepsJson),
    status = runCatching { TaskPlanStatus.valueOf(status) }.getOrDefault(TaskPlanStatus.FAILED),
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun TaskPlan.toEntity(): TaskPlanEntity = TaskPlanEntity(
    id = id,
    sessionId = sessionId,
    title = title,
    originalGoal = originalGoal,
    stepsJson = encodeSteps(steps),
    status = status.name,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun encodeSteps(steps: List<TaskStep>): String {
    return taskPlanJson.encodeToString(ListSerializer(TaskStep.serializer()), steps)
}

private fun decodeSteps(raw: String): List<TaskStep> {
    return runCatching {
        taskPlanJson.decodeFromString(ListSerializer(TaskStep.serializer()), raw)
    }.getOrElse {
        emptyList()
    }.mapIndexed { index, step ->
        if (step.index == index) step else step.copy(index = index)
    }
}
