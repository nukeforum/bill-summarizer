plugins {
  id("informedcitizen.android.application")
  id("informedcitizen.android.hilt")
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.services)
  alias(libs.plugins.firebase.crashlytics)
  alias(libs.plugins.sqldelight)
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
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (releaseSigningConfigured) {
                signingConfigs.getByName("release")
            } else {
                null
            }
        }
    }

    buildFeatures {
        compose = true
    }

    lint {
        baseline = file("lint-baseline.xml")
    }
}

sqldelight {
    databases {
        create("BillSummaryDatabase") {
            packageName.set("com.informedcitizen.cache")
        }
    }
}

// SQLDelight 2.x generates Kotlin sources into build/generated/sqldelight/.
// AGP's regular kotlin compilation picks them up, but the KSP task doesn't,
// so Hilt fails to resolve BillSummaryDatabase when it processes
// SqlDelightDriverModule. Use the AGP Variant API to register the generated
// dir as Kotlin source for each variant, and tie the kspKotlin task to the
// generator so codegen runs first. (This hack goes away once SQLDelight
// moves into its own :core:database module that doesn't apply KSP.)
androidComponents {
    onVariants { variant ->
        val genDir = layout.buildDirectory
            .dir("generated/sqldelight/code/BillSummaryDatabase/${variant.name}")
            .get().asFile.absolutePath
        variant.sources.kotlin?.addStaticSourceDirectory(genDir)
    }
}
afterEvaluate {
    listOf("Debug", "Release").forEach { variant ->
        val generateTask = tasks.findByName("generate${variant}BillSummaryDatabaseInterface") ?: return@forEach
        val kspTask = tasks.findByName("ksp${variant}Kotlin") ?: return@forEach
        kspTask.dependsOn(generateTask)
    }
}

dependencies {
  implementation(project(":core:model"))

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

  // Firebase
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.crashlytics)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
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

  // Networking
  implementation(libs.retrofit)
  implementation(libs.retrofit.converter.kotlinx.serialization)
  implementation(libs.okhttp)
  implementation(libs.okhttp.logging.interceptor)
  implementation(libs.kotlinx.serialization.json)

  // DataStore
  implementation(libs.androidx.datastore.preferences)

  // Chrome Custom Tabs
  implementation(libs.androidx.browser)

  // Splash screen (Android 12+ API, back-compat to API 21)
  implementation(libs.androidx.core.splashscreen)

  // SQLDelight cache + WorkManager + Hilt-Work
  implementation(libs.sqldelight.android.driver)
  implementation(libs.sqldelight.coroutines.extensions)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.hilt.work)
  ksp(libs.androidx.hilt.compiler)

  // On-device AICore (Gemini Nano)
  implementation(libs.google.ai.edge.aicore)

  testImplementation(libs.sqldelight.sqlite.driver)
  testImplementation(libs.androidx.work.testing)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.ext.junit)
}
