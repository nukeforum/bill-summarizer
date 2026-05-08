package com.informedcitizen.data.cache

import com.informedcitizen.data.ai.BillSummary
import kotlinx.coroutines.flow.Flow

data class BillSummaryEntry(
    val billId: String,
    val summary: BillSummary?,
    val errorKind: String?,
    val generatedAtMillis: Long,
)

interface BillSummaryCache {
    fun observeAll(): Flow<Map<String, BillSummaryEntry>>

    suspend fun get(billId: String): BillSummaryEntry?

    suspend fun putSuccess(billId: String, summary: BillSummary, generatedAtMillis: Long)
    suspend fun putError(billId: String, errorKind: String, generatedAtMillis: Long)

    suspend fun delete(billId: String)
    suspend fun clearAll()

    suspend fun enqueue(billId: String, priority: Int, bypassCap: Boolean, enqueuedAtMillis: Long)
    suspend fun nextPending(): PendingItem?
    suspend fun dequeue(billId: String)
    suspend fun queueDepth(): Long
    suspend fun clearPending()

    suspend fun incrementAttemptsToday(localDateIso: String)
    suspend fun attemptsToday(localDateIso: String): Long

    data class PendingItem(
        val billId: String,
        val priority: Int,
        val bypassCap: Boolean,
        val enqueuedAtMillis: Long,
    )
}
