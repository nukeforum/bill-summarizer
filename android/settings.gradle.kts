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
