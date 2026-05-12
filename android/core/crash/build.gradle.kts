plugins {
    id("informedcitizen.android.library")
    id("informedcitizen.android.hilt")
}

android {
    namespace = "com.informedcitizen.crash"
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
}
