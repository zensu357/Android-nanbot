package com.example.nanobot.core.tools.impl

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.subagent.AggregationStrategy
import com.example.nanobot.core.subagent.AgentRole
import com.example.nanobot.core.subagent.ParallelDispatcher
import com.example.nanobot.core.subagent.SubtaskSpec
import com.example.nanobot.core.tools.AgentTool
import com.example.nanobot.core.tools.ToolAccessCategory
import javax.inject.Inject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class ParallelDelegateTool @Inject constructor(
    private val dispatcher: ParallelDispatcher
) : AgentTool {
    override val name: String = "parallel_delegate"
    override val description: String =
        "Delegates multiple independent subtasks to isolated child sessions and merges their results"
    override val accessCategory: ToolAccessCategory = ToolAccessCategory.LOCAL_ORCHESTRATION
    override val availabilityHint: String =
        "Runs multiple subtasks in parallel; blocked by subagent depth or the current parallel limit"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("subtasks") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("task") {
                            put("type", "string")
                            put("description", "The focused subtask to delegate")
                        }
                        putJsonObject("title") {
                            put("type", "string")
                            put("description", "Optional short label for the delegated task")
                        }
                        putJsonObject("role") {
                            put("type", "string")
                            put("enum", JsonArray(AgentRole.entries.map { JsonPrimitive(it.name) }))
                            put("description", "Specialized role prompt for the child agent")
                        }
                        putJsonObject("priority") {
                            put("type", "integer")
                            put("description", "Higher values appear first in merged results")
                        }
                    }
                    put("required", buildJsonArray { add(JsonPrimitive("task")) })
                }
                put("description", "Independent subtasks to run in parallel")
            }
            putJsonObject("strategy") {
                put("type", "string")
                put("enum", JsonArray(AggregationStrategy.entries.map { JsonPrimitive(it.name) }))
                put("default", "MERGE_ALL")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("subtasks")) })
    }

    override fun isAvailable(config: AgentConfig, runContext: AgentRunContext): Boolean {
        return runContext.canDelegate() && runContext.maxParallelSubagents > 1
    }

    override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
        val subtasksJson = arguments["subtasks"]?.jsonArray
            ?: return "The 'subtasks' array is required for parallel_delegate."
        if (subtasksJson.isEmpty()) {
            return "The 'subtasks' array must contain at least one task."
        }

        val specs: List<SubtaskSpec> = try {
            buildList<SubtaskSpec> {
                subtasksJson.forEachIndexed { index, item ->
                    val obj = item.jsonObject
                    val task = obj["task"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                    if (task.isBlank()) {
                        throw IllegalArgumentException("Subtask ${index + 1} is missing a non-blank 'task' field.")
                    }
                    val roleName = obj["role"]?.jsonPrimitive?.contentOrNull?.trim()
                    val role: AgentRole = if (roleName.isNullOrEmpty()) {
                        AgentRole.GENERAL
                    } else {
                        runCatching { enumValueOf<AgentRole>(roleName) }
                            .getOrElse {
                                throw IllegalArgumentException(
                                    "Subtask ${index + 1} has an unsupported role '$roleName'."
                                )
                            }
                    }
                    add(
                        SubtaskSpec(
                            task = task,
                            title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null },
                            role = role,
                            priority = obj["priority"]?.jsonPrimitive?.intOrNull ?: 50
                        )
                    )
                }
            }
        } catch (illegalArgumentException: IllegalArgumentException) {
            return illegalArgumentException.message ?: "Invalid parallel delegation request."
        }

        if (!runContext.canParallel(specs.size)) {
            return "Parallel delegation supports at most ${runContext.maxParallelSubagents} subtasks in the current run context."
        }

        val strategyName = arguments["strategy"]?.jsonPrimitive?.contentOrNull ?: AggregationStrategy.MERGE_ALL.name
        val strategy = runCatching { enumValueOf<AggregationStrategy>(strategyName) }
            .getOrElse { return "Unknown aggregation strategy '$strategyName'." }

        val result = try {
            dispatcher.dispatchAll(
                subtasks = specs,
                config = config,
                parentContext = runContext,
                strategy = strategy
            )
        } catch (illegalArgumentException: IllegalArgumentException) {
            return illegalArgumentException.message ?: "Invalid parallel delegation request."
        }

        return if (result.success) {
            buildString {
                appendLine("## Parallel Results")
                appendLine("Subtasks: ${specs.size}")
                appendLine("Failed: ${result.failedCount}")
                appendLine()
                append(result.mergedSummary)
            }.trimEnd()
        } else {
            "Parallel delegation failed: ${result.mergedSummary}"
        }
    }
}
