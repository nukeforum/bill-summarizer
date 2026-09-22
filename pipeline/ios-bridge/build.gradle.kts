import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(17)
    jvm()

    val frameworkName = "InformedCitizenPipeline"
    val xcframework = XCFramework(frameworkName)
    val appleTargets = listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64(),
    )

    appleTargets.forEach { target ->
        target.binaries.framework {
            baseName = frameworkName
            isStatic = true
            binaryOption("bundleId", "com.informedcitizen.pipeline")
            xcframework.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
