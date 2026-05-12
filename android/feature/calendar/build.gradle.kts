plugins {
    id("informedcitizen.android.feature")
}

android {
    namespace = "com.informedcitizen.feature.calendar"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:crash"))
    implementation(project(":core:network"))
    implementation(project(":core:ui"))
}
