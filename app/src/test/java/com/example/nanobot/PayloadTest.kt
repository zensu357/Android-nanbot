package com.example.nanobot

import com.example.nanobot.core.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlin.test.assertTrue
import org.junit.Test

class PayloadTest {
    @Test
    fun serializesToolCallTypeForAssistantHistory() {
        val networkJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        val req = LlmChatRequest(
            model = "ep-xxxx",
            messages = listOf(
                LlmMessageDto(role = "system", content = JsonPrimitive("System prompt")),
                LlmMessageDto(
                    role = "assistant",
                    content = null,
                    toolCalls = listOf(
                        LlmToolCallDto(
                            id = "call_123",
                            function = LlmToolCallFunctionDto(
                                name = "device_time",
                                arguments = "{}"
                            ),
                            thoughtSignature = "sig_abc"
                        )
                    )
                ),
                LlmMessageDto(
                    role = "tool",
                    content = JsonPrimitive("Wednesday"),
                    toolCallId = "call_123"
                )
            ),
            tools = listOf(
                LlmToolDefinitionDto(
                    type = "function",
                    function = LlmToolFunctionDto(
                        name = "test_tool",
                        description = "Test tool",
                        parameters = JsonObject(emptyMap())
                    )
                )
            ),
            toolChoice = "auto"
        )

        val encoded = networkJson.encodeToString(req)

        assertTrue(encoded.contains("\"tool_calls\":[{\"id\":\"call_123\",\"type\":\"function\""))
        assertTrue(encoded.contains("\"thought_signature\":\"sig_abc\""))
    }
}
