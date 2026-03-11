package com.example.nanobot.core.ai

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.model.Attachment
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.LlmMessageDto
import com.example.nanobot.core.model.MessageRole
import com.example.nanobot.core.ai.provider.ProviderRegistry
import javax.inject.Inject
import kotlinx.serialization.json.JsonPrimitive

class PromptComposer @Inject constructor(
    private val systemPromptBuilder: SystemPromptBuilder,
    private val runtimeContextBuilder: RuntimeContextBuilder,
    private val memoryConsolidator: MemoryConsolidator,
    private val memoryExposurePlanner: MemoryExposurePlanner,
    private val historyExposurePlanner: HistoryExposurePlanner,
    private val promptDiagnosticsStore: PromptDiagnosticsStore
) {
    suspend fun compose(
        runContext: AgentRunContext,
        config: AgentConfig,
        history: List<ChatMessage>,
        latestUserInput: String,
        latestAttachments: List<Attachment> = emptyList()
    ): List<LlmMessageDto> {
        val route = ProviderRegistry.resolve(
            providerType = config.providerType,
            apiKey = config.apiKey,
            baseUrl = config.baseUrl,
            model = config.model,
            temperature = config.temperature
        )
        val messages = mutableListOf<LlmMessageDto>()
        val memoryExposure = if (config.enableMemory) {
            memoryExposurePlanner.buildWithDiagnostics(runContext.sessionId, latestUserInput)
        } else {
            MemoryExposureResult(null, false, 0, 0)
        }
        val systemPrompt = systemPromptBuilder.buildWithDiagnostics(
            config = config,
            memoryContext = memoryExposure.context,
            latestUserInput = latestUserInput
        )
        messages += LlmMessageDto(
            role = "system",
            content = systemPrompt.prompt.let(::JsonPrimitive)
        )
        val historyExposure = historyExposurePlanner.planWithDiagnostics(config, history)
        historyExposure.messages.forEach { message ->
            messages += message.toLlmMessage()
        }
        val runtimeContext = runtimeContextBuilder.buildWithDiagnostics(config, runContext, route, latestUserInput)
        messages += LlmMessageDto(
            role = MessageRole.USER.name.lowercase(),
            content = JsonPrimitive(
                runtimeContext.context + "\n\n" + latestUserInput
            ),
            attachments = latestAttachments.map { attachment ->
                com.example.nanobot.core.model.LlmAttachmentDto(
                    type = attachment.type.name.lowercase(),
                    mimeType = attachment.mimeType,
                    fileName = attachment.displayName,
                    localPath = attachment.localPath
                )
            }
        )
        promptDiagnosticsStore.publish(
            PromptDiagnosticsSnapshot(
                systemPromptChars = systemPrompt.prompt.length,
                systemPromptSections = systemPrompt.sectionTitles,
                catalogSkillIds = systemPrompt.catalogSkillIds,
                expandedSkillIds = systemPrompt.expandedSkillIds,
                memorySummaryIncluded = memoryExposure.summaryIncluded,
                memorySessionFactCount = memoryExposure.sessionFactCount,
                memoryLongTermFactCount = memoryExposure.longTermFactCount,
                runtimeDiagnosticsEnabled = runtimeContext.diagnosticsEnabled,
                runtimeContextChars = runtimeContext.context.length,
                historyOriginalCount = historyExposure.originalCount,
                historyKeptCount = historyExposure.keptCount,
                historyTruncatedMessageCount = historyExposure.truncatedMessageCount
            )
        )
        return messages
    }
}
