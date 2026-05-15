plugins {
    id("informedcitizen.kotlin.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)

    // Data shapes (Bill, Member, Outcome enum, …) live in `pipeline:shared`
    // so the same types serve Android, the JVM CLI used by GitHub Actions,
    // and the future iOS app. `api` so transitive consumers of `:core:model`
    // pick the types up without a direct dep on `pipeline:shared`.
    api("com.informedcitizen.pipeline:shared")
}
