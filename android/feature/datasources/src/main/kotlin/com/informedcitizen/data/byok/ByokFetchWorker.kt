package com.informedcitizen.data.byok

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Daily BYOK refresh tick. Each run re-checks which artifacts are due
 * per [ByokFetchTracker] cadences (bills daily; members and calendar
 * weekly) and fetches only those. No-ops instantly when no key is
 * configured, so a stale enqueued worker after key removal is
 * harmless.
 *
 * Plain (non-foreground) worker on purpose: the fetches take a couple
 * of minutes at most, well inside WorkManager's 10-minute window, and
 * a foreground service would re-open the Play permission-disclosure
 * surface the AI-titles feature deliberately vacated.
 */
@HiltWorker
class ByokFetchWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val keyStore: ByokKeyStore,
    private val orchestrator: ByokFetchOrchestrator,
    private val tracker: ByokFetchTracker,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        keyStore.congressApiKey.firstOrNull() ?: return Result.success()

        val now = System.currentTimeMillis()
        var anyFailed = false
        for (artifact in ByokArtifact.entries) {
            if (!tracker.isDue(artifact, now)) continue
            val result = when (artifact) {
                ByokArtifact.BILLS -> orchestrator.fetchBills()
                ByokArtifact.MEMBERS -> orchestrator.fetchMembersIndex()
                ByokArtifact.CALENDAR -> orchestrator.fetchCalendar()
            }
            if (result.isSuccess) {
                tracker.recordSuccess(artifact, now)
            } else {
                anyFailed = true
            }
        }
        // Retry lets a transient failure (API hiccup, network drop)
        // re-run with backoff instead of waiting a full day.
        return if (anyFailed) Result.retry() else Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "byok-fetch"
    }
}

/** Enqueues / cancels the daily BYOK tick as the key appears / disappears. */
@Singleton
class ByokFetchScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun ensureScheduled() {
        val request = PeriodicWorkRequestBuilder<ByokFetchWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ByokFetchWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(ByokFetchWorker.UNIQUE_WORK_NAME)
    }
}
