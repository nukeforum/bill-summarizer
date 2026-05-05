package com.informedcitizen

import android.app.Application
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.informedcitizen.crash.BuildEnvironment
import com.informedcitizen.data.repository.CrashReportingPreferenceRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class InformedCitizenApp : Application() {

    @Inject lateinit var buildEnvironment: BuildEnvironment
    @Inject lateinit var crashPrefs: CrashReportingPreferenceRepository

    override fun onCreate() {
        super.onCreate()
        applyCrashlyticsCollectionFlag()
    }

    private fun applyCrashlyticsCollectionFlag() {
        val crashlytics = FirebaseCrashlytics.getInstance()
        if (buildEnvironment.isDebuggable) {
            crashlytics.isCrashlyticsCollectionEnabled = false
            return
        }
        // One-shot synchronous read of the persisted opt-in flag at startup.
        // Single small DataStore read, identical to patterns in Google's own
        // DataStore samples; cost is dominated by Hilt graph construction
        // already happening in onCreate.
        val enabled = runBlocking { crashPrefs.enabled.first() }
        crashlytics.isCrashlyticsCollectionEnabled = enabled
    }
}
