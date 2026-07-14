pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // foojay-resolver version comes from `foojayResolverVersion` in
    // gradle.properties (shared with the build-logic included build);
    // bump it there.
    val foojayResolverVersion: String by settings
    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version foojayResolverVersion
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
    id("org.gradle.toolchains.foojay-resolver-convention")
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
