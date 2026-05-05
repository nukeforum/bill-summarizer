# Crashlytics opt-in — design

Date: 2026-05-05
Status: approved, ready for implementation plan

## Goal

Add Firebase Crashlytics to Informed Citizen as an **opt-in** crash + non-fatal
reporter that defaults to off, exposed by a Settings toggle. Preserves the
spirit of the current privacy policy (no surprise telemetry) while giving
users who want to help a one-tap way to do so.

## Why opt-in, default off

The published privacy policy
(`docs/privacy.html`, last updated 2026-05-04) makes three claims that
conventional always-on Crashlytics would break:

- "no analytics, no advertising, and no third-party tracking"
- "No analytics or telemetry SDKs"
- "No crash reporting that leaves the device"

A Settings-only toggle, default off, is the smallest deviation that still
gives a usable signal from users who choose to help. No first-run prompt
(would undercut the "we don't ask for things we don't need" tone), no
nudge banner.

## User-facing behavior

1. Out of the box, no crash data leaves the device. Behaves identically to
   today.
2. User opens Settings → sees a "Crash reporting" section below "Theme" with
   a single switch ("Send crash reports", default off) and a one-line
   explanation of what gets sent.
3. Flipping the switch on enables Crashlytics immediately (no restart). From
   that point on, uncaught exceptions and recorded non-fatals are uploaded
   on next app launch with a network connection.
4. Flipping the switch off disables Crashlytics and requests deletion of any
   unsent local reports.
5. Debug builds always behave as if the switch is off — flag is honored only
   on release builds.

## What gets reported (when enabled)

- **Crashes**: every uncaught exception (the Crashlytics default).
- **Non-fatals**: explicit `recordNonFatal(throwable, message)` calls at two
  catch points where today's code silently swallows network failures into a
  `Result.failure`:
  - `BillRepository.getBills` — manifest fetch failed.
  - `BillTextFetcher.fetchPlainText` — full-text fetch failed.
- The two `LlmShareHelper` catches (`ActivityNotFoundException`,
  `PackageManager.NameNotFoundException`) are expected control flow when
  probing for installed LLM apps; **not** reported.

What's in each report (Firebase auto-collects): stack trace, device model,
OS version, app version code/name, locale, orientation, free/total RAM &
disk, and a Crashlytics-generated installation UUID. No PII, no account ID
(the app has no accounts), no bill content, no breadcrumbs (we don't call
`Crashlytics.log()`), no custom keys.

## Architecture

Three new pieces, plus minimal Settings UI additions:

### `CrashReportingPreferenceRepository`

Mirror of `ThemePreferenceRepository`. DataStore-backed `Boolean`, default
`false`. Lives in `data.repository` package.

```kotlin
@Singleton
class CrashReportingPreferenceRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val enabled: Flow<Boolean> = dataStore.data
        .map { it[KEY] ?: false }
        .catch { emit(false) }

    suspend fun set(enabled: Boolean) {
        dataStore.edit { it[KEY] = enabled }
    }

    private companion object {
        val KEY = booleanPreferencesKey("crash_reporting_enabled")
    }
}
```

### `CrashReporter` interface + `FirebaseCrashReporter` impl

In a new `com.informedcitizen.crash` package:

```kotlin
interface CrashReporter {
    fun recordNonFatal(throwable: Throwable, message: String? = null)
}
```

`FirebaseCrashReporter` calls `FirebaseCrashlytics.getInstance().recordException`
(prefixed with an optional `log(message)` to attach context to that report).
A no-op fake (`NoOpCrashReporter` or test double) is used in unit tests.

Hilt `CrashModule` binds `CrashReporter` → `FirebaseCrashReporter` as
`@Singleton`.

### Startup wiring in `InformedCitizenApp.onCreate()`

1. Compute `isDebug = (applicationInfo.flags and FLAG_DEBUGGABLE) != 0`.
   Avoids needing `BuildConfig` (currently disabled via
   `buildFeatures.buildConfig = false`).
2. If `isDebug`, force `isCrashlyticsCollectionEnabled = false` and return.
3. Otherwise read the persisted opt-in flag once via
   `runBlocking { repo.enabled.first() }` (single small DataStore read, on
   the main thread, identical pattern Google's own DataStore samples use for
   one-shot startup config) and set
   `FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled` to it.

The `firebase_crashlytics_collection_enabled` `<meta-data>` in the manifest
is set to `false`, so any race window before our code runs is safe.

### Settings UI

`SettingsScreen` gains a second section below the theme picker:

```
Crash reporting
[Send crash reports]                              [ switch ]
Off by default. When on, anonymous crash data
(stack traces, device model, OS, app version) is
sent to Google Firebase to help fix bugs.
```

`SettingsViewModel` already exposes a theme `StateFlow`; it picks up a second
`StateFlow<Boolean>` for crash reporting and a
`setCrashReportingEnabled(Boolean)` method.

