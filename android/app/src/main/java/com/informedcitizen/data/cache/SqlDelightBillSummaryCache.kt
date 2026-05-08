package com.informedcitizen.data.cache

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.informedcitizen.cache.BillSummaryDatabase
import com.informedcitizen.data.ai.BillSummary
import com.informedcitizen.data.ai.BillTopic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlDelightBillSummaryCache(
    private val db: BillSummaryDatabase,
    private val modelVersion: String,
    private val promptVersion: Int,
) : BillSummaryCache {

    private val q = db.billSummaryQueries

    override fun observeAll(): Flow<Map<String, BillSummaryEntry>> =
        q.selectAllSummaries(modelVersion, promptVersion.toLong())
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.associate { row -> row.bill_id to row.toEntry() } }

    override suspend fun get(billId: String): BillSummaryEntry? = withContext(Dispatchers.IO) {
        q.selectSummary(billId, modelVersion, promptVersion.toLong())
            .executeAsOneOrNull()
            ?.toEntry()
    }

    override suspend fun putSuccess(
        billId: String,
        summary: BillSummary,
        generatedAtMillis: Long,
    ) {
        withContext(Dispatchers.IO) {
            q.upsertSummary(
                bill_id = billId,
                generated_title = summary.generatedTitle,
                topic = summary.topic.name,
                model_version = modelVersion,
                prompt_version = promptVersion.toLong(),
                generated_at = generatedAtMillis,
                error_kind = null,
            )
        }
    }

    override suspend fun putError(
        billId: String,
        errorKind: String,
        generatedAtMillis: Long,
    ) {
        withContext(Dispatchers.IO) {
            q.upsertSummary(
                bill_id = billId,
                generated_title = null,
                topic = null,
                model_version = modelVersion,
                prompt_version = promptVersion.toLong(),
                generated_at = generatedAtMillis,
                error_kind = errorKind,
            )
        }
    }

    override suspend fun delete(billId: String) {
        withContext(Dispatchers.IO) { q.deleteSummary(billId) }
    }

    override suspend fun clearAll() {
        withContext(Dispatchers.IO) { q.clearAllSummaries() }
    }

    override suspend fun enqueue(
        billId: String,
        priority: Int,
        bypassCap: Boolean,
        enqueuedAtMillis: Long,
    ) {
        withContext(Dispatchers.IO) {
            q.enqueuePending(
                bill_id = billId,
                priority = priority.toLong(),
                bypass_cap = if (bypassCap) 1L else 0L,
                enqueued_at = enqueuedAtMillis,
            )
        }
    }

    override suspend fun nextPending(): BillSummaryCache.PendingItem? = withContext(Dispatchers.IO) {
        q.selectNextPending().executeAsOneOrNull()?.let {
            BillSummaryCache.PendingItem(
                billId = it.bill_id,
                priority = it.priority.toInt(),
                bypassCap = it.bypass_cap == 1L,
                enqueuedAtMillis = it.enqueued_at,
            )
        }
    }

    override suspend fun dequeue(billId: String) {
        withContext(Dispatchers.IO) { q.dequeuePending(billId) }
    }

    override suspend fun queueDepth(): Long = withContext(Dispatchers.IO) {
        q.queueDepth().executeAsOne()
    }

    override suspend fun clearPending() {
        withContext(Dispatchers.IO) { q.clearPending() }
    }

    override suspend fun incrementAttemptsToday(localDateIso: String) {
        withContext(Dispatchers.IO) { q.incrementToday(localDateIso) }
    }

    override suspend fun attemptsToday(localDateIso: String): Long = withContext(Dispatchers.IO) {
        q.todayCount(localDateIso).executeAsOneOrNull() ?: 0L
    }

    private fun com.informedcitizen.cache.Bill_summary.toEntry(): BillSummaryEntry =
        BillSummaryEntry(
            billId = bill_id,
            summary = if (error_kind == null && generated_title != null && topic != null) {
                BillTopic.fromName(topic)?.let { BillSummary(generated_title, it) }
            } else null,
            errorKind = error_kind,
            generatedAtMillis = generated_at,
        )
}
