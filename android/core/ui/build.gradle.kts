plugins {
    id("informedcitizen.android.library.compose")
}

android {
    namespace = "com.informedcitizen.core.ui"
}

dependencies {
    api(project(":core:model"))

    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.compose.ui.tooling.preview)
    debugApi(libs.androidx.compose.ui.tooling)

    api(libs.androidx.lifecycle.runtime.compose)

    // BackHandler for BillSearchField's focus-scoped system-back handling (#109).
    implementation(libs.androidx.activity.compose)

    // Chrome Custom Tabs for ui/util/openInCustomTab.
    implementation(libs.androidx.browser)
    // androidx.core.net.toUri extension used by openInCustomTab.
    implementation(libs.androidx.core.ktx)

    // androidx.core.text.HtmlCompat used elsewhere in core:network; not needed here.

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
    // Compose UI tests run on the JVM via Robolectric (versions from the
    // compose BOM exposed above).
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
}