The setter both persists to DataStore and applies the change to the live
Crashlytics instance:

```kotlin
fun setCrashReportingEnabled(enabled: Boolean) {
    viewModelScope.launch {
        repo.set(enabled)
        if (!isDebug) {
            FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = enabled
            if (!enabled) FirebaseCrashlytics.getInstance().deleteUnsentReports()
        }
    }
}
```

The `isDebug` flag is provided by an injected `BuildEnvironment` value (or
`@ApplicationContext` + `applicationInfo` check) so the ViewModel stays
testable.

## Gradle

`gradle/libs.versions.toml`:

- New `[versions]`: `firebaseBom`, `googleServices`, `firebaseCrashlyticsPlugin`
  (pin to actual latest stable at implementation time; targets in plan stage:
  Firebase BoM ≥ 33, google-services ≥ 4.4, Crashlytics plugin ≥ 3.0).
- New `[libraries]`: `firebase-bom` (`com.google.firebase:firebase-bom`,
  platform), `firebase-crashlytics` (`com.google.firebase:firebase-crashlytics`,
  no version — BoM-managed). **No** `firebase-analytics`.
- New `[plugins]`: `google-services` (`com.google.gms.google-services`),
  `firebase-crashlytics` (`com.google.firebase.crashlytics`).

`android/build.gradle.kts`: add both plugins with `apply false`.

`android/app/build.gradle.kts`:

- Apply `google-services` and `firebase-crashlytics` plugins.
- Add `implementation(platform(libs.firebase.bom))` and
  `implementation(libs.firebase.crashlytics)`.
- Defaults are sufficient for mapping-file upload behavior — release builds
  already have `isMinifyEnabled = true`, so the Crashlytics plugin will
  upload mappings; debug builds aren't minified, so mapping upload is a
  no-op there.

## Manifest

In `AndroidManifest.xml` inside `<application>`:

```xml
<meta-data
    android:name="firebase_crashlytics_collection_enabled"
    android:value="false" />
```

Default-disabled at the SDK level; only enabled programmatically when (a)
the build is non-debug and (b) the user has opted in.

## `google-services.json`

The file is currently in `android/app/google-services.json` but untracked.
Commit it. The repo is open-source, the privacy policy already invites users
to verify behavior in source, and the API key inside is bundled in every
APK and restricted server-side by package name + signing fingerprint — it's
not a secret. Keeping it gitignored breaks reproducible CI builds for no
real benefit.

## Privacy policy update (`docs/privacy.html`)

1. Bump `Last updated: 2026-05-05`.
2. From the "What the app does not do" list, **remove**:
   - "No analytics or telemetry SDKs."
   - "No crash reporting that leaves the device."
3. Soften the lede: change "no analytics, no advertising, and no third-party
   tracking" to "no advertising, no third-party tracking, and no analytics or
   crash reporting unless you opt in."
4. Insert a new `<h2>Optional crash reporting</h2>` section between
   "The 'Share to LLM' feature" and "What the app does not do":

   > The app includes Firebase Crashlytics, **off by default**. You can turn
   > it on in Settings → Crash reporting. When enabled, if the app crashes
   > or hits a non-fatal error (e.g., a network failure fetching the bill
   > list), an anonymous report is sent to Google Firebase containing the
   > stack trace, device model, OS version, app version, locale, and a
   > Crashlytics-generated installation identifier. The reports include no
   > information about you, no account identifier (the app has no
   > accounts), and no content from the bills you've viewed. You can turn
   > the toggle back off at any time, which also requests deletion of any
   > unsent reports. Crashlytics is governed by Google's
   > [Firebase data processing terms](https://firebase.google.com/terms/data-processing-terms).

## Tests

- `CrashReportingPreferenceRepositoryTest` — round-trip via in-memory
  DataStore (use the same test pattern as any existing repository tests, or
  the standard `PreferenceDataStoreFactory.create { tempFile }` setup).
- `FakeCrashReporter` test double in `androidTest`/`test` source set;
  use it in unit tests for `BillRepository` and `BillTextFetcher` to assert
  `recordNonFatal` is invoked on the failure paths and not invoked on
  success.
- No instrumented Firebase tests; the `CrashReporter` interface is the test
  seam.

## Out of scope (explicitly)

- NDK Crashlytics — no native code.
- `firebase-analytics` — Crashlytics no longer requires it.
- Custom keys, `Crashlytics.log()` breadcrumbs, user IDs, performance
  monitoring, remote config, App Check.
- First-run consent dialog or nudge banner.
- A versionCode / versionName bump — that belongs to the next release-app
  pass, not this work.

## Operational follow-ups (not in this spec, listed so they aren't lost)

- After merge: cut a new release (versionCode bump, signed AAB, mapping
  archive) per the existing `release-app` skill so the new privacy text
  ships with the matching app build.
- After release: confirm in the Firebase console that crash reports flow
  in once a tester opts in.
