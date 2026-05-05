package com.informedcitizen

import android.app.Application
import com.informedcitizen.crash.CrashReporter
import com.informedcitizen.data.repository.CrashReportingPreferenceRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class InformedCitizenApp : Application() {

    @Inject lateinit var crashReporter: CrashReporter
    @Inject lateinit var crashPrefs: CrashReportingPreferenceRepository

    override fun onCreate() {
        super.onCreate()
        // One-shot synchronous read of the persisted opt-in flag at startup.
        // Single small DataStore read, on the main thread; cost is dominated
        // by Hilt graph construction already happening in onCreate. The
        // FirebaseCrashReporter impl force-offs collection in debug builds,
        // so the value here is honoured only in release.
        val enabled = runBlocking { crashPrefs.enabled.first() }
        crashReporter.setCollectionEnabled(enabled)
    }
}
