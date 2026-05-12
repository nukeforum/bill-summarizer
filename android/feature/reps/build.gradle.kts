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
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(project(":core:ui"))

    // MemberDetailViewModel needs BillRepository (still in :app). Hook this
    // up once :feature:bills exists; until then BillRepository binds through
    // the app graph and is resolved at Hilt aggregation time.

    implementation(libs.kotlinx.serialization.json)
}
