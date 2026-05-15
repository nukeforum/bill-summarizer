package com.informedcitizen.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals

class CongressTest {
    @Test
    fun year_2026_is_119th_congress() {
        assertEquals(119, congressForYear(2026))
    }

    @Test
    fun year_2025_is_119th_congress() {
        assertEquals(119, congressForYear(2025))
    }

    @Test
    fun year_2024_is_118th_congress() {
        assertEquals(118, congressForYear(2024))
    }

    @Test
    fun year_1789_is_1st_congress() {
        assertEquals(1, congressForYear(1789))
    }
}
