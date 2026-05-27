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

    // Chrome Custom Tabs for ui/util/openInCustomTab.
    implementation(libs.androidx.browser)
    // androidx.core.net.toUri extension used by openInCustomTab.
    implementation(libs.androidx.core.ktx)

    // androidx.core.text.HtmlCompat used elsewhere in core:network; not needed here.

    testImplementation(libs.junit)
}
