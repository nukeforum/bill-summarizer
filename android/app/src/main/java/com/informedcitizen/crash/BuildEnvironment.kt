package com.informedcitizen.crash

/**
 * Whether the running build has the debuggable flag set in its
 * ApplicationInfo. Used to short-circuit Crashlytics off in dev builds
 * regardless of the user's opt-in toggle.
 *
 * Modelled as a data class (not a Kotlin inline value class) because
 * Dagger/Hilt's KSP processor cannot generate factories for inline
 * value classes returned from @Provides functions.
 */
data class BuildEnvironment(val isDebuggable: Boolean)
