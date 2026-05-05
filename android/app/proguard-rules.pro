# R8/ProGuard rules. Retrofit, OkHttp, and Hilt ship their own consumer rules
# (or generate code R8 already understands), so nothing is needed for them
# here. kotlinx.serialization is reflection-driven at runtime and needs
# explicit keep rules for serializer companions.

# kotlinx.serialization
# https://github.com/Kotlin/kotlinx.serialization#android
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class com.informedcitizen.**$$serializer { *; }
-keepclassmembers class com.informedcitizen.** {
    *** Companion;
}
-keepclasseswithmembers class com.informedcitizen.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Compose tooling references that R8 occasionally flags in optimized builds.
-dontwarn androidx.compose.ui.tooling.**
