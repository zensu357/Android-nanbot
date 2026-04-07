package com.example.nanobot.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.nanobot.core.ai.AgentTurnRunner
import com.example.nanobot.core.ai.HeartbeatDecisionEngine
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.ChatSession
import com.example.nanobot.core.model.MessageRole
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.notifications.HeartbeatNotificationSink
import com.example.nanobot.core.preferences.SettingsConfigStore
import com.example.nanobot.domain.repository.SessionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class HeartbeatWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsDataStore: SettingsConfigStore,
    private val heartbeatDecider: HeartbeatDecisionEngine,
    private val sessionRepository: SessionRepository,
    private val agentOrchestrator: AgentTurnRunner,
    private val heartbeatNotifier: HeartbeatNotificationSink
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val config = settingsDataStore.configFlow.first()
        if (!config.enableBackgroundWork) {
            return Result.success()
        }

        val decision = heartbeatDecider.decide(config)
        if (decision.action != ACTION_RUN || decision.tasks.isBlank()) {
            return Result.success()
        }

        val session = getOrCreateHeartbeatSession()
        val existingMessages = sessionRepository.getHistoryForModel(session.id)
        val userMessage = ChatMessage(
            sessionId = session.id,
            role = MessageRole.USER,
            content = decision.tasks
        )
        sessionRepository.saveMessage(userMessage)

        val turnResult = runCatching {
            agentOrchestrator.runTurn(
                sessionId = session.id,
                history = existingMessages,
                userInput = decision.tasks,
                attachments = emptyList(),
                config = config,
                runContext = AgentRunContext.root(
                    sessionId = session.id,
                    maxSubagentDepth = config.maxSubagentDepth,
                    maxParallelSubagents = config.maxParallelSubagents
                ),
                onProgress = {}
            )
        }.getOrElse { throwable ->
            heartbeatNotifier.notifyFailed(
                throwable.message ?: "Heartbeat execution failed before a response was produced."
            )
            return Result.success()
        }

        for (message in turnResult.newMessages) {
            sessionRepository.saveMessage(message)
        }
        sessionRepository.upsertSession(
            session.copy(updatedAt = System.currentTimeMillis()),
            makeCurrent = false
        )

        val deliverySummary = turnResult.finalResponse?.content
            ?.trim()
            ?.ifBlank { decision.tasks }
            ?: decision.tasks
        heartbeatNotifier.notifyExecuted(deliverySummary)

        return Result.success()
    }

    private suspend fun getOrCreateHeartbeatSession(): ChatSession {
        return sessionRepository.getSessionByTitle(HEARTBEAT_SESSION_TITLE)
            ?: sessionRepository.upsertSession(
                ChatSession(title = HEARTBEAT_SESSION_TITLE),
                makeCurrent = false
            )
    }

    private companion object {
        const val ACTION_RUN = "run"
        const val HEARTBEAT_SESSION_TITLE = "Heartbeat"
    }
}
