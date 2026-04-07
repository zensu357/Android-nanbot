package com.example.nanobot.core.learning

import com.example.nanobot.core.database.dao.BehaviorEventDao
import com.example.nanobot.core.database.entity.BehaviorEventEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BehaviorTracker @Inject constructor(
    private val eventDao: BehaviorEventDao
) {
    suspend fun trackToolUsage(event: ToolUsageEvent) {
        eventDao.insert(
            BehaviorEventEntity(
                type = EventType.TOOL_USAGE.name,
                key = event.toolName,
                sessionId = event.sessionId,
                metadata = buildString {
                    append("{\"arguments\":\"")
                    append(escapeJson(sanitizeArguments(event.arguments)))
                    append("\",\"success\":")
                    append(event.success)
                    append(",\"duration_ms\":")
                    append(event.durationMs)
                    append(",\"turn_index\":")
                    append(event.turnIndex)
                    append('}')
                },
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun trackTaskCompletion(event: TaskCompletionEvent) {
        eventDao.insert(
            BehaviorEventEntity(
                type = EventType.TASK_COMPLETION.name,
                key = event.taskType,
                sessionId = event.sessionId,
                metadata = buildString {
                    append("{\"tool_sequence\":[")
                    append(event.toolSequence.joinToString(",") { tool -> "\"${escapeJson(tool)}\"" })
                    append("],\"total_turns\":")
                    append(event.totalTurns)
                    append(",\"success\":")
                    append(event.success)
                    append('}')
                },
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun trackFeedback(event: FeedbackEvent) {
        eventDao.insert(
            BehaviorEventEntity(
                type = EventType.FEEDBACK.name,
                key = event.signal.name,
                sessionId = event.sessionId,
                metadata = buildString {
                    append("{\"message_id\":\"")
                    append(escapeJson(event.messageId))
                    append("\",\"signal\":\"")
                    append(event.signal.name)
                    append('"')
                    event.context?.takeIf { it.isNotBlank() }?.let { context ->
                        append(",\"context\":\"")
                        append(escapeJson(context.take(200)))
                        append('"')
                    }
                    event.responseStyle?.let { responseStyle ->
                        append(",\"response_style\":\"")
                        append(responseStyle.name.lowercase())
                        append('"')
                    }
                    append('}')
                },
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun trackSkillActivation(skillId: String, sessionId: String, wasUserRequested: Boolean) {
        eventDao.insert(
            BehaviorEventEntity(
                type = EventType.SKILL_ACTIVATION.name,
                key = skillId,
                sessionId = sessionId,
                metadata = "{\"user_requested\":$wasUserRequested}",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    private fun sanitizeArguments(arguments: String): String {
        return arguments.replace(Regex("\\s+"), " ").trim().take(200)
    }

    private fun escapeJson(value: String): String {
        return buildString(value.length) {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
    }
}
