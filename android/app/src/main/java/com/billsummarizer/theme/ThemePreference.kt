package com.billsummarizer.theme

enum class ThemeFamily { MATERIAL, SOLARIZED }

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
    }
}

fun resolve(pref: ThemePreference, systemDark: Boolean): Pair<ThemeFamily, Boolean> = when (pref) {
    ThemePreference.MATERIAL_SYSTEM -> ThemeFamily.MATERIAL to systemDark
    ThemePreference.MATERIAL_LIGHT -> ThemeFamily.MATERIAL to false
    ThemePreference.MATERIAL_DARK -> ThemeFamily.MATERIAL to true
    ThemePreference.SOLARIZED_SYSTEM -> ThemeFamily.SOLARIZED to systemDark
    ThemePreference.SOLARIZED_LIGHT -> ThemeFamily.SOLARIZED to false
    ThemePreference.SOLARIZED_DARK -> ThemeFamily.SOLARIZED to true
}
