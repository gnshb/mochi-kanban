package com.mochikanban.app.reminders

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mochikanban.app.data.repo.CardRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Clears the Done column once a day (scheduled for ~midnight). */
@HiltWorker
class MidnightCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: CardRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        runCatching { repo.clearDone() }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "midnight-cleanup"
    }
}
