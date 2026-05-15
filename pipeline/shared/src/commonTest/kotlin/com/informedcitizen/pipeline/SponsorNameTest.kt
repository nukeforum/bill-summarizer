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

    @Test
    fun empty_string_is_returned_unchanged() {
        assertEquals("", cleanSponsorName(""))
    }

    @Test
    fun trims_trailing_whitespace_without_a_suffix() {
        assertEquals("Sen. Sanders, Bernard", cleanSponsorName("Sen. Sanders, Bernard  "))
    }

    @Test
    fun trims_leading_whitespace_without_a_suffix() {
        assertEquals("Sen. Sanders, Bernard", cleanSponsorName("  Sen. Sanders, Bernard"))
    }

    @Test
    fun does_not_strip_bracket_group_that_is_not_at_end_of_string() {
        assertEquals(
            "Sen. X [D-MI] Jr.",
            cleanSponsorName("Sen. X [D-MI] Jr."),
        )
    }

    @Test
    fun lowercase_party_state_suffix_is_not_recognized() {
        // The Congress.gov suffix is always uppercase. Lowercase should
        // pass through so we never silently mis-clean a name we don't
        // recognize.
        assertEquals("Sen. X [d-mi]", cleanSponsorName("Sen. X [d-mi]"))
    }

    @Test
    fun strips_only_the_trailing_bracket_when_multiple_present() {
        // Two suffixes in a row would be malformed data, but the regex
        // pins to `$` so we strip the rightmost and leave the other.
        assertEquals(
            "Sen. X [D-MI]",
            cleanSponsorName("Sen. X [D-MI] [R-NE-3]"),
        )
    }

    @Test
    fun preserves_unicode_characters_in_name() {
        assertEquals(
            "Rep. Ocasio-Cortez, Alexandría",
            cleanSponsorName("Rep. Ocasio-Cortez, Alexandría [D-NY-14]"),
        )
    }

    @Test
    fun at_large_district_zero_is_recognized() {
        // At-large delegates get district 0 in normalized form.
        assertEquals(
            "Del. Sablan, Gregorio",
            cleanSponsorName("Del. Sablan, Gregorio [D-MP-0]"),
        )
    }
}
