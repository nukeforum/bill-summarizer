# Crashlytics Opt-In Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire Firebase Crashlytics into the Informed Citizen Android app as an opt-in feature (default off, toggled in Settings), reporting both crashes and explicit non-fatals from the two known network catch points, and update the privacy policy to honor the new toggle.

**Architecture:** A small `CrashReporter` interface abstracts Firebase from app code, with a `FirebaseCrashReporter` impl Hilt-bound and a `FakeCrashReporter` used in tests. A new DataStore-backed `CrashReportingPreferenceRepository` persists the opt-in flag. `InformedCitizenApp.onCreate` reads the flag once on startup (synchronously via `runBlocking`) and toggles `FirebaseCrashlytics.isCrashlyticsCollectionEnabled`. Debug builds short-circuit to disabled regardless of the flag.

**Tech Stack:** Kotlin 2.3, Android Gradle Plugin 9.2, Hilt 2.59, DataStore 1.2, Firebase BoM 33.x, Firebase Crashlytics SDK + Gradle plugin, JUnit 4.

Working directory for all `./gradlew` commands: `android/`.

---

## File Structure

**Create:**
- `android/app/src/main/java/com/informedcitizen/crash/CrashReporter.kt` — interface
- `android/app/src/main/java/com/informedcitizen/crash/FirebaseCrashReporter.kt` — Firebase-backed impl
- `android/app/src/main/java/com/informedcitizen/crash/BuildEnvironment.kt` — `isDebuggable` flag (avoids needing `BuildConfig`)
- `android/app/src/main/java/com/informedcitizen/di/CrashModule.kt` — Hilt bindings for `CrashReporter` + `BuildEnvironment`
- `android/app/src/main/java/com/informedcitizen/data/repository/CrashReportingPreferenceRepository.kt` — DataStore boolean
- `android/app/src/test/java/com/informedcitizen/crash/FakeCrashReporter.kt` — test double
- `android/app/src/test/java/com/informedcitizen/data/repository/CrashReportingPreferenceRepositoryTest.kt`
- `android/app/src/test/java/com/informedcitizen/data/repository/BillRepositoryTest.kt`

**Modify:**
- `android/gradle/libs.versions.toml` — Firebase BoM, Crashlytics lib, two plugins
- `android/build.gradle.kts` — `apply false` for new plugins
- `android/app/build.gradle.kts` — apply plugins, add Firebase deps
- `android/app/src/main/AndroidManifest.xml` — add `firebase_crashlytics_collection_enabled` meta-data
- `android/app/src/main/java/com/informedcitizen/InformedCitizenApp.kt` — startup wiring
- `android/app/src/main/java/com/informedcitizen/data/repository/BillRepository.kt` — inject `CrashReporter`, record non-fatal on failure
- `android/app/src/main/java/com/informedcitizen/data/api/BillTextFetcher.kt` — same pattern
- `android/app/src/main/java/com/informedcitizen/ui/settings/SettingsViewModel.kt` — second `StateFlow<Boolean>` + setter
- `android/app/src/main/java/com/informedcitizen/ui/settings/SettingsScreen.kt` — Crash reporting section UI
- `docs/privacy.html` — wording updates per spec

**Track in git for the first time:**
- `android/app/google-services.json` — currently untracked

---

## Task 1: Gradle plugin and dependency wiring

**Files:**
- Modify: `android/gradle/libs.versions.toml`
- Modify: `android/build.gradle.kts`
- Modify: `android/app/build.gradle.kts`

- [ ] **Step 1: Add Firebase versions, libraries, and plugins to the catalog**

Edit `android/gradle/libs.versions.toml`. Under `[versions]`, add (keep alphabetical-ish where existing list has it; the existing file is roughly grouped, so just add at the end of `[versions]`):

```toml
firebaseBom = "33.6.0"
googleServicesPlugin = "4.4.2"
firebaseCrashlyticsPlugin = "3.0.2"
```

Under `[libraries]`, append:

```toml
firebase-bom = { module = "com.google.firebase:firebase-bom", version.ref = "firebaseBom" }
firebase-crashlytics = { module = "com.google.firebase:firebase-crashlytics" }
```

