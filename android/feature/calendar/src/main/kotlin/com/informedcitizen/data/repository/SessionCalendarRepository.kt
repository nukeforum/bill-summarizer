package com.informedcitizen.data.repository

import com.informedcitizen.crash.CrashReporter
import com.informedcitizen.data.api.BillsApi
import com.informedcitizen.data.cache.BillSource
import com.informedcitizen.data.cache.SessionCalendarCache
import com.informedcitizen.pipeline.model.SessionCalendar
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionCalendarRepository @Inject constructor(
    private val api: BillsApi,
    private val crashReporter: CrashReporter,
    private val persistentCache: SessionCalendarCache,
) {
    private val mutex = Mutex()
    private var cached: SessionCalendar? = null

    suspend fun getCalendar(forceRefresh: Boolean = false): Result<SessionCalendar> = mutex.withLock {
        if (!forceRefresh) {
            cached?.let { return@withLock Result.success(it) }
        }
        runCatching {
            val fetched = api.getSessionCalendar()
            cached = fetched
            writeThroughCache(fetched)
            fetched
        }.recoverCatching { networkError ->
            // Offline fallback: freshest persisted calendar (either
            // source). The failure is still reported as a non-fatal.
            crashReporter.recordNonFatal(networkError, "session calendar fetch failed")
            val fallback = persistentCache.loadFreshest()?.value ?: throw networkError
            cached = fallback
            fallback
        }
    }

    private suspend fun writeThroughCache(calendar: SessionCalendar) {
        runCatching {
            persistentCache.replaceForSource(
                source = BillSource.PUBLISHED,
                calendar = calendar,
                fetchedAtMillis = System.currentTimeMillis(),
            )
        }.onFailure { crashReporter.recordNonFatal(it, "calendar cache write-through failed") }
    }

    /**
     * Accept a calendar the in-app BYOK pipeline just produced:
     * replace the in-memory value the UI reads and persist under
     * [BillSource.BYOK]. Last-write-wins with the published path.
     */
    suspend fun publishByokCalendar(calendar: SessionCalendar) {
        mutex.withLock { cached = calendar }
        runCatching {
            persistentCache.replaceForSource(
                source = BillSource.BYOK,
                calendar = calendar,
                fetchedAtMillis = System.currentTimeMillis(),
            )
        }.onFailure { crashReporter.recordNonFatal(it, "byok calendar cache write failed") }
    }
}
