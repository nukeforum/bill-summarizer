plugins {
    id("informedcitizen.android.library")
}

android {
    namespace = "com.informedcitizen.core.testing"
}

dependencies {
    api(project(":core:crash"))
    api(project(":core:datastore"))
}
