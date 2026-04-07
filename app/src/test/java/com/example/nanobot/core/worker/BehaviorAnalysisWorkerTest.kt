package com.example.nanobot.core.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.example.nanobot.core.ai.FakeBehaviorEventDao
import com.example.nanobot.core.database.entity.BehaviorEventEntity
import com.example.nanobot.core.learning.BehaviorAnalyzer
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.preferences.SettingsDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BehaviorAnalysisWorkerTest {
    @Test
    fun skipsAnalysisWhenBehaviorLearningIsDisabled() = runTest {
        val settings = SettingsDataStore(appContext())
        settings.save(AgentConfig(enableBehaviorLearning = false))
        val dao = FakeBehaviorEventDao(
            initialEvents = listOf(
                BehaviorEventEntity(
                    id = 1,
                    type = "TOOL_USAGE",
                    key = "device_time",
                    sessionId = "session-1",
                    metadata = "{}",
                    timestamp = System.currentTimeMillis()
                )
            )
        )
        val worker = buildWorker(BehaviorAnalyzer(dao), settings)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, dao.allEvents().size)
    }

    @Test
    fun deletesEventsOlderThanRetentionWindowWhenLearningIsEnabled() = runTest {
        val settings = SettingsDataStore(appContext())
        settings.save(AgentConfig(enableBehaviorLearning = true))
        val now = System.currentTimeMillis()
        val dao = FakeBehaviorEventDao(
            initialEvents = listOf(
                BehaviorEventEntity(
                    id = 1,
                    type = "TOOL_USAGE",
                    key = "old_tool",
                    sessionId = "session-1",
                    metadata = "{}",
                    timestamp = now - 91L * 24 * 60 * 60 * 1000
                ),
                BehaviorEventEntity(
                    id = 2,
                    type = "TOOL_USAGE",
                    key = "new_tool",
                    sessionId = "session-1",
                    metadata = "{}",
                    timestamp = now - 1_000L
                )
            )
        )
        val worker = buildWorker(BehaviorAnalyzer(dao), settings)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(listOf("new_tool"), dao.allEvents().map { it.key })
    }

    private fun buildWorker(
        analyzer: BehaviorAnalyzer,
        settings: SettingsDataStore
    ): BehaviorAnalysisWorker {
        val workerFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker {
                return BehaviorAnalysisWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    analyzer = analyzer,
                    settingsDataStore = settings
                )
            }
        }
        return androidx.work.testing.TestListenableWorkerBuilder<BehaviorAnalysisWorker>(appContext())
            .setWorkerFactory(workerFactory)
            .build()
    }

    private fun appContext(): Context = ApplicationProvider.getApplicationContext()
}
