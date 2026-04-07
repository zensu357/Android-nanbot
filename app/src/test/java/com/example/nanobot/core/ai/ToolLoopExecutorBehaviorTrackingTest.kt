package com.example.nanobot.core.ai

import com.example.nanobot.core.learning.BehaviorTracker
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.model.LlmMessageDto
import com.example.nanobot.core.model.ProviderChatResult
import com.example.nanobot.core.model.ToolCallRequest
import com.example.nanobot.core.tools.AgentTool
import com.example.nanobot.core.tools.ToolAccessCategory
import com.example.nanobot.core.tools.ToolAccessPolicy
import com.example.nanobot.core.tools.ToolRegistry
import com.example.nanobot.core.tools.ToolValidator
import com.example.nanobot.domain.repository.ChatRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class ToolLoopExecutorBehaviorTrackingTest {
    @Test
    fun recordsToolUsageAfterSuccessfulExecution() = runTest {
        val dao = FakeBehaviorEventDao()
        val tracker = BehaviorTracker(dao)
        val chatRepository = ScriptedChatRepository(
            responses = listOf(
                ProviderChatResult(
                    content = null,
                    toolCalls = listOf(
                        ToolCallRequest(
                            id = "tool-1",
                            name = "device_time",
                            arguments = buildJsonObject { }
                        )
                    )
                ),
                ProviderChatResult(content = "Done")
            )
        )
        val toolRegistry = ToolRegistry(ToolValidator(), ToolAccessPolicy()).apply {
            register(
                StaticTool(
                    name = "device_time",
                    accessCategory = ToolAccessCategory.LOCAL_READ_ONLY,
                    result = "time"
                )
            )
        }
        val executor = ToolLoopExecutor(chatRepository, toolRegistry, behaviorTracker = tracker)

        executor.execute(
            sessionId = "session-1",
            initialMessages = listOf(LlmMessageDto(role = "user", content = JsonPrimitive("What time is it?"))),
            config = AgentConfig(enableTools = true, enableBehaviorLearning = true)
        )

        val event = dao.allEvents().single()
        assertEquals("TOOL_USAGE", event.type)
        assertEquals("device_time", event.key)
        assertTrue(event.metadata.contains("\"turn_index\":0"))
    }

    private class ScriptedChatRepository(
        private val responses: List<ProviderChatResult>
    ) : ChatRepository {
        private var index = 0

        override suspend fun completeChat(
            request: com.example.nanobot.core.model.LlmChatRequest,
            config: AgentConfig
        ): ProviderChatResult {
            return responses.getOrElse(index++) { responses.last() }
        }
    }

    private class StaticTool(
        override val name: String,
        override val accessCategory: ToolAccessCategory,
        private val result: String
    ) : AgentTool {
        override val description: String = name
        override val parametersSchema: JsonObject = buildJsonObject { }

        override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
            return result
        }
    }
}
