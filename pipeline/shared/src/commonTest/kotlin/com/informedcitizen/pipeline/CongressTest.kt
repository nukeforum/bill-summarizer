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

    @Test
    fun year_1790_is_still_1st_congress() {
        // The 1st Congress sat Mar 1789 – Mar 1791. The formula's
        // two-year-per-Congress floor keeps 1790 on the 1st.
        assertEquals(1, congressForYear(1790))
    }

    @Test
    fun year_1791_is_2nd_congress() {
        assertEquals(2, congressForYear(1791))
    }

    @Test
    fun year_2027_is_120th_congress() {
        // The off-by-one of the swearing-in date around Jan 3 is
        // accepted; the daily cron job's "one day off" was the original
        // Python comment.
        assertEquals(120, congressForYear(2027))
    }
}
