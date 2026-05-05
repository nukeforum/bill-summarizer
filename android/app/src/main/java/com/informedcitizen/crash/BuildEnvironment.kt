package com.informedcitizen.crash

/**
 * Whether the running build has the debuggable flag set in its
 * ApplicationInfo. Used to short-circuit Crashlytics off in dev builds
 * regardless of the user's opt-in toggle.
 */
@JvmInline
value class BuildEnvironment(val isDebuggable: Boolean)
