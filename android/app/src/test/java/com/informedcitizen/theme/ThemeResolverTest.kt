package com.informedcitizen.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeResolverTest {

    @Test
    fun `MATERIAL_SYSTEM follows systemDark`() {
        assertEquals(ThemeFamily.MATERIAL to false, resolve(ThemePreference.MATERIAL_SYSTEM, systemDark = false))
        assertEquals(ThemeFamily.MATERIAL to true, resolve(ThemePreference.MATERIAL_SYSTEM, systemDark = true))
    }

    @Test
    fun `MATERIAL_LIGHT is always Material light`() {
        assertEquals(ThemeFamily.MATERIAL to false, resolve(ThemePreference.MATERIAL_LIGHT, systemDark = false))
        assertEquals(ThemeFamily.MATERIAL to false, resolve(ThemePreference.MATERIAL_LIGHT, systemDark = true))
    }

    @Test
    fun `MATERIAL_DARK is always Material dark`() {
        assertEquals(ThemeFamily.MATERIAL to true, resolve(ThemePreference.MATERIAL_DARK, systemDark = false))
        assertEquals(ThemeFamily.MATERIAL to true, resolve(ThemePreference.MATERIAL_DARK, systemDark = true))
    }

    @Test
    fun `SOLARIZED_SYSTEM follows systemDark`() {
        assertEquals(ThemeFamily.SOLARIZED to false, resolve(ThemePreference.SOLARIZED_SYSTEM, systemDark = false))
        assertEquals(ThemeFamily.SOLARIZED to true, resolve(ThemePreference.SOLARIZED_SYSTEM, systemDark = true))
    }

    @Test
    fun `SOLARIZED_LIGHT is always Solarized light`() {
        assertEquals(ThemeFamily.SOLARIZED to false, resolve(ThemePreference.SOLARIZED_LIGHT, systemDark = false))
        assertEquals(ThemeFamily.SOLARIZED to false, resolve(ThemePreference.SOLARIZED_LIGHT, systemDark = true))
    }

    @Test
    fun `SOLARIZED_DARK is always Solarized dark`() {
        assertEquals(ThemeFamily.SOLARIZED to true, resolve(ThemePreference.SOLARIZED_DARK, systemDark = false))
        assertEquals(ThemeFamily.SOLARIZED to true, resolve(ThemePreference.SOLARIZED_DARK, systemDark = true))
    }
}
