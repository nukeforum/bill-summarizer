import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class KotlinLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")

        extensions.configure(JavaPluginExtension::class.java) {
            sourceCompatibility = Versions.JAVA
            targetCompatibility = Versions.JAVA
        }
        extensions.configure(KotlinJvmProjectExtension::class.java) {
            jvmToolchain(Versions.JVM_TOOLCHAIN)
            compilerOptions {
                jvmTarget.set(JvmTarget.fromTarget(Versions.JAVA.toString()))
                allWarningsAsErrors.set(true)
            }
        }
        tasks.withType(JavaCompile::class.java).configureEach {
            options.compilerArgs.add("-Werror")
        }
    }
}
