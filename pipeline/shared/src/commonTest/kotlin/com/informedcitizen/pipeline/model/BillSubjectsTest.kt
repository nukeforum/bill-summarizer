package com.informedcitizen.pipeline.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Matches the app's lenient wire config (NetworkModule / HttpClientFactory). */
private val LenientJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/**
 * A bill JSON with an optional `subjects` key. The key is omitted entirely
 * when [subjectsLine] is null so the "field absent" path is exercised too.
 */
private fun billJson(subjectsLine: String? = null): String {
    val line = if (subjectsLine == null) "" else "\"subjects\": $subjectsLine,\n      "
    return """
        {
          "id": "hr1234-119",
          "congress": 119,
          "type": "hr",
          "number": "1234",
          "title": "An Act",
          "sponsor": {"name": "Test Sponsor", "party": "D", "state": "XX"},
          "introduced_date": "2025-01-15",
          "latest_action": {"date": "2025-01-23", "text": "Referred to committee."},
          ${line}"congress_gov_url": "https://www.congress.gov/bill/119th-congress/house-bill/1234"
        }
    """.trimIndent()
}

/**
 * Issue #28: the additive [Bill.subjects] legislative-subject terms field.
 *
 * The field is additive and defaulted to the empty list, mirroring [Bill.votes]:
 *  - a manifest that omits `subjects` (every manifest published before #28)
 *    decodes to an empty list — no behavioural change for existing data;
 *  - a manifest that carries `subjects` decodes into the multi-value list #10's
 *    topical filtering will read;
 *  - because it is a new *key*, any app build released before this field existed
 *    drops it via `ignoreUnknownKeys`, so publishing it is safe on old installs.
 */
class BillSubjectsTest {
    @Test fun absent_subjects_field_decodes_to_empty_list() {
        val bill = LenientJson.decodeFromString(Bill.serializer(), billJson(subjectsLine = null))
        assertTrue(bill.subjects.isEmpty())
    }

    @Test fun empty_subjects_array_decodes_to_empty_list() {
        val bill = LenientJson.decodeFromString(Bill.serializer(), billJson("[]"))
        assertTrue(bill.subjects.isEmpty())
    }

    @Test fun multi_value_subjects_decode_in_order() {
        val json = billJson("""["Firearms and explosives", "Intelligence activities"]""")
        val bill = LenientJson.decodeFromString(Bill.serializer(), json)
        assertEquals(listOf("Firearms and explosives", "Intelligence activities"), bill.subjects)
    }

    @Test fun subjects_survive_round_trip() {
        val original = LenientJson.decodeFromString(
            Bill.serializer(),
            billJson("""["Health", "Taxation"]"""),
        )
        val reencoded = LenientJson.encodeToString(Bill.serializer(), original)
        val decoded = LenientJson.decodeFromString(Bill.serializer(), reencoded)
        assertEquals(listOf("Health", "Taxation"), decoded.subjects)
    }
}
