pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Composite build wiring: `pipeline:shared` (KMP, lives in the sibling
// `pipeline/` Gradle build) is consumed by Android modules and by the
// `pipeline:cli` JVM CLI. iOS will consume it via XCFramework / SPM.
// See TODO "Shared Pipeline (KMP)" for the multi-consumer rationale.
includeBuild("../pipeline")

rootProject.name = "Informed Citizen"
include(":app")
include(":core:model")
include(":core:crash")
include(":core:datastore")
include(":core:network")
include(":core:database")
include(":core:ui")
include(":feature:calendar")
include(":feature:reps")
include(":feature:bills")
include(":feature:ai-titles")
include(":feature:datasources")
include(":core:testing")
