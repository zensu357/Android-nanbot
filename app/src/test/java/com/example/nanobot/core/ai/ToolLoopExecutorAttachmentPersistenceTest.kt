package com.example.nanobot.core.ai

import androidx.test.core.app.ApplicationProvider
import com.example.nanobot.core.attachments.AttachmentStore
import com.example.nanobot.core.model.AgentConfig
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
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ToolLoopExecutorAttachmentPersistenceTest {
    @Test
    fun persistsMultimodalToolImagesAsMessageAttachments() = runTest {
        val chatRepository = RecordingChatRepository(
            responses = listOf(
                ProviderChatResult(
                    content = null,
                    toolCalls = listOf(
                        ToolCallRequest(
                            id = "tool-persist-1",
                            name = "take_screenshot",
                            arguments = buildJsonObject {}
                        )
                    )
                ),
                ProviderChatResult(content = "Done")
            )
        )
        val toolRegistry = ToolRegistry(ToolValidator(), ToolAccessPolicy()).apply {
            register(MultimodalTool())
        }
        val executor = ToolLoopExecutor(
            chatRepository = chatRepository,
            toolRegistry = toolRegistry,
            attachmentStore = AttachmentStore(ApplicationProvider.getApplicationContext())
        )

        val result = executor.execute(
            sessionId = "persist-session",
            initialMessages = listOf(LlmMessageDto(role = "user", content = JsonPrimitive("Inspect the screen"))),
            config = AgentConfig(enableTools = true, model = "gpt-4o-mini")
        )

        val toolMessage = result.newMessages.first { it.toolCallId == "tool-persist-1" }
        assertEquals(1, toolMessage.attachments.size)
        val attachment = toolMessage.attachments.single()
        assertEquals("image/jpeg", attachment.mimeType)
        assertTrue(attachment.localPath.startsWith("attachments/images/"))
        assertTrue(attachment.sizeBytes > 0)
    }

    private class MultimodalTool : AgentTool {
        override val name: String = "take_screenshot"
        override val description: String = "take screenshot"
        override val accessCategory: ToolAccessCategory = ToolAccessCategory.LOCAL_READ_ONLY
        override val parametersSchema: JsonObject = buildJsonObject { put("type", "object") }

        override suspend fun execute(
            arguments: JsonObject,
            config: AgentConfig,
            runContext: com.example.nanobot.core.model.AgentRunContext
        ): String {
            return "Screenshot captured."
        }

        override suspend fun executeStructured(
            arguments: JsonObject,
            config: AgentConfig,
            runContext: com.example.nanobot.core.model.AgentRunContext
        ): ToolResult {
            return ToolResult.Multimodal(
                text = "Screenshot captured.",
                images = listOf(ImagePart("data:image/jpeg;base64,AAA", "Current screen screenshot"))
            )
        }
    }

    private class RecordingChatRepository(
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
}
