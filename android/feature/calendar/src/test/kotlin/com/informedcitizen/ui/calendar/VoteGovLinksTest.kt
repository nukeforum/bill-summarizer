package com.informedcitizen.ui.calendar

import com.informedcitizen.pipeline.KNOWN_STATE_CODES
import com.informedcitizen.pipeline.model.Member
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoteGovLinksTest {

    private fun member(state: String) = Member(
        bioguideId = "X000000",
        name = "Rep $state",
        party = "I",
        state = state,
        chamber = "house",
    )

    /** Every USPS code the app can derive from a saved rep's state maps to a real per-state page. */
    @Test
    fun registrationUrl_maps_every_known_code_to_a_register_slug() {
        val expected = mapOf(
            "AL" to "alabama", "AK" to "alaska", "AZ" to "arizona", "AR" to "arkansas",
            "CA" to "california", "CO" to "colorado", "CT" to "connecticut", "DE" to "delaware",
            "FL" to "florida", "GA" to "georgia", "HI" to "hawaii", "ID" to "idaho",
            "IL" to "illinois", "IN" to "indiana", "IA" to "iowa", "KS" to "kansas",
            "KY" to "kentucky", "LA" to "louisiana", "ME" to "maine", "MD" to "maryland",
            "MA" to "massachusetts", "MI" to "michigan", "MN" to "minnesota", "MS" to "mississippi",
            "MO" to "missouri", "MT" to "montana", "NE" to "nebraska", "NV" to "nevada",
            "NH" to "new-hampshire", "NJ" to "new-jersey", "NM" to "new-mexico", "NY" to "new-york",
            "NC" to "north-carolina", "ND" to "north-dakota", "OH" to "ohio", "OK" to "oklahoma",
            "OR" to "oregon", "PA" to "pennsylvania", "RI" to "rhode-island", "SC" to "south-carolina",
            "SD" to "south-dakota", "TN" to "tennessee", "TX" to "texas", "UT" to "utah",
            "VT" to "vermont", "VA" to "virginia", "WA" to "washington", "WV" to "west-virginia",
            "WI" to "wisconsin", "WY" to "wyoming",
            "DC" to "district-of-columbia", "AS" to "american-samoa", "GU" to "guam",
            "MP" to "northern-mariana-islands", "PR" to "puerto-rico", "VI" to "virgin-islands",
        )
        for ((code, slug) in expected) {
            assertEquals(code, "https://vote.gov/register/$slug", VoteGovLinks.registrationUrl(code))
        }
    }

    /** Guards drift: the map covers exactly the KNOWN_STATE_CODES a saved rep can carry. */
    @Test
    fun registrationUrl_covers_every_known_state_code() {
        for (code in KNOWN_STATE_CODES) {
            assertTrue(
                "no vote.gov page for known code $code",
                VoteGovLinks.registrationUrl(code).startsWith("https://vote.gov/register/"),
            )
        }
    }

    @Test
    fun registrationUrl_multi_word_slugs_are_hyphenated() {
        assertEquals("https://vote.gov/register/new-york", VoteGovLinks.registrationUrl("NY"))
        assertEquals("https://vote.gov/register/district-of-columbia", VoteGovLinks.registrationUrl("DC"))
        assertEquals("https://vote.gov/register/american-samoa", VoteGovLinks.registrationUrl("AS"))
    }

    @Test
    fun registrationUrl_is_case_and_whitespace_insensitive() {
        assertEquals("https://vote.gov/register/ohio", VoteGovLinks.registrationUrl("oh"))
        assertEquals("https://vote.gov/register/ohio", VoteGovLinks.registrationUrl(" OH "))
    }

    @Test
    fun registrationUrl_falls_back_to_root_for_null_blank_or_unknown() {
        assertEquals(VoteGovLinks.ROOT_URL, VoteGovLinks.registrationUrl(null))
        assertEquals(VoteGovLinks.ROOT_URL, VoteGovLinks.registrationUrl(""))
        assertEquals(VoteGovLinks.ROOT_URL, VoteGovLinks.registrationUrl("  "))
        assertEquals(VoteGovLinks.ROOT_URL, VoteGovLinks.registrationUrl("ZZ"))
        assertEquals(VoteGovLinks.ROOT_URL, VoteGovLinks.registrationUrl("Ohio"))
    }

    @Test
    fun registrationStateCode_returns_the_single_distinct_state() {
        val reps = listOf(member("OH"), member("OH"))
        assertEquals("OH", VoteGovLinks.registrationStateCode(reps))
        // end-to-end: a single-state user reaches their page
        assertEquals(
            "https://vote.gov/register/ohio",
            VoteGovLinks.registrationUrl(VoteGovLinks.registrationStateCode(reps)),
        )
    }

    @Test
    fun registrationStateCode_is_null_for_empty_or_multi_state() {
        assertNull(VoteGovLinks.registrationStateCode(emptyList()))
        assertNull(VoteGovLinks.registrationStateCode(listOf(member("OH"), member("TX"))))
        // multi-state user lands on the national front door
        assertEquals(
            VoteGovLinks.ROOT_URL,
            VoteGovLinks.registrationUrl(
                VoteGovLinks.registrationStateCode(listOf(member("OH"), member("TX"))),
            ),
        )
    }

    @Test
    fun registrationStateCode_ignores_blank_states() {
        assertEquals("OH", VoteGovLinks.registrationStateCode(listOf(member("OH"), member("  "))))
        assertNull(VoteGovLinks.registrationStateCode(listOf(member(""), member("   "))))
    }
}
