package com.example.nanobot.core.tools.impl

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.model.Reminder
import com.example.nanobot.core.model.ReminderStatus
import com.example.nanobot.core.notifications.ReminderNotificationSink
import com.example.nanobot.core.tools.AgentTool
import com.example.nanobot.core.tools.ToolAccessCategory
import java.util.UUID
import javax.inject.Inject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class NotifyUserTool @Inject constructor(
    private val reminderNotificationSink: ReminderNotificationSink
) : AgentTool {
    override val name: String = "notify_user"
    override val description: String = "Creates a user-facing notification style message"
    override val accessCategory: ToolAccessCategory = ToolAccessCategory.LOCAL_SIDE_EFFECT
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("message") {
                put("type", "string")
                put("description", "The message to send to the user")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("message")) })
    }

    override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
        val message = arguments["message"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        return if (message.isBlank()) {
            "No notification message was provided."
        } else {
            runCatching {
                val now = System.currentTimeMillis()
                reminderNotificationSink.notify(
                    Reminder(
                        id = UUID.randomUUID().toString(),
                        title = null,
                        message = message,
                        triggerAt = now,
                        status = ReminderStatus.DELIVERED,
                        createdAt = now,
                        deliveredAt = now
                    )
                )
                "User notification sent: $message"
            }.getOrElse { throwable ->
                throwable.message ?: "Failed to send user notification."
            }
        }
    }
}
