plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.informedcitizen.pipeline.cli.MainKt")
}

dependencies {
    implementation(project(":shared"))
    // CLI uses kotlinx-datetime / kotlinx-coroutines / okio types directly
    // (Clock for "now", runBlocking to bridge into suspend fns,
    // Path.toPath for the output-dir arg). `:shared` uses these
    // internally with `implementation`, so they don't transit; declare
    // them here.
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.okio)
    implementation(libs.ktor.client.core)
}
