package com.example.nanobot.core.ai

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.model.LlmMessageDto
import com.example.nanobot.core.model.ProviderChatResult
import com.example.nanobot.core.model.ToolCallRequest
import com.example.nanobot.core.tools.AgentTool
import com.example.nanobot.core.tools.ImagePart
import com.example.nanobot.core.tools.ToolAccessCategory
import com.example.nanobot.core.tools.ToolAccessPolicy
import com.example.nanobot.core.tools.ToolRegistry
import com.example.nanobot.core.tools.ToolResult
import com.example.nanobot.core.tools.ToolValidator
import com.example.nanobot.domain.repository.ChatRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class ToolLoopExecutorMultimodalTest {
    @Test
    fun sendsToolMultimodalResultAsImageUrlContentPartsForVisionProviders() = runTest {
        val chatRepository = RecordingChatRepository(
            responses = listOf(
                ProviderChatResult(
                    content = null,
                    toolCalls = listOf(
                        ToolCallRequest(
                            id = "tool-vision-1",
                            name = "take_screenshot",
                            arguments = buildJsonObject {}
                        )
                    )
                ),
                ProviderChatResult(content = "I inspected the screenshot.")
            )
        )
        val toolRegistry = ToolRegistry(ToolValidator(), ToolAccessPolicy()).apply {
            register(MultimodalTool())
        }
        val executor = ToolLoopExecutor(chatRepository, toolRegistry)

        val result = executor.execute(
            sessionId = "vision-session",
            initialMessages = listOf(LlmMessageDto(role = "user", content = JsonPrimitive("Inspect the screen"))),
            config = AgentConfig(enableTools = true, model = "gpt-4o-mini")
        )

        assertEquals("I inspected the screenshot.", result.finalResponse?.content)
        val toolMessage = chatRepository.requests[1].messages.last()
        assertEquals("tool", toolMessage.role)
        assertEquals("tool-vision-1", toolMessage.toolCallId)
        val content = assertIs<JsonArray>(toolMessage.content)
        assertEquals("text", content[0].jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals("image_url", content[1].jsonObject["type"]?.jsonPrimitive?.content)
        assertTrue(
            content[1].jsonObject["image_url"]
                ?.jsonObject
                ?.get("url")
                ?.jsonPrimitive
                ?.content
                ?.startsWith("data:image/jpeg;base64,") == true
        )
    }

    @Test
    fun fallsBackToPlainTextToolMessagesWhenProviderLacksVision() = runTest {
        val chatRepository = RecordingChatRepository(
            responses = listOf(
                ProviderChatResult(
                    content = null,
                    toolCalls = listOf(
                        ToolCallRequest(
                            id = "tool-vision-2",
                            name = "take_screenshot",
                            arguments = buildJsonObject {}
                        )
                    )
                ),
                ProviderChatResult(content = "Used the fallback text.")
            )
        )
        val toolRegistry = ToolRegistry(ToolValidator(), ToolAccessPolicy()).apply {
            register(MultimodalTool())
        }
        val executor = ToolLoopExecutor(chatRepository, toolRegistry)

        executor.execute(
            sessionId = "text-session",
            initialMessages = listOf(LlmMessageDto(role = "user", content = JsonPrimitive("Inspect the screen"))),
            config = AgentConfig(enableTools = true, providerType = com.example.nanobot.core.model.ProviderType.OPENAI_COMPATIBLE, model = "moonshot-v1")
        )

        val toolMessage = chatRepository.requests[1].messages.last()
        assertIs<JsonPrimitive>(toolMessage.content)
        assertEquals("Screenshot captured.", toolMessage.content?.jsonPrimitive?.content)
        assertEquals("tool", toolMessage.role)
    }

    private class MultimodalTool : AgentTool {
        override val name: String = "take_screenshot"
        override val description: String = "take screenshot"
        override val accessCategory: ToolAccessCategory = ToolAccessCategory.LOCAL_READ_ONLY
        override val parametersSchema: JsonObject = buildJsonObject { put("type", "object") }

        override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
            return "Screenshot captured."
        }

        override suspend fun executeStructured(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): ToolResult {
            return ToolResult.Multimodal(
                text = "Screenshot captured.",
                images = listOf(ImagePart("data:image/jpeg;base64,AAA"))
            )
        }
    }

    private class RecordingChatRepository(
        private val responses: List<ProviderChatResult>
    ) : ChatRepository {
        val requests = mutableListOf<com.example.nanobot.core.model.LlmChatRequest>()
        private var index = 0

        override suspend fun completeChat(
            request: com.example.nanobot.core.model.LlmChatRequest,
            config: AgentConfig
        ): ProviderChatResult {
            requests += request
            return responses.getOrElse(index++) { responses.last() }
        }
    }
}
