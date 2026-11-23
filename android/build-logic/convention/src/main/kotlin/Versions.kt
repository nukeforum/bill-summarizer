import org.gradle.api.JavaVersion

object Versions {
    const val COMPILE_SDK = 37
    const val COMPILE_SDK_MINOR = 2
    const val MIN_SDK = 26
    const val TARGET_SDK = 37
    // Bytecode level the app's modules compile to, kept at 17 for D8 and the
    // Android runtime; JVM_TOOLCHAIN is the JDK that compiles them.
    val JAVA = JavaVersion.VERSION_17
    const val JVM_TOOLCHAIN = 25
}
