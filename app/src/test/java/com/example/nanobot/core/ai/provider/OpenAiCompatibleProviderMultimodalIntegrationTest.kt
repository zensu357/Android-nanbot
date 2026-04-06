@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.nanobot.core.ai.provider

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.nanobot.core.attachments.AttachmentStore
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.LlmAttachmentDto
import com.example.nanobot.core.model.LlmChatRequest
import com.example.nanobot.core.model.LlmMessageDto
import com.example.nanobot.core.model.ProviderType
import com.example.nanobot.core.network.HttpClientFactory
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpenAiCompatibleProviderMultimodalIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()

    @AfterTest
    fun tearDown() {
        runCatching { server.shutdown() }
        File(context.filesDir, "attachments").deleteRecursively()
    }

    @Test
    fun openAiCompatibleProviderSendsImageAttachmentAsMultimodalPayload() = runTest {
        server.start()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "choices": [
                        {
                          "index": 0,
                          "message": {
                            "role": "assistant",
                            "content": "I can see the image."
                          },
                          "finish_reason": "stop"
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )

        val attachmentFile = File(context.filesDir, "attachments/images/test-image.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6))
        }
        val provider = OpenAiCompatibleProvider(
            requestSanitizer = ProviderRequestSanitizer(),
            attachmentStore = AttachmentStore(context),
            httpClientFactory = HttpClientFactory()
        )
        val route = ProviderRegistry.resolve(
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "test-key",
            baseUrl = server.url("/").toString(),
            model = "gpt-4o-mini",
            temperature = 0.2
        )

        val result = provider.completeChat(
            config = AgentConfig(providerType = ProviderType.OPENAI_COMPATIBLE, apiKey = "test-key"),
            route = route,
            request = LlmChatRequest(
                model = "gpt-4o-mini",
                messages = listOf(
                    LlmMessageDto(
                        role = "user",
                        content = JsonPrimitive("Describe this image"),
                        attachments = listOf(
                            LlmAttachmentDto(
                                type = "image",
                                mimeType = "image/jpeg",
                                fileName = "test-image.jpg",
                                localPath = attachmentFile.relativeTo(context.filesDir).path.replace('\\', '/')
                            )
                        )
                    )
                )
            )
        )

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(requestBody.contains("\"type\":\"image_url\""))
        assertTrue(requestBody.contains("data:image/jpeg;base64,"))
        assertTrue(requestBody.contains("Describe this image"))
        assertEquals("I can see the image.", result.content)
    }

    @Test
    fun openAiCompatibleProviderReadsThoughtSignatureFromToolCallResponse() = runTest {
        server.start()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "choices": [
                        {
                          "index": 0,
                          "message": {
                            "role": "assistant",
                            "tool_calls": [
                              {
                                "id": "call_123",
                                "type": "function",
                                "thought_signature": "sig_abc",
                                "function": {
                                  "name": "web_search",
                                  "arguments": "{}"
                                }
                              }
                            ]
                          },
                          "finish_reason": "tool_calls"
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )

        val provider = OpenAiCompatibleProvider(
            requestSanitizer = ProviderRequestSanitizer(),
            attachmentStore = AttachmentStore(context),
            httpClientFactory = HttpClientFactory()
        )
        val route = ProviderRegistry.resolve(
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "test-key",
            baseUrl = server.url("/").toString(),
            model = "gemini-2.5-flash",
            temperature = 0.2
        )

        val result = provider.completeChat(
            config = AgentConfig(providerType = ProviderType.OPENAI_COMPATIBLE, apiKey = "test-key"),
            route = route,
            request = LlmChatRequest(
                model = "gemini-2.5-flash",
                messages = listOf(
                    LlmMessageDto(
                        role = "user",
                        content = JsonPrimitive("Search the web")
                    )
                )
            )
        )

        assertEquals(1, result.toolCalls.size)
        assertEquals("sig_abc", result.toolCalls.single().thoughtSignature)
        assertNull(result.content)
    }
}
