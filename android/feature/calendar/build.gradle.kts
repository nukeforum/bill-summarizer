plugins {
    id("informedcitizen.android.feature")
}

android {
    namespace = "com.informedcitizen.feature.calendar"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:crash"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:ui"))

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
}
