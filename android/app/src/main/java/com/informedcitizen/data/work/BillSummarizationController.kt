package com.informedcitizen.data.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.informedcitizen.data.cache.BillSummaryCache
import com.informedcitizen.data.repository.AiTitlesPreferenceRepository
import com.informedcitizen.data.repository.BillRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillSummarizationController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prefs: AiTitlesPreferenceRepository,
    private val cache: BillSummaryCache,
    private val billRepository: BillRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        prefs.enabled
            .distinctUntilChanged()
            .onEach { enabled ->
                if (!enabled) {
                    WorkManager.getInstance(context)
                        .cancelUniqueWork(BillSummarizationWorker.UNIQUE_NAME)
                }
            }
            .launchIn(scope)

        combine(prefs.enabled, prefs.scope, billRepository.observeAll()) { enabled, scope, bills ->
            Triple(enabled, scope, bills)
        }
            .onEach { (enabled, currentScope, bills) ->
                if (!enabled) return@onEach
                val cached = cache.observeAll().first().keys
                val policy = BillSummarizationPolicy(currentScope, LocalDate.now())
                val toEnqueue = policy.selectToEnqueue(bills, cached)
                if (toEnqueue.isEmpty()) return@onEach
                val now = System.currentTimeMillis()
                toEnqueue.forEach { bill ->
                    cache.enqueue(bill.id, priority = 0, bypassCap = false, enqueuedAtMillis = now)
                }
                triggerWorker()
            }
            .launchIn(scope)
    }

    fun retry(billId: String) {
        scope.launch {
            cache.delete(billId)
            cache.enqueue(billId, priority = 100, bypassCap = true, enqueuedAtMillis = System.currentTimeMillis())
            triggerWorker()
        }
    }

    fun stopNow() {
        WorkManager.getInstance(context).cancelUniqueWork(BillSummarizationWorker.UNIQUE_NAME)
    }

    fun clearCache() {
        scope.launch {
            cache.clearAll()
            cache.clearPending()
            val bills = billRepository.observeAll().first()
            val currentScope = prefs.scope.first()
            val policy = BillSummarizationPolicy(currentScope, LocalDate.now())
            val now = System.currentTimeMillis()
            policy.selectToEnqueue(bills, emptySet()).forEach { bill ->
                cache.enqueue(bill.id, priority = 0, bypassCap = false, enqueuedAtMillis = now)
            }
            if (prefs.enabled.first()) triggerWorker()
        }
    }

    private fun triggerWorker() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            BillSummarizationWorker.UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<BillSummarizationWorker>().build(),
        )
    }
}
