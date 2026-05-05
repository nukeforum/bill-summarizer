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

    /**
     * Apply the user's opt-in flag to the underlying SDK. Implementations
     * are free to also force-off in debug builds regardless of [enabled].
     */
    fun setCollectionEnabled(enabled: Boolean)

    /**
     * Request deletion of any reports queued on disk that have not yet
     * been uploaded. Called when the user opts out, so reports captured
     * while opted-in are dropped rather than uploaded later.
     */
    fun deleteUnsentReports()
}
