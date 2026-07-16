pluginManagement {
    // foojay-resolver version comes from `foojayResolverVersion` in this
    // build's gradle.properties. `pipeline/` is a standalone Gradle build
    // (own gradlew, also consumable via includeBuild from android/), so it
    // cannot read android/gradle.properties; the property is defined once
    // per Gradle build instead. Bump it in pipeline/gradle.properties.
    val foojayResolverVersion: String by settings
    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version foojayResolverVersion
    }
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
    id("org.gradle.toolchains.foojay-resolver-convention")
}

rootProject.name = "pipeline"
include(":shared")
include(":cli")
