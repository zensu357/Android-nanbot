package com.example.nanobot.core.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.example.nanobot.core.model.Reminder
import com.example.nanobot.core.preferences.SettingsConfigStore
import com.example.nanobot.domain.repository.HeartbeatRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

interface WorkerSchedulingController {
    suspend fun refreshScheduling()
}

interface ReminderWorkScheduler {
    suspend fun scheduleReminder(reminder: Reminder, sessionId: String?)
}

interface WorkerScheduleBackend {
    fun enqueueUniquePeriodicWork(
        uniqueName: String,
        policy: ExistingPeriodicWorkPolicy,
        request: PeriodicWorkRequest
    )

    fun enqueueUniqueWork(
        uniqueName: String,
        policy: ExistingWorkPolicy,
        request: OneTimeWorkRequest
    )

    fun cancelUniqueWork(uniqueName: String)
}

class WorkManagerScheduleBackend(
    private val workManager: WorkManager
) : WorkerScheduleBackend {
    override fun enqueueUniquePeriodicWork(
        uniqueName: String,
        policy: ExistingPeriodicWorkPolicy,
        request: PeriodicWorkRequest
    ) {
        workManager.enqueueUniquePeriodicWork(uniqueName, policy, request)
    }

    override fun enqueueUniqueWork(
        uniqueName: String,
        policy: ExistingWorkPolicy,
        request: OneTimeWorkRequest
    ) {
        workManager.enqueueUniqueWork(uniqueName, policy, request)
    }

    override fun cancelUniqueWork(uniqueName: String) {
        workManager.cancelUniqueWork(uniqueName)
    }
}

@Singleton
class NanobotWorkerScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsConfigStore,
    private val heartbeatRepository: HeartbeatRepository
) : WorkerSchedulingController, ReminderWorkScheduler {
    fun scheduleRecurringWork() {
        runBlocking {
            refreshScheduling()
        }
    }

    override suspend fun refreshScheduling() {
        refreshScheduling(WorkManagerScheduleBackend(WorkManager.getInstance(context)))
    }

    override suspend fun scheduleReminder(reminder: Reminder, sessionId: String?) {
        scheduleReminder(
            reminder = reminder,
            backend = WorkManagerScheduleBackend(WorkManager.getInstance(context)),
            sessionId = sessionId,
            now = System.currentTimeMillis()
        )
    }

    suspend fun refreshScheduling(backend: WorkerScheduleBackend) {
        val config = runCatching {
            settingsDataStore.configFlow.first()
        }.getOrNull()
        val heartbeatEnabled = runCatching {
            heartbeatRepository.isHeartbeatEnabled()
        }.getOrDefault(true)

        val memoryWork = PeriodicWorkRequestBuilder<MemoryConsolidationWorker>(6, TimeUnit.HOURS)
            .build()
        val heartbeatWork = PeriodicWorkRequestBuilder<HeartbeatWorker>(1, TimeUnit.HOURS)
            .build()
        val reminderWork = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES)
            .build()
        val cleanupWork = PeriodicWorkRequestBuilder<SessionCleanupWorker>(1, TimeUnit.DAYS)
            .build()
        val behaviorAnalysisWork = PeriodicWorkRequestBuilder<BehaviorAnalysisWorker>(24, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        backend.enqueueUniquePeriodicWork(
            MEMORY_CONSOLIDATION_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            memoryWork
        )
        if (config?.enableBackgroundWork == true && heartbeatEnabled) {
            backend.enqueueUniquePeriodicWork(
                HEARTBEAT_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                heartbeatWork
            )
        } else {
            backend.cancelUniqueWork(HEARTBEAT_WORK)
        }
        backend.enqueueUniquePeriodicWork(
            REMINDER_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            reminderWork
        )
        backend.enqueueUniquePeriodicWork(
            SESSION_CLEANUP_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            cleanupWork
        )
        if (config?.enableBehaviorLearning == true) {
            backend.enqueueUniquePeriodicWork(
                BEHAVIOR_ANALYSIS_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                behaviorAnalysisWork
            )
        } else {
            backend.cancelUniqueWork(BEHAVIOR_ANALYSIS_WORK)
        }
    }

    suspend fun scheduleReminder(
        reminder: Reminder,
        backend: WorkerScheduleBackend,
        sessionId: String?,
        now: Long = System.currentTimeMillis()
    ) {
        val initialDelayMillis = (reminder.triggerAt - now).coerceAtLeast(0L)
        val reminderWork = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(ReminderWorker.buildInputData(reminder.id, sessionId))
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .build()
        backend.enqueueUniqueWork(
            reminderUniqueWorkName(reminder.id),
            ExistingWorkPolicy.REPLACE,
            reminderWork
        )
    }

    private companion object {
        const val MEMORY_CONSOLIDATION_WORK = "memory_consolidation_work"
        const val HEARTBEAT_WORK = "heartbeat_work"
        const val REMINDER_WORK = "reminder_work"
        const val SESSION_CLEANUP_WORK = "session_cleanup_work"
        const val BEHAVIOR_ANALYSIS_WORK = "behavior_analysis_work"

        fun reminderUniqueWorkName(reminderId: String): String = "reminder_once_$reminderId"
    }
}
