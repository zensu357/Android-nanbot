package com.example.nanobot.core.tools.impl

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.taskplan.StepStatus
import com.example.nanobot.core.taskplan.TaskExecutionEngine
import com.example.nanobot.core.taskplan.TaskPlan
import com.example.nanobot.core.taskplan.TaskPlanner
import com.example.nanobot.core.taskplan.TaskPlanStatus
import com.example.nanobot.core.taskplan.TaskStateStore
import com.example.nanobot.core.tools.AgentTool
import com.example.nanobot.core.tools.ToolAccessCategory
import javax.inject.Inject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class TaskPlanTool @Inject constructor(
    private val planner: TaskPlanner,
    private val engine: TaskExecutionEngine,
    private val store: TaskStateStore
) : AgentTool {
    override val name: String = "task_plan"
    override val description: String =
        "Creates, tracks, resumes, and cancels multi-step task plans for complex goals"
    override val accessCategory: ToolAccessCategory = ToolAccessCategory.LOCAL_ORCHESTRATION
    override val availabilityHint: String = "Persistent long-task planning stored locally in Room"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", JsonArray(listOf("plan", "status", "resume", "cancel").map { JsonPrimitive(it) }))
            }
            putJsonObject("goal") {
                put("type", "string")
                put("description", "The complex goal to plan")
            }
            putJsonObject("plan_id") {
                put("type", "string")
                put("description", "Optional explicit plan id")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("action")) })
    }

    override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
        val action = arguments["action"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        if (action.isBlank()) {
            return "The 'action' field is required for task_plan."
        }

        return when (action) {
            "plan" -> createPlan(arguments, config, runContext)
            "status" -> {
                val plan = resolvePlan(arguments, runContext) ?: return "No active plan found."
                formatPlan(plan)
            }
            "resume" -> resumePlan(arguments, config, runContext)
            "cancel" -> cancelPlan(arguments, runContext)
            else -> "Unknown action '$action'."
        }
    }

    private suspend fun createPlan(
        arguments: JsonObject,
        config: AgentConfig,
        runContext: AgentRunContext
    ): String {
        val goal = arguments["goal"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        if (goal.isBlank()) {
            return "The 'goal' field is required for task_plan(action=plan)."
        }
        val plan = runCatching { planner.plan(goal, runContext.sessionId, config) }
            .getOrElse { throwable ->
                return "Failed to create task plan: ${throwable.message ?: "unknown error"}"
            }
        store.save(plan)
        return if (plan.steps.isEmpty()) {
            "Task is simple enough to execute directly (complexity < threshold)."
        } else {
            formatPlan(plan)
        }
    }

    private suspend fun resumePlan(
        arguments: JsonObject,
        config: AgentConfig,
        runContext: AgentRunContext
    ): String {
        val plan = resolvePlan(arguments, runContext) ?: return "No plan to resume."
        if (plan.status == TaskPlanStatus.CANCELLED || plan.status == TaskPlanStatus.COMPLETED) {
            return "Plan '${plan.title}' is already ${plan.status.name.lowercase()}.\n${formatPlan(plan)}"
        }
        val result = runCatching { engine.execute(plan, config, runContext) }
            .getOrElse { throwable ->
                return "Failed to resume plan '${plan.title}': ${throwable.message ?: "unknown error"}"
            }
        return "Plan '${result.title}' ${result.status.name.lowercase()}.\n${formatPlan(result)}"
    }

    private suspend fun cancelPlan(arguments: JsonObject, runContext: AgentRunContext): String {
        val plan = resolvePlan(arguments, runContext) ?: return "Plan not found."
        if (plan.status == TaskPlanStatus.CANCELLED) {
            return "Plan '${plan.title}' is already cancelled."
        }
        store.save(plan.copy(status = TaskPlanStatus.CANCELLED, updatedAt = System.currentTimeMillis()))
        return "Plan '${plan.title}' cancelled."
    }

    private suspend fun resolvePlan(arguments: JsonObject, runContext: AgentRunContext): TaskPlan? {
        val planId = arguments["plan_id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { null }
        return if (planId != null) {
            store.load(planId)
        } else {
            store.activeForSession(runContext.sessionId)
        }
    }

    private fun formatPlan(plan: TaskPlan): String {
        return buildString {
            appendLine("## Task Plan: ${plan.title}")
            appendLine("Plan ID: ${plan.id}")
            appendLine("Status: ${plan.status} | Steps: ${plan.steps.size}")
            appendLine()
            plan.steps.forEach { step ->
                val icon = when (step.status) {
                    StepStatus.COMPLETED -> "[done]"
                    StepStatus.IN_PROGRESS -> "[running]"
                    StepStatus.FAILED -> "[failed x${step.retryCount}]"
                    StepStatus.SKIPPED -> "[skipped]"
                    StepStatus.PENDING -> "[pending]"
                }
                appendLine("${step.index + 1}. $icon ${step.description}")
                if (!step.errorMessage.isNullOrBlank()) {
                    appendLine("   Error: ${step.errorMessage}")
                }
            }
        }.trimEnd()
    }
}
