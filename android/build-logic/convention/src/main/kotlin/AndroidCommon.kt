import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
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
}
