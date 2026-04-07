package com.example.nanobot.core.worker

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.Reminder
import com.example.nanobot.core.model.ReminderStatus
import com.example.nanobot.core.preferences.SettingsConfigStore
import com.example.nanobot.domain.repository.HeartbeatRepository
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NanobotWorkerSchedulerTest {
    @Test
    fun disablesHeartbeatWhenBackgroundWorkIsOff() = runTest {
        val backend = RecordingScheduleBackend()
        val scheduler = createScheduler(
            config = AgentConfig(enableBackgroundWork = false),
            heartbeatEnabled = true
        )

        scheduler.refreshScheduling(backend)

        assertTrue("heartbeat_work" in backend.cancelled)
        assertFalse(backend.enqueued.contains("heartbeat_work"))
    }

    @Test
    fun enqueuesHeartbeatWhenBackgroundWorkAndHeartbeatAreEnabled() = runTest {
        val backend = RecordingScheduleBackend()
        val scheduler = createScheduler(
            config = AgentConfig(enableBackgroundWork = true),
            heartbeatEnabled = true
        )

        scheduler.refreshScheduling(backend)

        assertTrue("heartbeat_work" in backend.enqueued)
    }

    @Test
    fun enqueuesBehaviorAnalysisWhenLearningIsEnabled() = runTest {
        val backend = RecordingScheduleBackend()
        val scheduler = createScheduler(
            config = AgentConfig(enableBehaviorLearning = true),
            heartbeatEnabled = true
        )

        scheduler.refreshScheduling(backend)

        assertTrue("behavior_analysis_work" in backend.enqueued)
        assertEquals(true, backend.periodicRequiresBatteryNotLow["behavior_analysis_work"])
    }

    @Test
    fun cancelsBehaviorAnalysisWhenLearningIsDisabled() = runTest {
        val backend = RecordingScheduleBackend()
        val scheduler = createScheduler(
            config = AgentConfig(enableBehaviorLearning = false),
            heartbeatEnabled = true
        )

        scheduler.refreshScheduling(backend)

        assertTrue("behavior_analysis_work" in backend.cancelled)
    }

    @Test
    fun schedulesOneTimeReminderWorkAtReminderTriggerTime() = runTest {
        val backend = RecordingScheduleBackend()
        val scheduler = createScheduler(
            config = AgentConfig(enableBackgroundWork = true),
            heartbeatEnabled = true
        )
        val now = 1_000L
        val reminder = Reminder(
            id = "reminder-1",
            title = null,
            message = "Ping",
            triggerAt = now + 60_000L,
            status = ReminderStatus.SCHEDULED,
            createdAt = now
        )

        scheduler.scheduleReminder(reminder, backend, sessionId = "session-1", now = now)

        assertTrue("reminder_once_reminder-1" in backend.enqueuedOneTime)
        assertEquals(60_000L, backend.oneTimeDelays["reminder_once_reminder-1"])
    }

    private fun createScheduler(
        config: AgentConfig,
        heartbeatEnabled: Boolean
    ): NanobotWorkerScheduler {
        return NanobotWorkerScheduler(
            context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            settingsDataStore = FakeSettingsConfigStore(config),
            heartbeatRepository = FakeHeartbeatRepository(heartbeatEnabled)
        )
    }

    private class RecordingScheduleBackend : WorkerScheduleBackend {
        val enqueued = mutableListOf<String>()
        val enqueuedOneTime = mutableListOf<String>()
        val oneTimeDelays = mutableMapOf<String, Long>()
        val periodicRequiresBatteryNotLow = mutableMapOf<String, Boolean>()
        val cancelled = mutableListOf<String>()

        override fun enqueueUniquePeriodicWork(
            uniqueName: String,
            policy: androidx.work.ExistingPeriodicWorkPolicy,
            request: androidx.work.PeriodicWorkRequest
        ) {
            enqueued += uniqueName
            periodicRequiresBatteryNotLow[uniqueName] = request.workSpec.constraints.requiresBatteryNotLow()
        }

        override fun enqueueUniqueWork(
            uniqueName: String,
            policy: androidx.work.ExistingWorkPolicy,
            request: androidx.work.OneTimeWorkRequest
        ) {
            enqueuedOneTime += uniqueName
            oneTimeDelays[uniqueName] = request.workSpec.initialDelay
        }

        override fun cancelUniqueWork(uniqueName: String) {
            cancelled += uniqueName
        }
    }

    private class FakeSettingsConfigStore(config: AgentConfig) : SettingsConfigStore {
        override val configFlow: Flow<AgentConfig> = MutableStateFlow(config)
        override val skillsDirectoryUriFlow: Flow<String?> = MutableStateFlow(null)
        override val skillRootsFlow: Flow<List<String>> = MutableStateFlow(emptyList())
        override val trustProjectSkillsFlow: Flow<Boolean> = MutableStateFlow(false)

        override suspend fun save(config: AgentConfig) = Unit

        override suspend fun saveSkillsDirectoryUri(uri: String?) = Unit

        override suspend fun addSkillRootUri(uri: String) = Unit

        override suspend fun removeSkillRootUri(uri: String) = Unit

        override suspend fun setTrustProjectSkills(trusted: Boolean) = Unit
    }

    private class FakeHeartbeatRepository(enabled: Boolean) : HeartbeatRepository {
        private val enabledFlow = MutableStateFlow(enabled)

        override fun observeHeartbeatInstructions(): Flow<String> = MutableStateFlow("")

        override fun observeHeartbeatEnabled(): Flow<Boolean> = enabledFlow

        override suspend fun getHeartbeatInstructions(): String = ""

        override suspend fun isHeartbeatEnabled(): Boolean = enabledFlow.value

        override suspend fun setHeartbeatInstructions(value: String) = Unit

        override suspend fun setHeartbeatEnabled(value: Boolean) {
            enabledFlow.value = value
        }
    }
}
