import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // AGP 9+ ships with built-in Kotlin support, so no separate
            // org.jetbrains.kotlin.android plugin apply is needed.
            pluginManager.apply("com.android.application")
            extensions.configure(ApplicationExtension::class.java) {
                compileSdk = Versions.COMPILE_SDK
                defaultConfig {
                    minSdk = Versions.MIN_SDK
                    targetSdk = Versions.TARGET_SDK
                }
                compileOptions {
                    sourceCompatibility = Versions.JAVA
                    targetCompatibility = Versions.JAVA
                }
                buildFeatures {
                    aidl = false
                    buildConfig = false
                    shaders = false
                }
                packaging {
                    resources {
                        excludes += "/META-INF/{AL2.0,LGPL2.1}"
                    }
                }
                lint {
                    warningsAsErrors = true
                    disable += listOf("OldTargetApi", "GradleDependency", "AndroidGradlePluginVersion")
                }
                testOptions {
                    unitTests.isIncludeAndroidResources = true
                }
            }
            configureKotlinAndroid()
        }
    }
}
