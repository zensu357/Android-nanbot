package com.example.nanobot.core.taskplan

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.LlmChatRequest
import com.example.nanobot.core.model.LlmMessageDto
import com.example.nanobot.domain.repository.ChatRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

@Singleton
class TaskPlanner @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend fun plan(
        goal: String,
        sessionId: String,
        config: AgentConfig
    ): TaskPlan {
        val response = chatRepository.completeChat(
            request = LlmChatRequest(
                model = config.model,
                messages = listOf(
                    LlmMessageDto(role = "system", content = JsonPrimitive(DECOMPOSITION_PROMPT)),
                    LlmMessageDto(role = "user", content = JsonPrimitive("Goal: $goal"))
                ),
                temperature = 0.1,
                maxTokens = 800,
                tools = null,
                toolChoice = null
            ),
            config = config
        )
        val parsed = parseResponse(response.content)
        if (parsed.complexity < COMPLEXITY_THRESHOLD || parsed.steps.isEmpty()) {
            return TaskPlan(
                sessionId = sessionId,
                title = goal.take(80),
                originalGoal = goal,
                steps = emptyList(),
                status = TaskPlanStatus.COMPLETED
            )
        }

        val rawSteps = parsed.steps.take(MAX_STEP_COUNT)
        val stepIds = List(rawSteps.size) { UUID.randomUUID().toString() }
        val steps = rawSteps.mapIndexed { index, rawStep ->
            TaskStep(
                id = stepIds[index],
                index = index,
                description = rawStep.description.ifBlank { "Step ${index + 1}" },
                dependsOn = rawStep.dependsOn.mapNotNull(stepIds::getOrNull),
                delegatable = rawStep.delegatable,
                estimatedComplexity = parseComplexity(rawStep.complexity)
            )
        }

        return TaskPlan(
            sessionId = sessionId,
            title = parsed.title?.takeIf { it.isNotBlank() } ?: goal.take(80),
            originalGoal = goal,
            steps = steps,
            status = TaskPlanStatus.PENDING
        )
    }

    private fun parseResponse(content: String?): PlannerResponse {
        if (content.isNullOrBlank()) {
            return PlannerResponse()
        }
        val payload = extractJsonPayload(content)
        return runCatching {
            plannerJson.decodeFromString<PlannerResponse>(payload)
        }.getOrElse { throwable ->
            throw IllegalStateException("Task planner returned invalid JSON: ${throwable.message}", throwable)
        }
    }

    private fun extractJsonPayload(content: String): String {
        val trimmed = content.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        check(start >= 0 && end > start) { "Task planner did not return a JSON object." }
        return trimmed.substring(start, end + 1)
    }

    private fun parseComplexity(raw: String?): StepComplexity {
        val normalized = raw?.trim()?.uppercase().orEmpty()
        return runCatching { StepComplexity.valueOf(normalized) }.getOrDefault(StepComplexity.MEDIUM)
    }

    @Serializable
    private data class PlannerResponse(
        val complexity: Int = 1,
        val title: String? = null,
        val steps: List<PlannerStepResponse> = emptyList()
    )

    @Serializable
    private data class PlannerStepResponse(
        val description: String = "",
        @SerialName("depends_on") val dependsOn: List<Int> = emptyList(),
        val delegatable: Boolean = false,
        val complexity: String? = null
    )

    private companion object {
        const val COMPLEXITY_THRESHOLD = 3
        const val MAX_STEP_COUNT = 8

        val plannerJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        val DECOMPOSITION_PROMPT = """
            You are a task planner. Given a user goal, analyze its complexity and decompose it into steps.

            Respond in JSON:
            {
              "complexity": <1-5>,
              "title": "<short title>",
              "steps": [
                {
                  "description": "<what to do>",
                  "depends_on": [<step indices>],
                  "delegatable": <true if independent>,
                  "complexity": "LOW|MEDIUM|HIGH"
                }
              ]
            }

            Rules:
            - complexity 1-2: simple task, return an empty steps array
            - complexity 3-5: decompose into 2-8 concrete steps
            - each step should be independently verifiable
            - mark steps as delegatable if they do not depend on prior results
        """.trimIndent()
    }
}
