package com.example.nanobot.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.nanobot.domain.repository.SessionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SessionCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionRepository: SessionRepository
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return runCatching {
            val cutoff = System.currentTimeMillis() - THIRTY_DAYS_MILLIS
            sessionRepository.deleteSessionsOlderThan(cutoff)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }

    private companion object {
        const val THIRTY_DAYS_MILLIS = 30L * 24L * 60L * 60L * 1000L
    }
}
