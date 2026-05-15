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
}
