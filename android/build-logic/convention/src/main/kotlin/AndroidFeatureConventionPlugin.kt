import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("informedcitizen.android.library.compose")
                apply("informedcitizen.android.hilt")
            }
            val libs = extensions.getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java).named("libs")
            dependencies.add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            dependencies.add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
            dependencies.add("implementation", libs.findLibrary("androidx-hilt-navigation-compose").get())
        }
    }
}
