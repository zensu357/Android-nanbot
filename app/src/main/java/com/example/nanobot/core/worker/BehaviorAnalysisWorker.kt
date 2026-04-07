package com.example.nanobot.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.nanobot.core.learning.BehaviorAnalyzer
import com.example.nanobot.core.preferences.SettingsDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class BehaviorAnalysisWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val analyzer: BehaviorAnalyzer,
    private val settingsDataStore: SettingsDataStore
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return runCatching {
            val config = settingsDataStore.configFlow.first()
            if (!config.enableBehaviorLearning) {
                return@runCatching
            }
            analyzer.analyze()
            val retentionCutoff = System.currentTimeMillis() - RETENTION_WINDOW_MS
            analyzer.deleteOlderThan(retentionCutoff)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }

    private companion object {
        const val RETENTION_WINDOW_MS = 90L * 24 * 60 * 60 * 1000
    }
}
