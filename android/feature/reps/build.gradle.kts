plugins {
    id("informedcitizen.android.feature")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.informedcitizen.feature.reps"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:crash"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(project(":core:ui"))

    // MemberDetailViewModel injects BillRepository for the sponsored
    // legislation rows.
    implementation(project(":feature:bills"))

    // The reps VMs read the election calendar (ElectionCalendarRepository)
    // and the shared "on your ballot" matcher (upForElectionBadge) to flag
    // saved reps facing their next general election (issue #33).
    implementation(project(":feature:calendar"))

    implementation(libs.kotlinx.serialization.json)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
}
