package com.example.nanobot.core.subagent

import com.example.nanobot.core.ai.AgentTurnRunner
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.MessageRole
import com.example.nanobot.core.model.SubagentRequest
import com.example.nanobot.core.model.SubagentResult
import com.example.nanobot.domain.repository.SessionRepository
import javax.inject.Inject
import javax.inject.Singleton
import javax.inject.Provider

@Singleton
class SubagentCoordinator @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val agentTurnRunnerProvider: Provider<AgentTurnRunner>
) {
    suspend fun delegate(request: SubagentRequest, config: AgentConfig): SubagentResult {
        val parentRunContext = AgentRunContext(
            sessionId = request.parentSessionId,
            parentSessionId = null,
            subagentDepth = request.subagentDepth,
            maxSubagentDepth = request.maxSubagentDepth,
            allowedToolNames = request.allowedToolNames,
            unlockedToolNames = request.unlockedToolNames,
            supportsVision = request.supportsVision
        )
        if (!parentRunContext.canDelegate()) {
            return SubagentResult(
                sessionId = null,
                parentSessionId = request.parentSessionId,
                subagentDepth = request.subagentDepth,
                summary = "Subagent delegation is blocked because the maximum subagent depth has been reached.",
                completed = false
            )
        }

        val sessionTitle = request.title?.trim().takeUnless { it.isNullOrBlank() }
            ?: buildDefaultTitle(request.task)
        val subagentSession = sessionRepository.createSession(
            title = sessionTitle,
            makeCurrent = false,
            parentSessionId = request.parentSessionId,
            subagentDepth = request.subagentDepth + 1
        )
        val userMessage = ChatMessage(
            sessionId = subagentSession.id,
            role = MessageRole.USER,
            content = request.task
        )
        sessionRepository.saveMessage(userMessage)

        val turnResult = agentTurnRunnerProvider.get().runTurn(
            sessionId = subagentSession.id,
            history = emptyList(),
            userInput = request.task,
            attachments = emptyList(),
            config = config,
            runContext = parentRunContext.child(subagentSession.id),
            onProgress = {}
        )

        turnResult.newMessages.forEach { sessionRepository.saveMessage(it) }
        sessionRepository.touchSession(subagentSession, makeCurrent = false)

        return SubagentResult(
            sessionId = subagentSession.id,
            parentSessionId = request.parentSessionId,
            subagentDepth = request.subagentDepth + 1,
            summary = summarize(turnResult.newMessages),
            artifactPaths = collectArtifactPaths(turnResult.newMessages),
            completed = true
        )
    }

    private fun summarize(messages: List<ChatMessage>): String {
        val assistantMessages = messages
            .filter { it.role == MessageRole.ASSISTANT }
            .mapNotNull { it.content?.trim() }
            .filter { it.isNotBlank() }
        if (assistantMessages.isEmpty()) {
            return "The subagent finished without producing a text summary."
        }

        return assistantMessages.last().lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .take(600)
    }

    private fun collectArtifactPaths(messages: List<ChatMessage>): List<String> {
        return messages
            .filter { it.role == MessageRole.TOOL }
            .mapNotNull { it.content }
            .flatMap { content ->
                content.lineSequence()
                    .map { it.trim() }
                    .filter { it.startsWith("Path:") }
                    .map { it.removePrefix("Path:").trim() }
                    .toList()
            }
            .distinct()
    }

    private fun buildDefaultTitle(task: String): String {
        val prefix = task.trim().ifBlank { "Subtask" }
        return "Subagent: ${prefix.take(32)}"
    }
}
