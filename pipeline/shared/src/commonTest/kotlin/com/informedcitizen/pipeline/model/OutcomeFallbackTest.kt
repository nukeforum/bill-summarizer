package com.informedcitizen.pipeline.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** Matches the app's lenient wire config (NetworkModule / HttpClientFactory). */
private val LenientJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

private fun billJson(outcome: String): String = """
    {
      "id": "hr1234-119",
      "congress": 119,
      "type": "hr",
      "number": "1234",
      "title": "An Act",
      "sponsor": {"name": "Test Sponsor", "party": "D", "state": "XX"},
      "introduced_date": "2025-01-15",
      "latest_action": {"date": "2025-01-23", "text": "Some new kind of action."},
      "outcome": "$outcome",
      "congress_gov_url": "https://www.congress.gov/bill/119th-congress/house-bill/1234"
    }
""".trimIndent()

/**
 * Regression for #51: an outcome string this app generation doesn't know
 * must coerce to [Outcome.UNKNOWN] rather than crash deserialization. Before
 * the fix, [Bill.outcome] had no default so coerceInputValues had nothing to
 * coerce to and threw on any unrecognised value.
 */
class OutcomeFallbackTest {
    @Test fun unknown_outcome_string_coerces_to_UNKNOWN() {
        val bill = LenientJson.decodeFromString(Bill.serializer(), billJson("some_future_outcome"))
        assertEquals(Outcome.UNKNOWN, bill.outcome)
    }

    @Test fun known_outcome_strings_still_decode_exactly() {
        assertEquals(
            Outcome.ENACTED,
            LenientJson.decodeFromString(Bill.serializer(), billJson("enacted")).outcome,
        )
        assertEquals(
            Outcome.FAILED,
            LenientJson.decodeFromString(Bill.serializer(), billJson("failed")).outcome,
        )
    }

    @Test fun explicit_unknown_wire_value_decodes_to_UNKNOWN() {
        assertEquals(
            Outcome.UNKNOWN,
            LenientJson.decodeFromString(Bill.serializer(), billJson("unknown")).outcome,
        )
    }
}
