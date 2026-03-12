package com.example.nanobot.core.ai

import com.example.nanobot.core.ai.provider.LlmProvider
import com.example.nanobot.core.ai.provider.AzureOpenAiProvider
import com.example.nanobot.core.ai.provider.OpenRouterProvider
import com.example.nanobot.core.ai.provider.OpenAiProvider
import com.example.nanobot.core.ai.provider.ProviderRegistry
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.LlmChatRequest
import com.example.nanobot.core.model.ProviderChatResult
import javax.inject.Inject

class ProviderFactory @Inject constructor(
    private val openAiProvider: OpenAiProvider,
    private val openRouterProvider: OpenRouterProvider,
    private val azureOpenAiProvider: AzureOpenAiProvider
) {
    fun create(config: AgentConfig): LlmProvider {
        val resolvedRoute = ProviderRegistry.resolve(
            providerType = config.providerType,
            apiKey = config.apiKey,
            baseUrl = config.baseUrl,
            model = config.model,
            temperature = config.temperature
        )
        val unsupportedAttachmentsMessage = buildUnsupportedAttachmentMessageIfNeeded(resolvedRoute, config)
        return when (resolvedRoute.providerType) {
            com.example.nanobot.core.model.ProviderType.OPENAI_COMPATIBLE -> wrapWithAttachmentCapability(
                delegate = openAiProvider.create(config, resolvedRoute),
                unsupportedMessage = unsupportedAttachmentsMessage
            )
            com.example.nanobot.core.model.ProviderType.OPEN_ROUTER -> object : LlmProvider {
                override val route = resolvedRoute

                override suspend fun completeChat(request: LlmChatRequest): ProviderChatResult {
                    unsupportedAttachmentsMessage?.let { message ->
                        if (request.messages.any { it.attachments.isNotEmpty() }) {
                            return ProviderChatResult(content = message, finishReason = "error")
                        }
                    }
                    return openRouterProvider.create(config, resolvedRoute).completeChat(request)
                }
            }
            com.example.nanobot.core.model.ProviderType.AZURE_OPENAI -> object : LlmProvider {
                override val route = resolvedRoute

                override suspend fun completeChat(request: LlmChatRequest): com.example.nanobot.core.model.ProviderChatResult {
                    unsupportedAttachmentsMessage?.let { message ->
                        if (request.messages.any { it.attachments.isNotEmpty() }) {
                            return ProviderChatResult(content = message, finishReason = "error")
                        }
                    }
                    return azureOpenAiProvider.completeChat(config, resolvedRoute, request)
                }
            }
        }
    }

    private fun wrapWithAttachmentCapability(
        delegate: LlmProvider,
        unsupportedMessage: String?
    ): LlmProvider = object : LlmProvider {
        override val route = delegate.route

        override suspend fun completeChat(request: LlmChatRequest): ProviderChatResult {
            unsupportedMessage?.let { message ->
                if (request.messages.any { it.attachments.isNotEmpty() }) {
                    return ProviderChatResult(content = message, finishReason = "error")
                }
            }
            return delegate.completeChat(request)
        }
    }

    private fun buildUnsupportedAttachmentMessageIfNeeded(
        route: com.example.nanobot.core.ai.provider.ResolvedProviderRoute,
        config: AgentConfig
    ): String? {
        if (route.supportsImageAttachments) return null
        return when (route.providerType) {
            com.example.nanobot.core.model.ProviderType.OPEN_ROUTER ->
                "Image attachments are not supported on the current OpenRouter path in this Android build yet."
            com.example.nanobot.core.model.ProviderType.AZURE_OPENAI ->
                "Image attachments are not supported on the current Azure OpenAI path in this Android build yet."
            com.example.nanobot.core.model.ProviderType.OPENAI_COMPATIBLE ->
                "Image attachments are not supported by the current provider configuration (${route.providerLabel}) in this Android build yet."
        }
    }
}
