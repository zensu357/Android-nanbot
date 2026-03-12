package com.example.nanobot.core.ai.provider

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.nanobot.core.ai.ProviderFactory
import com.example.nanobot.core.attachments.AttachmentStore
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.LlmAttachmentDto
import com.example.nanobot.core.model.LlmChatRequest
import com.example.nanobot.core.model.LlmMessageDto
import com.example.nanobot.core.model.ProviderType
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class OpenAiCompatibleProviderAttachmentTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @AfterTest
    fun tearDown() {
        File(context.filesDir, "attachments").deleteRecursively()
    }

    @Test
    fun providerRegistryMarksOpenAiAsImageCapable() {
        val route = ProviderRegistry.resolve(
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "",
            baseUrl = "https://api.openai.com/",
            model = "gpt-4o-mini",
            temperature = 0.2
        )

        assertTrue(route.supportsImageAttachments)
    }

    @Test
    fun providerFactoryRejectsAttachmentsWhenResolvedProviderLacksCapability() = runTest {
        val factory = ProviderFactory(
            openAiProvider = OpenAiProvider(OpenAiCompatibleProvider(ProviderRequestSanitizer(), AttachmentStore(context))),
            openRouterProvider = OpenRouterProvider(OpenAiCompatibleProvider(ProviderRequestSanitizer(), AttachmentStore(context))),
            azureOpenAiProvider = AzureOpenAiProvider(ProviderRequestSanitizer(), AttachmentStore(context))
        )
        val request = LlmChatRequest(
            model = "gpt-4o-mini",
            messages = listOf(
                LlmMessageDto(
                    role = "user",
                    content = JsonPrimitive("Describe this image"),
                    attachments = listOf(
                        LlmAttachmentDto(
                            type = "image",
                            mimeType = "image/jpeg",
                            fileName = "photo.jpg",
                            localPath = "attachments/images/photo.jpg"
                        )
                    )
                )
            )
        )

        val result = factory.create(
            AgentConfig(
                providerType = ProviderType.OPENAI_COMPATIBLE,
                baseUrl = "https://example.com/",
                model = "custom-model"
            )
        ).completeChat(request)

        assertEquals("error", result.finishReason)
        assertTrue(result.content.orEmpty().contains("not supported"))
    }
}
