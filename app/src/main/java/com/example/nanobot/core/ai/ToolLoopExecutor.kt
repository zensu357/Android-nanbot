package com.example.nanobot.core.ai

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentProgressEvent
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.model.AgentTurnResult
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.LlmChatRequest
import com.example.nanobot.core.model.LlmMessageDto
import com.example.nanobot.core.model.MessageRole
import com.example.nanobot.domain.repository.ChatRepository
import com.example.nanobot.core.tools.ToolRegistry
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonPrimitive

class ToolLoopExecutor @Inject constructor(
    private val chatRepository: ChatRepository,
    private val toolRegistry: ToolRegistry
) {
    suspend fun execute(
        sessionId: String,
        initialMessages: List<com.example.nanobot.core.model.LlmMessageDto>,
        config: AgentConfig,
        runContext: AgentRunContext = AgentRunContext.root(sessionId, config.maxSubagentDepth),
        maxIterations: Int = config.maxToolIterations,
        onProgress: suspend (AgentProgressEvent) -> Unit = {}
    ): AgentTurnResult {
        val messages = initialMessages.toMutableList()
        val emittedMessages = mutableListOf<ChatMessage>()
        val visibleToolNames = toolRegistry.visibleTools(config, runContext).map { it.name }.toSet()
        val availableTools = toolRegistry.getDefinitions(config, runContext).ifEmpty { null }

        onProgress(AgentProgressEvent.Started)

        repeat(maxIterations) {
            onProgress(AgentProgressEvent.Thinking)
            val response = chatRepository.completeChat(
                request = LlmChatRequest(
                    model = config.model,
                    messages = messages,
                    temperature = config.temperature,
                    maxTokens = config.maxTokens,
                    tools = if (config.enableTools) availableTools else null,
                    toolChoice = if (config.enableTools && availableTools != null) "auto" else null
                ),
                config = config
            )

            val assistantMessage = response.toAssistantMessage(sessionId)
            emittedMessages += assistantMessage
            messages += assistantMessage.toLlmMessage()

            if (response.toolCalls.isEmpty()) {
                onProgress(AgentProgressEvent.Finishing)
                onProgress(AgentProgressEvent.Completed)
                return AgentTurnResult(
                    newMessages = emittedMessages,
                    finalResponse = assistantMessage
                )
            }

            response.toolCalls.forEach { toolCall ->
                onProgress(AgentProgressEvent.ToolCalling(toolCall.name))
                if (toolCall.name !in visibleToolNames) {
                    onProgress(AgentProgressEvent.Error("Tool '${toolCall.name}' is blocked by the current tool access policy."))
                }
                val result = try {
                    toolRegistry.execute(toolCall.name, toolCall.arguments, config, runContext)
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    onProgress(AgentProgressEvent.Error(throwable.message ?: "Tool execution failed."))
                    throw throwable
                }
                val toolMessage = ChatMessage(
                    sessionId = sessionId,
                    role = MessageRole.TOOL,
                    content = result,
                    toolCallId = toolCall.id,
                    toolName = toolCall.name.let { name ->
                        if (name == "activate_skill") {
                            val skillName = toolCall.arguments["name"]?.toString()?.trim('"').orEmpty()
                            if (skillName.isNotBlank()) "activate_skill:$skillName" else name
                        } else {
                            name
                        }
                    },
                    protectedContext = toolCall.name == "activate_skill"
                )
                emittedMessages += toolMessage
                messages += toolMessage.toLlmMessage()
                onProgress(AgentProgressEvent.ToolResult(toolCall.name))
            }
        }

        onProgress(AgentProgressEvent.Error("Maximum tool-iteration limit reached. Requesting a final answer without further tool calls."))
        val fallback = requestFinalAnswerAfterToolLimit(
            sessionId = sessionId,
            messages = messages,
            emittedMessages = emittedMessages,
            config = config,
            onProgress = onProgress,
            finalizationReason = "Maximum tool-iteration limit reached."
        )
        return AgentTurnResult(
            newMessages = emittedMessages + fallback,
            finalResponse = fallback
        )
    }

    private suspend fun requestFinalAnswerAfterToolLimit(
        sessionId: String,
        messages: MutableList<com.example.nanobot.core.model.LlmMessageDto>,
        emittedMessages: MutableList<ChatMessage>,
        config: AgentConfig,
        onProgress: suspend (AgentProgressEvent) -> Unit,
        finalizationReason: String
    ): ChatMessage {
        val finalizationPrompt = LlmMessageDto(
            role = MessageRole.USER.name.lowercase(),
            content = JsonPrimitive(
                "$finalizationReason Do not call any more tools. Use only the conversation and tool results already available, then provide the best possible final answer now."
            )
        )
        messages += finalizationPrompt
        return runCatching {
            onProgress(AgentProgressEvent.Thinking)
            val response = chatRepository.completeChat(
                request = LlmChatRequest(
                    model = config.model,
                    messages = messages,
                    temperature = config.temperature,
                    maxTokens = config.maxTokens,
                    tools = null,
                    toolChoice = null
                ),
                config = config
            )
            val finalAssistant = response.toAssistantMessage(sessionId)
            if (!finalAssistant.content.isNullOrBlank()) {
                onProgress(AgentProgressEvent.Finishing)
                onProgress(AgentProgressEvent.Completed)
                return@runCatching finalAssistant
            }
            fallbackLimitMessage(sessionId)
        }.getOrElse { throwable ->
            onProgress(AgentProgressEvent.Error(throwable.message ?: "Failed to finalize after hitting the tool limit."))
            fallbackLimitMessage(sessionId)
        }
    }

    private fun fallbackLimitMessage(sessionId: String): ChatMessage {
        return ChatMessage(
            sessionId = sessionId,
            role = MessageRole.ASSISTANT,
            content = "I reached the maximum tool-iteration limit before finishing this task. Please continue the conversation if you want me to keep working from the results gathered so far."
        )
    }
}
