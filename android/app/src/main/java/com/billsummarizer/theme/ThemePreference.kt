package com.billsummarizer.theme

enum class ThemeFamily { MATERIAL, SOLARIZED }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ThemePreference {
    MATERIAL_SYSTEM,
    MATERIAL_LIGHT,
    MATERIAL_DARK,
    SOLARIZED_SYSTEM,
    SOLARIZED_LIGHT,
    SOLARIZED_DARK;

    companion object {
        val DEFAULT = SOLARIZED_SYSTEM

        fun fromStored(name: String?): ThemePreference =
            name?.let { runCatching { valueOf(it) }.getOrNull() } ?: DEFAULT

        fun from(family: ThemeFamily, mode: ThemeMode): ThemePreference = when (family) {
            ThemeFamily.MATERIAL -> when (mode) {
                ThemeMode.SYSTEM -> MATERIAL_SYSTEM
                ThemeMode.LIGHT -> MATERIAL_LIGHT
                ThemeMode.DARK -> MATERIAL_DARK
            }
            ThemeFamily.SOLARIZED -> when (mode) {
                ThemeMode.SYSTEM -> SOLARIZED_SYSTEM
                ThemeMode.LIGHT -> SOLARIZED_LIGHT
                ThemeMode.DARK -> SOLARIZED_DARK
            }
        }
    }
}

val ThemePreference.family: ThemeFamily
    get() = when (this) {
        ThemePreference.MATERIAL_SYSTEM,
        ThemePreference.MATERIAL_LIGHT,
        ThemePreference.MATERIAL_DARK -> ThemeFamily.MATERIAL
        ThemePreference.SOLARIZED_SYSTEM,
        ThemePreference.SOLARIZED_LIGHT,
        ThemePreference.SOLARIZED_DARK -> ThemeFamily.SOLARIZED
    }

val ThemePreference.mode: ThemeMode
    get() = when (this) {
        ThemePreference.MATERIAL_SYSTEM,
        ThemePreference.SOLARIZED_SYSTEM -> ThemeMode.SYSTEM
        ThemePreference.MATERIAL_LIGHT,
        ThemePreference.SOLARIZED_LIGHT -> ThemeMode.LIGHT
        ThemePreference.MATERIAL_DARK,
        ThemePreference.SOLARIZED_DARK -> ThemeMode.DARK
    }

fun ThemePreference.withFamily(newFamily: ThemeFamily): ThemePreference =
    ThemePreference.from(newFamily, mode)

fun ThemePreference.withMode(newMode: ThemeMode): ThemePreference =
    ThemePreference.from(family, newMode)

fun resolve(pref: ThemePreference, systemDark: Boolean): Pair<ThemeFamily, Boolean> = when (pref) {
    ThemePreference.MATERIAL_SYSTEM -> ThemeFamily.MATERIAL to systemDark
    ThemePreference.MATERIAL_LIGHT -> ThemeFamily.MATERIAL to false
    ThemePreference.MATERIAL_DARK -> ThemeFamily.MATERIAL to true
    ThemePreference.SOLARIZED_SYSTEM -> ThemeFamily.SOLARIZED to systemDark
    ThemePreference.SOLARIZED_LIGHT -> ThemeFamily.SOLARIZED to false
    ThemePreference.SOLARIZED_DARK -> ThemeFamily.SOLARIZED to true
}
