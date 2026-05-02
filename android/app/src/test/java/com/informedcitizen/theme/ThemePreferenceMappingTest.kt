package com.informedcitizen.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePreferenceMappingTest {

    @Test
    fun `family returns expected ThemeFamily for every preference`() {
        assertEquals(ThemeFamily.MATERIAL, ThemePreference.MATERIAL_SYSTEM.family)
        assertEquals(ThemeFamily.MATERIAL, ThemePreference.MATERIAL_LIGHT.family)
        assertEquals(ThemeFamily.MATERIAL, ThemePreference.MATERIAL_DARK.family)
        assertEquals(ThemeFamily.SOLARIZED, ThemePreference.SOLARIZED_SYSTEM.family)
        assertEquals(ThemeFamily.SOLARIZED, ThemePreference.SOLARIZED_LIGHT.family)
        assertEquals(ThemeFamily.SOLARIZED, ThemePreference.SOLARIZED_DARK.family)
    }

    @Test
    fun `mode returns expected ThemeMode for every preference`() {
        assertEquals(ThemeMode.SYSTEM, ThemePreference.MATERIAL_SYSTEM.mode)
        assertEquals(ThemeMode.LIGHT, ThemePreference.MATERIAL_LIGHT.mode)
        assertEquals(ThemeMode.DARK, ThemePreference.MATERIAL_DARK.mode)
        assertEquals(ThemeMode.SYSTEM, ThemePreference.SOLARIZED_SYSTEM.mode)
        assertEquals(ThemeMode.LIGHT, ThemePreference.SOLARIZED_LIGHT.mode)
        assertEquals(ThemeMode.DARK, ThemePreference.SOLARIZED_DARK.mode)
    }

    @Test
    fun `from(family, mode) covers the entire 6-value cross product`() {
        assertEquals(ThemePreference.MATERIAL_SYSTEM, ThemePreference.from(ThemeFamily.MATERIAL, ThemeMode.SYSTEM))
        assertEquals(ThemePreference.MATERIAL_LIGHT, ThemePreference.from(ThemeFamily.MATERIAL, ThemeMode.LIGHT))
        assertEquals(ThemePreference.MATERIAL_DARK, ThemePreference.from(ThemeFamily.MATERIAL, ThemeMode.DARK))
        assertEquals(ThemePreference.SOLARIZED_SYSTEM, ThemePreference.from(ThemeFamily.SOLARIZED, ThemeMode.SYSTEM))
        assertEquals(ThemePreference.SOLARIZED_LIGHT, ThemePreference.from(ThemeFamily.SOLARIZED, ThemeMode.LIGHT))
        assertEquals(ThemePreference.SOLARIZED_DARK, ThemePreference.from(ThemeFamily.SOLARIZED, ThemeMode.DARK))
    }

    @Test
    fun `split-and-recompose round-trips every preference`() {
        ThemePreference.entries.forEach { pref ->
            assertEquals(pref, ThemePreference.from(pref.family, pref.mode))
        }
    }

    @Test
    fun `withFamily replaces family and preserves mode`() {
        ThemePreference.entries.forEach { pref ->
            ThemeFamily.entries.forEach { newFamily ->
                val result = pref.withFamily(newFamily)
                assertEquals(newFamily, result.family)
                assertEquals(pref.mode, result.mode)
            }
        }
    }

    @Test
    fun `withMode replaces mode and preserves family`() {
        ThemePreference.entries.forEach { pref ->
            ThemeMode.entries.forEach { newMode ->
                val result = pref.withMode(newMode)
                assertEquals(newMode, result.mode)
                assertEquals(pref.family, result.family)
            }
        }
    }

    @Test
    fun `withFamily(self) and withMode(self) are idempotent`() {
        ThemePreference.entries.forEach { pref ->
            assertEquals(pref, pref.withFamily(pref.family))
            assertEquals(pref, pref.withMode(pref.mode))
        }
    }
}
