package com.informedcitizen.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StateCodesTest {
    @Test fun maps_full_state_name_to_postal_code() {
        assertEquals("CA", stateCode("California"))
        assertEquals("NY", stateCode("New York"))
        assertEquals("WV", stateCode("West Virginia"))
    }

    @Test fun passes_through_two_letter_input_uppercased() {
        assertEquals("CA", stateCode("ca"))
        assertEquals("DC", stateCode("DC"))
    }

    @Test fun maps_delegate_jurisdictions() {
        assertEquals("DC", stateCode("District of Columbia"))
        assertEquals("MP", stateCode("Northern Mariana Islands"))
        assertEquals("PR", stateCode("Puerto Rico"))
    }

    @Test fun returns_empty_for_null_or_empty_input() {
        assertEquals("", stateCode(null))
        assertEquals("", stateCode(""))
    }

    @Test fun unknown_name_falls_back_and_warns() {
        var warning: String? = null
        val result = stateCode("Atlantis") { warning = it }
        assertEquals("AT", result)
        assertTrue(warning?.contains("Atlantis") == true)
    }
}
