package com.informedcitizen

import android.app.Application
import com.informedcitizen.crash.CrashReporter
import com.informedcitizen.data.repository.CrashReportingPreferenceRepository
import com.informedcitizen.data.repository.SessionCalendarRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import javax.inject.Inject

@HiltAndroidApp
class InformedCitizenApp : Application() {

    @Inject lateinit var crashReporter: CrashReporter
    @Inject lateinit var crashPrefs: CrashReportingPreferenceRepository
    @Inject lateinit var sessionCalendarRepository: SessionCalendarRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // One-shot synchronous read of the persisted opt-in flag at startup.
        // Single small DataStore read, on the main thread; cost is dominated
        // by Hilt graph construction already happening in onCreate. The
        // FirebaseCrashReporter impl force-offs collection in debug builds,
        // so the value here is honoured only in release.
        val enabled = runBlocking { crashPrefs.enabled.first() }
        crashReporter.setCollectionEnabled(enabled)

        appScope.launch { reportCalendarExhaustionIfNearEnd() }
    }

    private suspend fun reportCalendarExhaustionIfNearEnd() {
        val calendar = sessionCalendarRepository.getCalendar().getOrNull() ?: return
        val today = LocalDate.now()
        val warnThreshold = today.plusDays(30)
        val needsRefresh = listOf("house", "senate").any { key ->
            val days = calendar.chambers[key]?.sessionDays.orEmpty()
                .map(LocalDate::parse)
            val last = days.maxOrNull()
            last != null && last < warnThreshold
        }
        if (needsRefresh) {
            crashReporter.recordNonFatal(
                IllegalStateException("session calendar nearing exhaustion"),
                "session calendar within 30 days of last known session day",
            )
        }
    }
}
