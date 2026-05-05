package com.informedcitizen.crash

/**
 * App-facing seam for crash + non-fatal reporting. The production binding
 * forwards to FirebaseCrashlytics; tests use a FakeCrashReporter.
 *
 * Calls are safe to make unconditionally — when the user has not opted in,
 * Firebase's own collection-enabled flag drops them on the floor.
 */
interface CrashReporter {
    /**
     * Record a non-fatal Throwable. The optional message is attached as a
     * Crashlytics log entry (visible in the report's "Logs" tab) so the
     * report contains a human-readable hint about what was happening.
     */
    fun recordNonFatal(throwable: Throwable, message: String? = null)
}
