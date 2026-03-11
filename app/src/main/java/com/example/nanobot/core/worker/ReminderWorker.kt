package com.example.nanobot.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.MessageRole
import com.example.nanobot.core.notifications.ReminderNotificationSink
import com.example.nanobot.domain.repository.ReminderRepository
import com.example.nanobot.domain.repository.SessionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val reminderRepository: ReminderRepository,
    private val reminderNotifier: ReminderNotificationSink,
    private val sessionRepository: SessionRepository
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val reminderId = inputData.getString(KEY_REMINDER_ID)
        val sessionId = inputData.getString(KEY_SESSION_ID)
        if (!reminderId.isNullOrBlank()) {
            reminderRepository.getReminder(reminderId)?.let { reminder ->
                if (reminder.status == com.example.nanobot.core.model.ReminderStatus.SCHEDULED && reminder.triggerAt <= now) {
                    deliverReminder(reminder, now, sessionId)
                }
            }
            return Result.success()
        }

        val dueReminders = reminderRepository.getDueReminders(now)

        dueReminders.forEach { reminder ->
            deliverReminder(reminder, now, sessionId = null)
        }

        return Result.success()
    }

    private suspend fun deliverReminder(
        reminder: com.example.nanobot.core.model.Reminder,
        deliveredAt: Long,
        sessionId: String?
    ) {
        runCatching {
            reminderNotifier.notify(reminder)
            if (!sessionId.isNullOrBlank()) {
                sessionRepository.saveMessage(
                    ChatMessage(
                        sessionId = sessionId,
                        role = MessageRole.ASSISTANT,
                        content = "叮咚！提醒时间到了：${reminder.message}"
                    )
                )
            }
            reminderRepository.markDelivered(reminder.id, deliveredAt)
        }.onFailure { throwable ->
            reminderRepository.markFailed(
                reminder.id,
                throwable.message ?: "Unknown reminder delivery failure."
            )
        }
    }

    companion object {
        private const val KEY_REMINDER_ID = "reminder_id"
        private const val KEY_SESSION_ID = "session_id"

        fun buildInputData(reminderId: String, sessionId: String?): Data {
            return Data.Builder()
                .putString(KEY_REMINDER_ID, reminderId)
                .putString(KEY_SESSION_ID, sessionId)
                .build()
        }
    }
}
