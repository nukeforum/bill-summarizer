pluginManagement {
    // Included builds do not inherit the root build's Gradle properties, so
    // load the owning android/ build's gradle.properties directly to keep
    // `foojayResolverVersion` single-sourced. Bump it in
    // android/gradle.properties.
    val rootProperties = java.util.Properties().apply {
        settings.settingsDir.parentFile.resolve("gradle.properties").inputStream().use { load(it) }
    }
    val foojayResolverVersion = rootProperties.getProperty("foojayResolverVersion")
        ?: error("foojayResolverVersion missing from android/gradle.properties")
    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version foojayResolverVersion
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
