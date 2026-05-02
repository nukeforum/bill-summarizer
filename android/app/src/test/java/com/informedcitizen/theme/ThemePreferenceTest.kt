package com.informedcitizen.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePreferenceTest {

    @Test
    fun `fromStored round-trips every enum name`() {
        ThemePreference.entries.forEach { pref ->
            assertEquals(pref, ThemePreference.fromStored(pref.name))
        }
    }

    @Test
    fun `fromStored returns DEFAULT for null`() {
        assertEquals(ThemePreference.DEFAULT, ThemePreference.fromStored(null))
    }

    @Test
    fun `fromStored returns DEFAULT for unknown string`() {
        assertEquals(ThemePreference.DEFAULT, ThemePreference.fromStored("garbage"))
        assertEquals(ThemePreference.DEFAULT, ThemePreference.fromStored(""))
    }

    @Test
    fun `DEFAULT is SOLARIZED_SYSTEM`() {
        assertEquals(ThemePreference.SOLARIZED_SYSTEM, ThemePreference.DEFAULT)
    }
}
