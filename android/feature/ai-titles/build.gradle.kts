plugins {
    id("informedcitizen.android.feature")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.informedcitizen.feature.aititles"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:crash"))
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(project(":core:database"))
    implementation(project(":core:ui"))
    // BillRepository (owned by :feature:bills) is used by the worker
    // and controller. A cleaner split would extract a BillRepository
    // interface into :core:model; that's tracked as future work.
    implementation(project(":feature:bills"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.google.ai.edge.aicore)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
}
