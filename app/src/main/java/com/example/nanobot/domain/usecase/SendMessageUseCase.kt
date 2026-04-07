package com.example.nanobot.domain.usecase

import com.example.nanobot.core.ai.AgentTurnRunner
import com.example.nanobot.core.ai.MemoryRefreshScheduler
import com.example.nanobot.core.ai.NoOpMemoryRefreshScheduler
import com.example.nanobot.core.learning.BehaviorTracker
import com.example.nanobot.core.learning.FeedbackEvent
import com.example.nanobot.core.learning.FeedbackSignal
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentProgressEvent
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.model.Attachment
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.MessageRole
import com.example.nanobot.core.skills.ActivatedSkillSessionStore
import com.example.nanobot.core.taskplan.TaskStateStore
import com.example.nanobot.domain.repository.SessionRepository
import com.example.nanobot.domain.repository.SkillRepository
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val agentTurnRunner: AgentTurnRunner,
    private val skillRepository: SkillRepository,
    private val activatedSkillSessionStore: ActivatedSkillSessionStore,
    private val taskStateStore: TaskStateStore,
    private val behaviorTracker: BehaviorTracker? = null,
    private val memoryRefreshScheduler: MemoryRefreshScheduler = NoOpMemoryRefreshScheduler
) {
    suspend operator fun invoke(
        input: String,
        attachments: List<Attachment> = emptyList(),
        config: AgentConfig,
        onProgress: suspend (AgentProgressEvent) -> Unit = {}
    ): List<ChatMessage> {
        val session = sessionRepository.getOrCreateCurrentSession()
        val normalizedInput = normalizeInputForActivePlan(session.id, input)
        val existingMessages = filterHistoryForActiveSkills(
            sessionId = session.id,
            history = sessionRepository.getHistoryForModel(session.id)
        )
        val userMessage = ChatMessage(
            sessionId = session.id,
            role = MessageRole.USER,
            content = normalizedInput,
            attachments = attachments
        )

        sessionRepository.saveMessage(userMessage)

        val turnResult = agentTurnRunner.runTurn(
            sessionId = session.id,
            history = existingMessages,
            userInput = normalizedInput,
            attachments = attachments,
            config = config,
            runContext = AgentRunContext.root(
                sessionId = session.id,
                maxSubagentDepth = config.maxSubagentDepth,
                maxParallelSubagents = config.maxParallelSubagents,
                allowedToolNames = resolveAllowedToolNames(session.id),
                unlockedToolNames = resolveUnlockedToolNames(session.id)
            ),
            onProgress = onProgress
        )

        for (message in turnResult.newMessages) {
            sessionRepository.saveMessage(message)
        }
        val finalAssistantMessage = turnResult.newMessages.lastOrNull { it.role == MessageRole.ASSISTANT }
        if (config.enableBehaviorLearning && finalAssistantMessage != null && existingMessages.any { it.role == MessageRole.ASSISTANT }) {
            behaviorTracker?.trackFeedback(
                FeedbackEvent(
                    sessionId = session.id,
                    messageId = finalAssistantMessage.id,
                    signal = FeedbackSignal.IMPLICIT_ACCEPTANCE,
                    context = input.take(120)
                )
            )
        }
        memoryRefreshScheduler.request(session.id, config)
        sessionRepository.touchSession(
            session.copy(title = input.take(24).ifBlank { "New Chat" }),
            makeCurrent = true
        )

        return buildList {
            add(userMessage)
            addAll(turnResult.newMessages)
        }
    }

    private suspend fun resolveAllowedToolNames(sessionId: String): Set<String>? {
        val activated = activatedSkillSessionStore.listActivated(sessionId)
        if (activated.isEmpty()) return null
        val declaredSets = activated.mapNotNull { record ->
            val skill = skillRepository.getSkillByName(record.skillName) ?: return@mapNotNull null
            val allowed = (
                skill.allowedTools + skillRepository.getHiddenToolEntitlements(skill)
                ).map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
            allowed.takeIf { it.isNotEmpty() }
        }
        if (declaredSets.isEmpty()) return null
        return declaredSets.reduce { acc, next -> acc intersect next }
    }

    private suspend fun resolveUnlockedToolNames(sessionId: String): Set<String> {
        val activated = activatedSkillSessionStore.listActivated(sessionId)
        if (activated.isEmpty()) return emptySet()
        return activated
            .mapNotNull { record -> skillRepository.getSkillByName(record.skillName) }
            .flatMap { skill -> skillRepository.getHiddenToolEntitlements(skill) }
            .toSet()
    }

    private fun filterHistoryForActiveSkills(sessionId: String, history: List<ChatMessage>): List<ChatMessage> {
        val activeSkillNames = activatedSkillSessionStore.listActivated(sessionId)
            .map { it.skillName.lowercase() }
            .toSet()
        return history.filterNot { message ->
            if (!message.protectedContext) return@filterNot false
            val toolName = message.toolName.orEmpty()
            if (!toolName.startsWith("activate_skill:")) return@filterNot false
            val skillName = toolName.substringAfter(':', missingDelimiterValue = "").lowercase()
            skillName.isNotBlank() && skillName !in activeSkillNames
        }
    }

    private suspend fun normalizeInputForActivePlan(sessionId: String, input: String): String {
        val normalized = input.trim()
        if (!looksLikeContinueRequest(normalized)) return input
        val activePlan = taskStateStore.activeForSession(sessionId) ?: return input
        if (activePlan.status.name !in setOf("PENDING", "IN_PROGRESS")) return input

        return buildString {
            appendLine(normalized)
            appendLine()
            appendLine("[Active task plan detected]")
            appendLine("Plan ID: ${activePlan.id}")
            appendLine("Plan Title: ${activePlan.title}")
            appendLine("Plan Status: ${activePlan.status}")
            appendLine("If continuation is appropriate, call task_plan with action=resume instead of replanning.")
        }.trimEnd()
    }

    private fun looksLikeContinueRequest(input: String): Boolean {
        val normalized = input.lowercase()
        return normalized in setOf(
            "继续",
            "继续吧",
            "接着做",
            "接着来",
            "继续执行",
            "continue",
            "resume",
            "keep going",
            "go on"
        )
    }
}