Under `[plugins]`, append:

```toml
google-services = { id = "com.google.gms.google-services", version.ref = "googleServicesPlugin" }
firebase-crashlytics = { id = "com.google.firebase.crashlytics", version.ref = "firebaseCrashlyticsPlugin" }
```

- [ ] **Step 2: Wire plugins at the project level**

Edit `android/build.gradle.kts`. Inside the existing `plugins { ... }` block, add two lines so the block becomes:

```kotlin
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.hilt.android) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.google.services) apply false
  alias(libs.plugins.firebase.crashlytics) apply false
}
```

- [ ] **Step 3: Apply plugins and add Firebase deps in app module**

Edit `android/app/build.gradle.kts`. In the top-level `plugins { ... }` block, add:

```kotlin
  alias(libs.plugins.google.services)
  alias(libs.plugins.firebase.crashlytics)
```

so it becomes:

```kotlin
plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.hilt.android)
  alias(libs.plugins.ksp)
  alias(libs.plugins.google.services)
  alias(libs.plugins.firebase.crashlytics)
}
```

In the `dependencies { ... }` block, add a `// Firebase` group right after the existing `// Compose` block (before `debugImplementation(libs.androidx.compose.ui.tooling)`):

```kotlin
  // Firebase
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.crashlytics)
```

- [ ] **Step 4: Verify the build still configures and assembles**

From `android/`:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. The `google-services` plugin will warn or fail if `app/google-services.json` is missing — it's already there, so it should pass.

- [ ] **Step 5: Commit**

```bash
git add android/gradle/libs.versions.toml android/build.gradle.kts android/app/build.gradle.kts
git commit -m "Crashlytics: wire Firebase BoM + Crashlytics Gradle plugin"
```

---

## Task 2: Track `google-services.json` in git

**Files:**
- Add to git: `android/app/google-services.json`

- [ ] **Step 1: Confirm the file is currently untracked**

```bash
git status --short android/app/google-services.json
```

Expected: `?? android/app/google-services.json`.

- [ ] **Step 2: Stage and commit**

```bash
git add android/app/google-services.json
git commit -m "Crashlytics: commit google-services.json (open-source repo, key restricted by package + signature)"
```

---

## Task 3: Add the Crashlytics collection-disabled manifest meta-data

**Files:**
- Modify: `android/app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add the meta-data tag**

Edit `android/app/src/main/AndroidManifest.xml`. Inside the `<application>` element, immediately after the opening `<application ...>` line and before the `<activity>`, add:

```xml
        <!--
          Default Crashlytics auto-collection to OFF. Programmatic enable
          happens only on release builds when the user has opted in via
          Settings, so any race window before our app code runs is silent.
        -->
        <meta-data
            android:name="firebase_crashlytics_collection_enabled"
            android:value="false" />
```

- [ ] **Step 2: Verify build still passes**

From `android/`:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/AndroidManifest.xml
git commit -m "Crashlytics: default firebase_crashlytics_collection_enabled to false in manifest"
```

---

## Task 4: `BuildEnvironment` value object (debug-build detection)

**Files:**
- Create: `android/app/src/main/java/com/informedcitizen/crash/BuildEnvironment.kt`

We need an `isDebuggable` flag injectable into the ViewModel and Application. We avoid `BuildConfig.DEBUG` because `buildFeatures.buildConfig = false` is set in the project. Instead we read `ApplicationInfo.FLAG_DEBUGGABLE` once.

- [ ] **Step 1: Create the file**

```kotlin
package com.informedcitizen.crash

/**
 * Whether the running build has the debuggable flag set in its
 * ApplicationInfo. Used to short-circuit Crashlytics off in dev builds
 * regardless of the user's opt-in toggle.
 */
@JvmInline
value class BuildEnvironment(val isDebuggable: Boolean)
```

- [ ] **Step 2: Verify compile**

