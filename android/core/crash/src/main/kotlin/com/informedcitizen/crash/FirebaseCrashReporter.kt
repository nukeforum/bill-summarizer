package com.informedcitizen.crash

import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseCrashReporter @Inject constructor(
    private val buildEnvironment: BuildEnvironment,
) : CrashReporter {

    // Debug builds short-circuit before touching FirebaseCrashlytics. Two
    // reasons: (1) Crashlytics is force-off in debug regardless of opt-in,
    // so dev crashes don't pollute the production dashboard; (2) the
    // .debug applicationId is not registered in google-services.json, so
    // FirebaseCrashlytics.getInstance() would throw
    // IllegalStateException("Default FirebaseApp is not initialized…")
    // and crash the app at Application.onCreate.
    override fun recordNonFatal(throwable: Throwable, message: String?) {
        if (buildEnvironment.isDebuggable) return
        val crashlytics = FirebaseCrashlytics.getInstance()
        if (message != null) {
            crashlytics.log(message)
        }
        crashlytics.recordException(throwable)
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        if (buildEnvironment.isDebuggable) return
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = enabled
    }

    override fun deleteUnsentReports() {
        if (buildEnvironment.isDebuggable) return
        FirebaseCrashlytics.getInstance().deleteUnsentReports()
    }
}
