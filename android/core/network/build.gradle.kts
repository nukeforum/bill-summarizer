plugins {
    id("informedcitizen.android.library")
    id("informedcitizen.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.informedcitizen.network"
}

dependencies {
    // API interfaces return model types and the BillTextFetcher reports
    // non-fatals via CrashReporter, so both flow through this module.
    api(project(":core:model"))
    implementation(project(":core:crash"))

    // Retrofit interfaces are part of the module's API surface — consumers
    // inject them and call their suspend funs, so api() is appropriate.
    api(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    api(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    // HtmlCompat for BillTextFetcher.stripHtml.
    implementation(libs.androidx.core.ktx)
}