From `android/`:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/informedcitizen/crash/BuildEnvironment.kt
git commit -m "Crashlytics: add BuildEnvironment value class for debuggable flag"
```

---

## Task 5: `CrashReporter` interface

**Files:**
- Create: `android/app/src/main/java/com/informedcitizen/crash/CrashReporter.kt`

- [ ] **Step 1: Create the file**

```kotlin
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
```

- [ ] **Step 2: Verify compile**

From `android/`:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/informedcitizen/crash/CrashReporter.kt
git commit -m "Crashlytics: add CrashReporter interface"
```

---

## Task 6: `FakeCrashReporter` for tests

**Files:**
- Create: `android/app/src/test/java/com/informedcitizen/crash/FakeCrashReporter.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.informedcitizen.crash

class FakeCrashReporter : CrashReporter {

    data class Recorded(val throwable: Throwable, val message: String?)

    private val _recorded = mutableListOf<Recorded>()
    val recorded: List<Recorded> get() = _recorded.toList()

    override fun recordNonFatal(throwable: Throwable, message: String?) {
        _recorded.add(Recorded(throwable, message))
    }
}
```

- [ ] **Step 2: Verify it compiles in the test source set**

From `android/`:

```bash
./gradlew :app:compileDebugUnitTestKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/test/java/com/informedcitizen/crash/FakeCrashReporter.kt
git commit -m "Crashlytics: add FakeCrashReporter test double"
```

---

## Task 7: `FirebaseCrashReporter` production impl

**Files:**
- Create: `android/app/src/main/java/com/informedcitizen/crash/FirebaseCrashReporter.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.informedcitizen.crash

import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseCrashReporter @Inject constructor() : CrashReporter {
    override fun recordNonFatal(throwable: Throwable, message: String?) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        if (message != null) {
            crashlytics.log(message)
        }
        crashlytics.recordException(throwable)
    }
}
```

- [ ] **Step 2: Verify compile**

From `android/`:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/informedcitizen/crash/FirebaseCrashReporter.kt
git commit -m "Crashlytics: add FirebaseCrashReporter production impl"
```

---

## Task 8: Hilt `CrashModule` (binds `CrashReporter` + provides `BuildEnvironment`)

**Files:**
- Create: `android/app/src/main/java/com/informedcitizen/di/CrashModule.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.informedcitizen.di

import android.content.Context
import android.content.pm.ApplicationInfo
import com.informedcitizen.crash.BuildEnvironment
import com.informedcitizen.crash.CrashReporter
import com.informedcitizen.crash.FirebaseCrashReporter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CrashBindingsModule {
    @Binds
    @Singleton
    abstract fun bindCrashReporter(impl: FirebaseCrashReporter): CrashReporter
}

@Module
@InstallIn(SingletonComponent::class)
object CrashProvidersModule {

    @Provides
    @Singleton
    fun provideBuildEnvironment(@ApplicationContext context: Context): BuildEnvironment {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return BuildEnvironment(isDebuggable = debuggable)
    }
}
```

- [ ] **Step 2: Verify Hilt graph still compiles**

From `android/`:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. Hilt's KSP processor will fail loudly if the bindings are wrong.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/informedcitizen/di/CrashModule.kt
git commit -m "Crashlytics: add Hilt module binding CrashReporter + BuildEnvironment"
```

---

## Task 9: `CrashReportingPreferenceRepository` (TDD)

**Files:**
- Create: `android/app/src/main/java/com/informedcitizen/data/repository/CrashReportingPreferenceRepository.kt`
- Create: `android/app/src/test/java/com/informedcitizen/data/repository/CrashReportingPreferenceRepositoryTest.kt`

