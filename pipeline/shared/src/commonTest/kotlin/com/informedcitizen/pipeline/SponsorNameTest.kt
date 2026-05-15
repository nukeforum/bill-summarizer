package com.informedcitizen.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals

class SponsorNameTest {
    @Test
    fun strips_senate_party_state_suffix() {
        assertEquals(
            "Sen. Peters, Gary C.",
            cleanSponsorName("Sen. Peters, Gary C. [D-MI]"),
        )
    }

    @Test
    fun strips_house_party_state_district_suffix() {
        assertEquals(
            "Rep. Smith, Adrian",
            cleanSponsorName("Rep. Smith, Adrian [R-NE-3]"),
        )
    }

    @Test
    fun passes_through_name_without_suffix() {
        assertEquals("Sen. Sanders, Bernard", cleanSponsorName("Sen. Sanders, Bernard"))
    }

    @Test
    fun passes_through_unknown_fallback() {
        assertEquals("Unknown", cleanSponsorName("Unknown"))
    }
}
