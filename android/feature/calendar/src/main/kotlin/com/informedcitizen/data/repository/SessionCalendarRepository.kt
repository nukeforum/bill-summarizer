package com.informedcitizen.data.repository

import com.informedcitizen.crash.CrashReporter
import com.informedcitizen.data.api.BillsApi
import com.informedcitizen.pipeline.model.SessionCalendar
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionCalendarRepository @Inject constructor(
    private val api: BillsApi,
    private val crashReporter: CrashReporter,
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
            fetched
        }.onFailure { crashReporter.recordNonFatal(it, "session calendar fetch failed") }
    }
}
