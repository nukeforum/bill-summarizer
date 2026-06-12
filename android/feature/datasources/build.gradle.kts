plugins {
    id("informedcitizen.android.feature")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.informedcitizen.feature.datasources"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:crash"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:ui"))
    // publishByok* push targets live on the concrete repositories.
    implementation(project(":feature:bills"))
    implementation(project(":feature:reps"))
    implementation(project(":feature:calendar"))

    // The in-app pipeline calls pipeline:shared's orchestrators
    // directly (the same code CI runs). Its Ktor/okio/datetime types
    // appear in signatures we consume, so they're compile-time deps
    // here; the implementations arrive transitively.
    implementation(libs.ktor.client.core)
    implementation(libs.okio)
    implementation(libs.kotlinx.datetime)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.okio.fakefilesystem)
}
