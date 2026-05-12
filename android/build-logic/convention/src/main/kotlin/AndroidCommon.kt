import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

internal fun Project.configureKotlinAndroid() {
    extensions.configure(KotlinAndroidProjectExtension::class.java) {
        jvmToolchain(Versions.JVM_TOOLCHAIN)
        compilerOptions {
            allWarningsAsErrors.set(true)
        }
    }
    tasks.withType(JavaCompile::class.java).configureEach {
        options.compilerArgs.add("-Werror")
    }
    // AGP 9 / Gradle 9 fail the unit-test task when a module has no
    // discovered tests. Library modules in this codebase that ship no
    // tests of their own (e.g. :core:datastore) should not break the
    // root `gradle test` run.
    tasks.withType(Test::class.java).configureEach {
        failOnNoDiscoveredTests.set(false)
    }
}
