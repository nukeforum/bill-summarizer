plugins {
    id("informedcitizen.android.library")
    id("informedcitizen.android.hilt")
}

android {
    namespace = "com.informedcitizen.datastore"
}

dependencies {
    // api so consuming modules can inject DataStore<Preferences> directly
    // without re-declaring the dependency.
    api(libs.androidx.datastore.preferences)
}