We use an in-memory DataStore for the test (file-backed, scoped to JUnit's `@Rule` `TemporaryFolder`). This matches what existing repository unit tests would look like; if the project lacks an example, the pattern below is self-contained.

- [ ] **Step 1: Write the failing test**

Create `android/app/src/test/java/com/informedcitizen/data/repository/CrashReportingPreferenceRepositoryTest.kt`:

```kotlin
package com.informedcitizen.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CrashReportingPreferenceRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newDataStore(): DataStore<Preferences> {
        val file: File = tempFolder.newFile("crash_prefs.preferences_pb")
        // PreferenceDataStoreFactory needs a non-existent file, so delete the
        // one TemporaryFolder pre-created (it just needs the path).
        file.delete()
        return PreferenceDataStoreFactory.create(produceFile = { file })
    }

    @Test
    fun `default value is false`() = runTest {
        val repo = CrashReportingPreferenceRepository(newDataStore())
        assertFalse(repo.enabled.first())
    }

    @Test
    fun `set true is observed by enabled flow`() = runTest {
        val repo = CrashReportingPreferenceRepository(newDataStore())
        repo.set(true)
        assertTrue(repo.enabled.first())
    }

    @Test
    fun `set false after true round-trips`() = runTest {
        val repo = CrashReportingPreferenceRepository(newDataStore())
        repo.set(true)
        repo.set(false)
        assertEquals(false, repo.enabled.first())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

From `android/`:

```bash
./gradlew :app:testDebugUnitTest --tests "com.informedcitizen.data.repository.CrashReportingPreferenceRepositoryTest"
```

Expected: FAIL — `CrashReportingPreferenceRepository` is unresolved.

- [ ] **Step 3: Add coroutines-test to test dependencies if missing**

`androidx.datastore` + `kotlinx-coroutines-test` are both already wired (see `libs.versions.toml`). No change needed here — this step is just to confirm no dependency gap before moving on.

```bash
grep -n "kotlinx-coroutines-test" gradle/libs.versions.toml
grep -n "kotlinx-coroutines-test" app/build.gradle.kts
```

Expected: both files reference it. If they don't, stop and add `testImplementation(libs.kotlinx.coroutines.test)` to `app/build.gradle.kts` (it's there already at the time this plan was written).

- [ ] **Step 4: Implement the repository**

Create `android/app/src/main/java/com/informedcitizen/data/repository/CrashReportingPreferenceRepository.kt`:

```kotlin
package com.informedcitizen.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

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

- [ ] **Step 5: Run tests to verify they pass**

From `android/`:

```bash
./gradlew :app:testDebugUnitTest --tests "com.informedcitizen.data.repository.CrashReportingPreferenceRepositoryTest"
```

Expected: PASS, three tests, no warnings (project enforces `allWarningsAsErrors`).

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/informedcitizen/data/repository/CrashReportingPreferenceRepository.kt \
        android/app/src/test/java/com/informedcitizen/data/repository/CrashReportingPreferenceRepositoryTest.kt
git commit -m "Crashlytics: add CrashReportingPreferenceRepository with tests"
```

---

## Task 10: `BillRepository` non-fatal reporting (TDD)

**Files:**
- Modify: `android/app/src/main/java/com/informedcitizen/data/repository/BillRepository.kt`
- Create: `android/app/src/test/java/com/informedcitizen/data/repository/BillRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

Create `android/app/src/test/java/com/informedcitizen/data/repository/BillRepositoryTest.kt`:

```kotlin
package com.informedcitizen.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.informedcitizen.crash.FakeCrashReporter
import com.informedcitizen.data.api.BillsApi
import com.informedcitizen.data.model.Bill
import com.informedcitizen.data.model.BillsManifest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class BillRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newDataStore(): DataStore<Preferences> {
        val file: File = tempFolder.newFile("repo_test.preferences_pb")
        file.delete()
        return PreferenceDataStoreFactory.create(produceFile = { file })
    }

    @Test
    fun `success path does not call CrashReporter`() = runTest {
        val reporter = FakeCrashReporter()
        val repo = BillRepository(
            api = StubApi(BillsManifest(bills = emptyList())),
            dataStore = newDataStore(),
            crashReporter = reporter,
        )

        val result = repo.getBills(forceRefresh = true)

        assertTrue(result.isSuccess)
        assertTrue("no non-fatal recorded on success", reporter.recorded.isEmpty())
    }

    @Test
    fun `failure path records non-fatal and surfaces failure`() = runTest {
        val reporter = FakeCrashReporter()
        val boom = IOException("simulated network failure")
        val repo = BillRepository(
            api = ThrowingApi(boom),
            dataStore = newDataStore(),
            crashReporter = reporter,
        )

        val result = repo.getBills(forceRefresh = true)

        assertTrue("getBills returns failure", result.isFailure)
        assertEquals(1, reporter.recorded.size)
        assertSame(boom, reporter.recorded.single().throwable)
        assertEquals("manifest fetch failed", reporter.recorded.single().message)
    }

    private class StubApi(private val manifest: BillsManifest) : BillsApi {
        override suspend fun getBills(): BillsManifest = manifest
    }

    private class ThrowingApi(private val throwable: Throwable) : BillsApi {
        override suspend fun getBills(): BillsManifest = throw throwable
    }
}
```

- [ ] **Step 2: Run the test to verify it fails (compile error or behavior)**

From `android/`:

```bash
./gradlew :app:testDebugUnitTest --tests "com.informedcitizen.data.repository.BillRepositoryTest"
```

Expected: FAIL — `BillRepository`'s constructor doesn't accept a `crashReporter` parameter yet.

- [ ] **Step 3: Update `BillRepository` to inject `CrashReporter` and report on failure**

Edit `android/app/src/main/java/com/informedcitizen/data/repository/BillRepository.kt`. Replace the file contents with:

```kotlin
package com.informedcitizen.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.informedcitizen.crash.CrashReporter
import com.informedcitizen.data.api.BillsApi
import com.informedcitizen.data.model.Bill
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillRepository @Inject constructor(
    private val api: BillsApi,
    private val dataStore: DataStore<Preferences>,
    private val crashReporter: CrashReporter,
) {
    private val mutex = Mutex()
    private var cached: List<Bill>? = null

    suspend fun getBills(forceRefresh: Boolean = false): Result<List<Bill>> = mutex.withLock {
        if (!forceRefresh) {
            cached?.let { return@withLock Result.success(it) }
        }
        runCatching {
            val manifest = api.getBills()
            cached = manifest.bills
            dataStore.edit { it[LAST_FETCHED_KEY] = System.currentTimeMillis() }
            manifest.bills
        }.onFailure { crashReporter.recordNonFatal(it, "manifest fetch failed") }
    }

    fun getBillById(id: String): Bill? = cached?.firstOrNull { it.id == id }

    suspend fun lastFetchedAtMillis(): Long? =
        dataStore.data.firstOrNull()?.get(LAST_FETCHED_KEY)

    private companion object {
        val LAST_FETCHED_KEY = longPreferencesKey("last_fetched_at_millis")
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

From `android/`:

```bash
./gradlew :app:testDebugUnitTest --tests "com.informedcitizen.data.repository.BillRepositoryTest"
```

Expected: PASS, two tests.

- [ ] **Step 5: Run the full unit test suite to confirm no regression**

From `android/`:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL with all tests passing. No new warnings (project sets `allWarningsAsErrors`).

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/informedcitizen/data/repository/BillRepository.kt \
        android/app/src/test/java/com/informedcitizen/data/repository/BillRepositoryTest.kt
git commit -m "Crashlytics: report manifest fetch failures as non-fatals"
```

---

## Task 11: `BillTextFetcher` non-fatal reporting

**Files:**
- Modify: `android/app/src/main/java/com/informedcitizen/data/api/BillTextFetcher.kt`

The pattern is identical to Task 10 but on the OkHttp-backed full-text fetcher. We don't add a unit test here — testing OkHttp call paths requires MockWebServer (a new dep), and the change is a one-liner mirroring code that's already covered by Task 10's tests via the `CrashReporter` seam. Manual smoke test in Task 16 will verify end-to-end.

- [ ] **Step 1: Inject `CrashReporter` and record on failure**

Replace the contents of `android/app/src/main/java/com/informedcitizen/data/api/BillTextFetcher.kt` with:

```kotlin
package com.informedcitizen.data.api

import androidx.core.text.HtmlCompat
import com.informedcitizen.crash.CrashReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillTextFetcher @Inject constructor(
    private val client: OkHttpClient,
    private val crashReporter: CrashReporter,
) {
    suspend fun fetchPlainText(url: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} fetching $url")
                }
                val html = response.body.string()
                stripHtml(html)
            }
        }.onFailure { crashReporter.recordNonFatal(it, "full-text fetch failed") }
    }

    private fun stripHtml(html: String): String {
        val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
        return spanned.toString().replace(Regex("\\n{3,}"), "\n\n").trim()
    }
}
```

- [ ] **Step 2: Verify the Hilt graph + tests still compile and pass**

From `android/`:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/informedcitizen/data/api/BillTextFetcher.kt
git commit -m "Crashlytics: report full-text fetch failures as non-fatals"
```

---

## Task 12: `InformedCitizenApp` startup wiring

**Files:**
- Modify: `android/app/src/main/java/com/informedcitizen/InformedCitizenApp.kt`

- [ ] **Step 1: Replace the application class to wire startup**

Replace the contents of `android/app/src/main/java/com/informedcitizen/InformedCitizenApp.kt` with:

```kotlin
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
```

- [ ] **Step 2: Build and run the unit tests**

From `android/`:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, tests pass.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/informedcitizen/InformedCitizenApp.kt
git commit -m "Crashlytics: apply opt-in flag at app startup, force-off on debug"
```

---

## Task 13: `SettingsViewModel` exposes crash-reporting state

**Files:**
- Modify: `android/app/src/main/java/com/informedcitizen/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: Update the ViewModel**

Replace the contents of `android/app/src/main/java/com/informedcitizen/ui/settings/SettingsViewModel.kt` with:

```kotlin
package com.informedcitizen.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.informedcitizen.crash.BuildEnvironment
import com.informedcitizen.data.repository.CrashReportingPreferenceRepository
import com.informedcitizen.data.repository.ThemePreferenceRepository
import com.informedcitizen.theme.ThemePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePrefs: ThemePreferenceRepository,
    private val crashPrefs: CrashReportingPreferenceRepository,
    private val buildEnvironment: BuildEnvironment,
) : ViewModel() {

    val preference: StateFlow<ThemePreference> = themePrefs.preference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemePreference.DEFAULT)

    val crashReportingEnabled: StateFlow<Boolean> = crashPrefs.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setPreference(pref: ThemePreference) {
        viewModelScope.launch { themePrefs.set(pref) }
    }

    fun setCrashReportingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            crashPrefs.set(enabled)
            if (!buildEnvironment.isDebuggable) {
                val crashlytics = FirebaseCrashlytics.getInstance()
                crashlytics.isCrashlyticsCollectionEnabled = enabled
                if (!enabled) {
                    crashlytics.deleteUnsentReports()
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build**

From `android/`:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/informedcitizen/ui/settings/SettingsViewModel.kt
git commit -m "Crashlytics: expose crash-reporting state and setter from SettingsViewModel"
```

---

## Task 14: Settings UI — Crash reporting section

**Files:**
- Modify: `android/app/src/main/java/com/informedcitizen/ui/settings/SettingsScreen.kt`

We add a second section below "Theme" with a single switch row. Reuse the existing `SectionHeader` composable.

- [ ] **Step 1: Add the section to `SettingsContent` and a new `CrashReportingRow` composable**

Edit `android/app/src/main/java/com/informedcitizen/ui/settings/SettingsScreen.kt`. Add `import androidx.compose.material3.Switch` near the other Material3 imports.

Update the `SettingsScreen` composable to also collect the crash flag and pass through. Replace the body with:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preference by viewModel.preference.collectAsStateWithLifecycle()
    val crashReportingEnabled by viewModel.crashReportingEnabled.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        SettingsContent(
            preference = preference,
            crashReportingEnabled = crashReportingEnabled,
            innerPadding = innerPadding,
            onPreferenceChange = viewModel::setPreference,
            onCrashReportingEnabledChange = viewModel::setCrashReportingEnabled,
        )
    }
}
```

Replace `SettingsContent` with:

```kotlin
@Composable
private fun SettingsContent(
    preference: ThemePreference,
    crashReportingEnabled: Boolean,
    innerPadding: PaddingValues,
    onPreferenceChange: (ThemePreference) -> Unit,
    onCrashReportingEnabledChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(innerPadding)) {
        SectionHeader("Theme")
        FamilySegmentedRow(
            family = preference.family,
            onFamilyChange = { newFamily ->
                onPreferenceChange(preference.withFamily(newFamily))
            },
        )
        ModeRadioGroup(
            mode = preference.mode,
            onModeChange = { newMode ->
                onPreferenceChange(preference.withMode(newMode))
            },
        )
        SectionHeader("Crash reporting")
        CrashReportingRow(
            enabled = crashReportingEnabled,
            onEnabledChange = onCrashReportingEnabledChange,
        )
    }
}
```

Add a new private composable at the bottom of the file (just above the two `displayName` extension properties):

```kotlin
@Composable
private fun CrashReportingRow(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = enabled,
                role = Role.Switch,
                onClick = { onEnabledChange(!enabled) },
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Send crash reports",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Off by default. When on, anonymous crash data " +
                    "(stack traces, device model, OS, app version) is sent " +
                    "to Google Firebase to help fix bugs.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = null)
    }
}
```

- [ ] **Step 2: Build and ensure no warnings (project sets warningsAsErrors)**

From `android/`:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. If any unused-import warnings show up, remove the offending imports.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/informedcitizen/ui/settings/SettingsScreen.kt
git commit -m "Crashlytics: add Settings UI section with opt-in switch"
```

---

## Task 15: Privacy policy update

**Files:**
- Modify: `docs/privacy.html`

- [ ] **Step 1: Bump the date and adjust the lede**

Edit `docs/privacy.html`. Replace `Last updated: 2026-05-04` with `Last updated: 2026-05-05`.

In the opening paragraph, replace:

```
has no accounts, no analytics, no advertising, and no third-party tracking.
```

with:

```
has no accounts, no advertising, and no third-party tracking, and no analytics or crash reporting unless you opt in.
```

- [ ] **Step 2: Insert the "Optional crash reporting" section**

Insert a new `<h2>` section between the existing "The 'Share to LLM' feature" `</p>` (the one that ends `...handles the data under <em>its own</em> privacy policy, not this one.</p>`) and the existing `<h2>What the app does not do</h2>`.

Add:

```html
  <h2>Optional crash reporting</h2>
  <p>
    The app includes Firebase Crashlytics, <strong>off by default</strong>.
    You can turn it on in Settings &rarr; Crash reporting. When enabled, if
    the app crashes or hits a non-fatal error (e.g., a network failure
    fetching the bill list), an anonymous report is sent to Google Firebase
    containing the stack trace, device model, OS version, app version,
    locale, and a Crashlytics-generated installation identifier. Reports
    include no information about you, no account identifier (the app has no
    accounts), and no content from the bills you've viewed. You can turn
    the toggle back off at any time, which also requests deletion of any
    unsent reports. Crashlytics is governed by Google's
    <a href="https://firebase.google.com/terms/data-processing-terms">Firebase data processing terms</a>.
  </p>
```

- [ ] **Step 3: Remove the now-incorrect bullet items from "What the app does not do"**

In the `<ul>` under `<h2>What the app does not do</h2>`, delete these two `<li>` lines:

```html
    <li>No analytics or telemetry SDKs.</li>
    <li>No crash reporting that leaves the device.</li>
```

The remaining bullets (no accounts, no advertising, no sensitive permissions, no selling) stay.

- [ ] **Step 4: Eyeball the rendered HTML**

Open the file in a browser to make sure the layout still reads cleanly:

```bash
# Mac/Linux:  open docs/privacy.html
# Windows:    start docs/privacy.html
start docs/privacy.html
```

Expected: page renders, new section appears between Share-to-LLM and What-the-app-does-not-do, list still has 4 bullets (down from 6).

- [ ] **Step 5: Commit**

```bash
git add docs/privacy.html
git commit -m "Docs: update privacy policy for opt-in Crashlytics"
```

---

## Task 16: Smoke test on a real device

**Files:** none

This validates the end-to-end behavior — Crashlytics integration is one of those things where unit tests can't cover the part that matters (a real Firebase client phoning home). Use the project's `android` CLI (per the user's android-cli skill preference) for install/launch.

- [ ] **Step 1: Build a debug AAB-equivalent and install on the connected Pixel 9 Pro**

Use the `android` CLI to deploy:

```bash
android deploy
```

Expected: build succeeds, app installs and launches.

- [ ] **Step 2: Verify default-off behavior**

In the running app:
1. Open Settings.
2. Confirm a "Crash reporting" section exists below "Theme" with a switch defaulting to **off**.
3. Confirm the supporting text is readable in both Solarized and Material themes (toggle the family).

Expected: toggle is off, text is legible, no rendering glitches.

- [ ] **Step 3: Verify the toggle persists**

1. Flip the switch on.
2. Force-stop the app via long-press → App info → Force stop, OR via `android` CLI.
3. Re-launch and reopen Settings.

Expected: the switch is still on. Flip it off and confirm the same persistence.

- [ ] **Step 4: Verify the debug short-circuit**

Because this is a debug build, the `FLAG_DEBUGGABLE` short-circuit means even with the toggle on, `setCrashlyticsCollectionEnabled` is being set to false at startup. Confirm via logcat:

```bash
android logs | grep -i crashlytics
```

Expected: no crash uploads attempted regardless of the toggle (Crashlytics will log a "data collection disabled" message at startup). This is intentional — the smoke test is for the toggle UX, not for actual crash flow. End-to-end crash flow is verified after the next release build (operational follow-up in spec).

- [ ] **Step 5: No commit (no code changes)**

If anything looks wrong, fix and commit per the affected task. Otherwise nothing to commit here.

---

## Task 17: Update the project TODO

**Files:**
- Modify: `~/tools/MindPalace/MindPalace/bill-summarizer/TODO.md`

- [ ] **Step 1: Move the "Crash reporting" item from Open to Done**

Edit `~/tools/MindPalace/MindPalace/bill-summarizer/TODO.md`.

In the "Open" section, delete:

```markdown
- [ ] **Crash reporting.** Plan called Crashlytics optional; deferred
      from milestone 6 because it needs a Firebase project + a
      `google-services.json` enrolled in the user's account.
```

In the "Done" section, insert at the top (so the most recently completed item leads):

```markdown
- [x] **Crashlytics opt-in shipped** on 2026-05-05. Settings-only toggle,
      default off; debug builds short-circuit to off regardless. Reports
      crashes plus two explicit non-fatals (manifest fetch failure, full-text
      fetch failure). Privacy policy updated to add an "Optional crash
      reporting" section and to drop the now-incorrect "no analytics SDKs"
      / "no crash reporting that leaves the device" bullets. Spec at
      `docs/superpowers/specs/2026-05-05-crashlytics-opt-in-design.md`,
      plan at `docs/superpowers/plans/2026-05-05-crashlytics-opt-in.md`.
      Operational follow-up: cut a new release (versionCode bump + signed
      AAB) so the new privacy text ships with the matching app build.
```

- [ ] **Step 2: No git commit needed**

The TODO file lives outside the repo (per memory note). Saving the file is enough.

---

## Self-Review Notes

- **Spec coverage:** Each spec section maps to a task — Architecture (4–9, 12), Gradle (1), Manifest (3), Non-fatal reporting (10–11), Settings UI (13–14), Privacy update (15), Tests (6, 9, 10), `google-services.json` commit (2), out-of-scope items explicitly skipped.
- **Type consistency:** `CrashReporter.recordNonFatal(throwable, message)`, `CrashReportingPreferenceRepository.enabled`/`set`, `BuildEnvironment.isDebuggable`, `SettingsViewModel.crashReportingEnabled`/`setCrashReportingEnabled` — names are consistent across tasks 5, 9, 10, 11, 12, 13, 14.
- **No placeholders:** every task contains the actual code or commands.
- **Operational follow-up (release cut)** is intentionally NOT a task in this plan — it's flagged in the spec and the TODO entry, and is the user's call when to invoke `release-app`.
