package com.example.nanobot.core.tools.impl

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.model.Reminder
import com.example.nanobot.core.model.ReminderStatus
import com.example.nanobot.core.tools.AgentTool
import com.example.nanobot.core.tools.ToolAccessCategory
import com.example.nanobot.core.worker.ReminderWorkScheduler
import com.example.nanobot.domain.repository.ReminderRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class ScheduleReminderTool @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val reminderWorkScheduler: ReminderWorkScheduler
) : AgentTool {
    override val name: String = "schedule_reminder"
    override val description: String = "Creates a reminder request that can be tracked and executed later"
    override val accessCategory: ToolAccessCategory = ToolAccessCategory.LOCAL_SIDE_EFFECT
    override val availabilityHint: String = "Available when background work is enabled"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("message") {
                put("type", "string")
                put("description", "Reminder body text")
            }
            putJsonObject("delayMinutes") {
                put("type", "integer")
                put("description", "Delay in minutes before the reminder should trigger")
            }
            putJsonObject("title") {
                put("type", "string")
                put("description", "Optional reminder title")
            }
        }
        put("required", buildJsonArray {
            add(JsonPrimitive("message"))
            add(JsonPrimitive("delayMinutes"))
        })
    }

    override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
        val message = arguments["message"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        val delayMinutes = arguments["delayMinutes"]?.jsonPrimitive?.intOrNull
        val title = arguments["title"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }

        if (message.isBlank()) {
            return "The 'message' field is required for schedule_reminder."
        }
        if (delayMinutes == null || delayMinutes <= 0) {
            return "The 'delayMinutes' field must be a positive integer."
        }

        val now = System.currentTimeMillis()
        val triggerAt = now + delayMinutes * 60_000L
        val reminder = Reminder(
            id = UUID.randomUUID().toString(),
            title = title,
            message = message,
            triggerAt = triggerAt,
            status = ReminderStatus.SCHEDULED,
            createdAt = now,
            deliveredAt = null,
            errorMessage = null
        )
        reminderRepository.upsert(reminder)
        reminderWorkScheduler.scheduleReminder(reminder, runContext.sessionId)

        val formattedTrigger = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(triggerAt))

        return buildString {
            appendLine("Reminder created.")
            appendLine("ID: ${reminder.id}")
            appendLine("Trigger Time: $formattedTrigger")
            appendLine("Message: ${reminder.message}")
            reminder.title?.let { appendLine("Title: $it") }
        }.trim()
    }
}
