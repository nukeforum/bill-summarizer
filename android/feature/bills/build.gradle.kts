plugins {
    id("informedcitizen.android.feature")
}

android {
    namespace = "com.informedcitizen.feature.bills"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:crash"))
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(project(":core:database"))
    implementation(project(":core:ui"))
    implementation(project(":feature:calendar"))

    // BillDetailViewModel uses BillTextFetcher via :core:network and
    // BillRepository (now owned here). LlmShareHelper uses ui/util from
    // :core:ui plus Bill from :core:model. BillsListViewModel reaches
    // into :feature:calendar for SessionCalendarRepository and into
    // :core:database for BillSummaryCache.

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
}
