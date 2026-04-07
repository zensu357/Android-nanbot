package com.example.nanobot.core.taskplan

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.LlmChatRequest
import com.example.nanobot.core.model.ProviderChatResult
import com.example.nanobot.domain.repository.ChatRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class TaskPlannerTest {
    @Test
    fun returnsCompletedPlanWhenGoalIsSimple() = runTest {
        val planner = TaskPlanner(
            chatRepository = StaticChatRepository(
                """
                {
                  "complexity": 2,
                  "title": "Simple task",
                  "steps": []
                }
                """.trimIndent()
            )
        )

        val plan = planner.plan("Rename one variable", "session-1", AgentConfig())

        assertEquals(TaskPlanStatus.COMPLETED, plan.status)
        assertTrue(plan.steps.isEmpty())
    }

    @Test
    fun createsPendingPlanWithResolvedDependenciesForComplexGoal() = runTest {
        val planner = TaskPlanner(
            chatRepository = StaticChatRepository(
                """
                {
                  "complexity": 4,
                  "title": "Refactor module",
                  "steps": [
                    {
                      "description": "Inspect the existing implementation",
                      "depends_on": [],
                      "delegatable": true,
                      "complexity": "LOW"
                    },
                    {
                      "description": "Apply the refactor",
                      "depends_on": [0],
                      "delegatable": false,
                      "complexity": "HIGH"
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val plan = planner.plan("Refactor this module", "session-1", AgentConfig())

        assertEquals(TaskPlanStatus.PENDING, plan.status)
        assertEquals(2, plan.steps.size)
        assertEquals("Refactor module", plan.title)
        assertTrue(plan.steps.first().delegatable)
        assertEquals(StepComplexity.LOW, plan.steps.first().estimatedComplexity)
        assertFalse(plan.steps[1].dependsOn.isEmpty())
        assertEquals(plan.steps.first().id, plan.steps[1].dependsOn.single())
    }

    private class StaticChatRepository(
        private val response: String
    ) : ChatRepository {
        override suspend fun completeChat(request: LlmChatRequest, config: AgentConfig): ProviderChatResult {
            return ProviderChatResult(content = response)
        }
    }
}
