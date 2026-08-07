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
    // Paging 3 for the sharded bills list (#41): paging-runtime carries the
    // RemoteMediator; paging-compose drives the LazyPagingItems list body.
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    // BackHandler in the in-app full-text reader (issue #98): system back
    // closes the reader overlay rather than leaving the bill-detail screen.
    implementation(libs.androidx.activity.compose)

    // BillDetailViewModel uses BillTextFetcher via :core:network and
    // BillRepository (now owned here). LlmShareHelper uses ui/util from
    // :core:ui plus Bill from :core:model. BillsListViewModel reaches
    // into :feature:calendar for SessionCalendarRepository and into
    // :core:database for BillSummaryCache.

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // asSnapshot() drives the pagedBills Pager (RemoteMediator + PagingSource)
    // end-to-end in a plain JVM test with no adapter/UI.
    testImplementation(libs.androidx.paging.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
    // Compose UI tests run on the JVM via Robolectric (versions from the
    // compose BOM exposed by :core:ui).
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
}
