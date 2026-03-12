@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.nanobot.core.ai.provider

import androidx.test.core.app.ApplicationProvider
import com.example.nanobot.core.ai.ProviderFactory
import com.example.nanobot.core.attachments.AttachmentStore
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.LlmAttachmentDto
import com.example.nanobot.core.model.LlmChatRequest
import com.example.nanobot.core.model.LlmMessageDto
import com.example.nanobot.core.model.ProviderType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonPrimitive
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProviderFactoryAttachmentTest {
    @Test
    fun unsupportedProviderReturnsExplicitAttachmentUnsupportedMessage() = kotlinx.coroutines.test.runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val transport = OpenAiCompatibleProvider(
            requestSanitizer = ProviderRequestSanitizer(),
            attachmentStore = AttachmentStore(context)
        )
        val factory = ProviderFactory(
            openAiProvider = OpenAiProvider(transport),
            openRouterProvider = OpenRouterProvider(transport),
            azureOpenAiProvider = AzureOpenAiProvider(ProviderRequestSanitizer(), AttachmentStore(context))
        )
        val request = LlmChatRequest(
            model = "gpt-4o-mini",
            messages = listOf(
                LlmMessageDto(
                    role = "user",
                    content = JsonPrimitive("Describe this"),
                    attachments = listOf(
                        LlmAttachmentDto(
                            type = "image",
                            mimeType = "image/png",
                            fileName = "photo.png",
                            localPath = "attachments/images/photo.png"
                        )
                    )
                )
            )
        )

        // Use a custom provider which does not have supportsImageAttachments enabled
        val customResult = factory.create(
            AgentConfig(
                providerType = ProviderType.OPENAI_COMPATIBLE,
                baseUrl = "https://example.com/",
                model = "custom-model"
            )
        ).completeChat(request)

        assertTrue(customResult.content.orEmpty().contains("not supported"))
        assertEquals("error", customResult.finishReason)
    }
}
