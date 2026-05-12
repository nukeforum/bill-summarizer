package com.informedcitizen.crash

import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseCrashReporter @Inject constructor(
    private val buildEnvironment: BuildEnvironment,
) : CrashReporter {

    override fun recordNonFatal(throwable: Throwable, message: String?) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        if (message != null) {
            crashlytics.log(message)
        }
        crashlytics.recordException(throwable)
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        // Force-off in debug builds regardless of the opt-in flag, so dev
        // crashes don't pollute the production Crashlytics dashboard.
        val effective = enabled && !buildEnvironment.isDebuggable
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = effective
    }

    override fun deleteUnsentReports() {
        FirebaseCrashlytics.getInstance().deleteUnsentReports()
    }
}
