plugins {
  id("informedcitizen.android.application")
  id("informedcitizen.android.hilt")
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.services)
  alias(libs.plugins.firebase.crashlytics)
}

// Release signing credentials. Read from Gradle properties (typically
// ~/.gradle/gradle.properties) with environment-variable fallback for CI.
// Absent values leave the release signingConfig unconfigured, so debug
// development still works; only release bundling/assembling will fail.
val releaseKeystorePath: String? =
    (project.findProperty("INFORMEDCITIZEN_KEYSTORE_PATH") as String?)
        ?: System.getenv("INFORMEDCITIZEN_KEYSTORE_PATH")
val releaseKeystorePassword: String? =
    (project.findProperty("INFORMEDCITIZEN_KEYSTORE_PASSWORD") as String?)
        ?: System.getenv("INFORMEDCITIZEN_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? =
    (project.findProperty("INFORMEDCITIZEN_KEY_ALIAS") as String?)
        ?: System.getenv("INFORMEDCITIZEN_KEY_ALIAS")
val releaseKeyPassword: String? =
    (project.findProperty("INFORMEDCITIZEN_KEY_PASSWORD") as String?)
        ?: System.getenv("INFORMEDCITIZEN_KEY_PASSWORD")
val releaseSigningConfigured: Boolean = releaseKeystorePath != null &&
    releaseKeystorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null

android {
    namespace = "com.informedcitizen"
    defaultConfig {
        applicationId = "com.informedcitizen"
        versionCode = 3
        versionName = "1.0.1"
    }

    signingConfigs {
        create("release") {
            if (releaseSigningConfigured) {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // Distinct applicationId so debug + release can coexist on a device.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "IC Debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (releaseSigningConfigured) {
                signingConfigs.getByName("release")
            } else {
                null
            }
            resValue("string", "app_name", "Informed Citizen")
        }
    }

    // google-services.json only lists the `com.informedcitizen` client, so the
    // google-services + crashlytics gradle plugins would fail for the .debug
    // variant. CrashModule gates Firebase on FLAG_DEBUGGABLE, so debug builds
    // never invoke Crashlytics at runtime — safe to skip these tasks.
    afterEvaluate {
        tasks.matching { task ->
            val n = task.name
            n.contains("Debug") &&
                (n.contains("GoogleServices") || n.contains("Crashlytics"))
        }.configureEach { enabled = false }
    }

    buildFeatures {
        compose = true
        resValues = true
    }

    lint {
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
  implementation(project(":core:model"))
  implementation(project(":core:crash"))
  implementation(project(":core:datastore"))
  implementation(project(":core:network"))
  implementation(project(":core:database"))
  implementation(project(":core:ui"))
  implementation(project(":feature:calendar"))
  implementation(project(":feature:reps"))
  implementation(project(":feature:bills"))
  implementation(project(":feature:ai-titles"))
    implementation(project(":feature:datasources"))

  // Shared KMP pipeline (lives in the sibling `pipeline/` Gradle build,
  // wired via composite-build in settings.gradle.kts). Same module is
  // consumed by the JVM CLI in CI and will be consumed by iOS via
  // XCFramework. See TODO "Shared Pipeline (KMP)".
  implementation(libs.pipeline.shared)

  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)

  // Firebase Crashlytics SDK is consumed via :core:crash; the
  // google-services + firebase-crashlytics gradle plugins stay here
  // because they operate at the APK level (read google-services.json,
  // upload mapping.txt for the release build).
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(project(":core:testing"))
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Hilt nav-compose (Hilt core itself provided by the convention plugin)
  implementation(libs.androidx.hilt.navigation.compose)

  // Networking provided by :core:network (api-exposed Retrofit + OkHttp).
  // kotlinx.serialization is consumed by repositories that decode their
  // own JSON (e.g. zip crosswalk); keep it here until those move.
  implementation(libs.kotlinx.serialization.json)

  // DataStore types come via :core:datastore (api-exposed).

  // Chrome Custom Tabs
  implementation(libs.androidx.browser)

  // Splash screen (Android 12+ API, back-compat to API 21)
  implementation(libs.androidx.core.splashscreen)

  // SQLDelight comes from :core:database (api-exposed).
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.hilt.work)
  ksp(libs.androidx.hilt.compiler)

  // AICore (Gemini Nano) is consumed via :feature:ai-titles; the
  // tools:overrideLibrary entry in :app/AndroidManifest.xml still applies
  // because manifest overrides are evaluated at the merged-manifest level.

  testImplementation(libs.sqldelight.sqlite.driver)
  testImplementation(libs.androidx.work.testing)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.ext.junit)
}
