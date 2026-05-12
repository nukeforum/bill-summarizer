package com.informedcitizen.data.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.informedcitizen.data.ai.BillSummarizer
import com.informedcitizen.data.cache.BillSummaryCache
import com.informedcitizen.data.repository.AiTitlesPreferenceRepository
import com.informedcitizen.data.repository.BillRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate

@HiltWorker
class BillSummarizationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val cache: BillSummaryCache,
    private val billRepository: BillRepository,
    private val summarizer: BillSummarizer,
    private val prefs: AiTitlesPreferenceRepository,
    private val clock: Clock,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!prefs.enabled.first()) return Result.success()

        setForeground(makeForeground(currentIndex = 0, total = 0, indeterminate = true))
        val total = cache.queueDepth().toInt()
        if (total == 0) return Result.success()

        val today = LocalDate.now(clock).toString()
        val scope = prefs.scope.first()
        val capPerDay: Int? = (scope as? SummarizationScope.Progressive)?.capPerDay

        var processed = 0
        while (true) {
            if (!prefs.enabled.first()) break
            val next = cache.nextPending() ?: break
            if (capPerDay != null && !next.bypassCap &&
                cache.attemptsToday(today) >= capPerDay
            ) break

            val bill = billRepository.findById(next.billId)
            if (bill == null) {
                cache.dequeue(next.billId)
                continue
            }

            cache.incrementAttemptsToday(today)
            when (val result = summarizer.summarize(bill)) {
                is BillSummarizer.Result.Success ->
                    cache.putSuccess(bill.id, result.summary, clock.millis())
                is BillSummarizer.Result.Failure ->
                    cache.putError(bill.id, result.errorKind, clock.millis())
            }
            cache.dequeue(bill.id)

            processed += 1
            setForeground(makeForeground(processed, total, indeterminate = false))
        }
        return Result.success()
    }

    // Manifest entries for dataSync foreground service are removed while
    // FeatureFlags.AI_TITLES is off; worker is unreachable at runtime.
    @android.annotation.SuppressLint("SpecifyForegroundServiceType")
    private fun makeForeground(
        currentIndex: Int,
        total: Int,
        indeterminate: Boolean,
    ): ForegroundInfo {
        val notification = SummarizationNotifications.build(
            context = applicationContext,
            currentIndex = currentIndex,
            total = total,
            indeterminate = indeterminate,
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                SummarizationNotifications.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(SummarizationNotifications.NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val UNIQUE_NAME = BILL_SUMMARIZATION_WORK_NAME
    }
}
