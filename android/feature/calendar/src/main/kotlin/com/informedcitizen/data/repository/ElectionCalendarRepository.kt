package com.informedcitizen.data.repository

import com.informedcitizen.crash.CrashReporter
import com.informedcitizen.data.api.BillsApi
import com.informedcitizen.data.cache.BillSource
import com.informedcitizen.data.cache.ElectionCalendarCache
import com.informedcitizen.pipeline.model.ElectionCalendar
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads `docs/data/election_calendar.json` (issue #24) the same way
 * [SessionCalendarRepository] reads the session calendar: fetch once,
 * memoize, write through to a persistent cache, and fall back to the
 * freshest persisted copy when offline so the upcoming-elections surface
 * still answers "when will I vote" without a network.
 */
@Singleton
class ElectionCalendarRepository @Inject constructor(
    private val api: BillsApi,
    private val crashReporter: CrashReporter,
    private val persistentCache: ElectionCalendarCache,
) {
    private val mutex = Mutex()
    private var cached: ElectionCalendar? = null

    suspend fun getCalendar(forceRefresh: Boolean = false): Result<ElectionCalendar> = mutex.withLock {
        if (!forceRefresh) {
            cached?.let { return@withLock Result.success(it) }
        }
        runCatching {
            val fetched = api.getElectionCalendar()
            cached = fetched
            writeThroughCache(fetched)
            fetched
        }.recoverCatching { networkError ->
            // Offline fallback: freshest persisted calendar (either
            // source). The failure is still reported as a non-fatal.
            crashReporter.recordNonFatal(networkError, "election calendar fetch failed")
            val fallback = persistentCache.loadFreshest()?.value ?: throw networkError
            cached = fallback
            fallback
        }
    }

    private suspend fun writeThroughCache(calendar: ElectionCalendar) {
        runCatching {
            persistentCache.replaceForSource(
                source = BillSource.PUBLISHED,
                calendar = calendar,
                fetchedAtMillis = System.currentTimeMillis(),
            )
        }.onFailure { crashReporter.recordNonFatal(it, "election calendar cache write-through failed") }
    }

    /**
     * Accept a calendar the in-app BYOK pipeline just produced:
     * replace the in-memory value the UI reads and persist under
     * [BillSource.BYOK]. Last-write-wins with the published path.
     */
    suspend fun publishByokCalendar(calendar: ElectionCalendar) {
        mutex.withLock { cached = calendar }
        runCatching {
            persistentCache.replaceForSource(
                source = BillSource.BYOK,
                calendar = calendar,
                fetchedAtMillis = System.currentTimeMillis(),
            )
        }.onFailure { crashReporter.recordNonFatal(it, "byok election calendar cache write failed") }
    }
}
