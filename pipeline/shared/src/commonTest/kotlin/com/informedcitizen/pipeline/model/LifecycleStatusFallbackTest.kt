package com.informedcitizen.pipeline.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Matches the app's lenient wire config (NetworkModule / HttpClientFactory). */
private val LenientJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/**
 * A bill JSON with an optional `status` (lifecycle) key. `status` is omitted
 * entirely when [status] is null so the "field absent" path is exercised too.
 */
private fun billJson(status: String? = null): String {
    val statusLine = if (status == null) "" else "\"status\": \"$status\",\n      "
    return """
        {
          "id": "hr1234-119",
          "congress": 119,
          "type": "hr",
          "number": "1234",
          "title": "An Act",
          "sponsor": {"name": "Test Sponsor", "party": "D", "state": "XX"},
          "introduced_date": "2025-01-15",
          "latest_action": {"date": "2025-01-23", "text": "Referred to the Committee on Ways and Means."},
          ${statusLine}"congress_gov_url": "https://www.congress.gov/bill/119th-congress/house-bill/1234"
        }
    """.trimIndent()
}

/**
 * Issue #39 compatibility check for the additive lifecycle-status wire field.
 *
 * The ticket mandates verifying how already-installed app versions parse the
 * new statuses before publishing. The decision baked in here is to carry the
 * lifecycle status in a **separate `status` field** (not by widening
 * [Outcome] in place). This test locks in the two properties that make that
 * separation safe for every old install:
 *
 *  1. A manifest that carries `status` decodes fine even when the value is one
 *     the app doesn't recognise (an app build that predates a future status),
 *     coercing to null rather than crashing.
 *  2. A manifest that omits `status` (every manifest published to date) decodes
 *     to null — no behavioural change for existing data.
 *
 * The whole-field-unknown case (a truly old build that has never heard of the
 * `status` key at all) is already covered structurally by `ignoreUnknownKeys`:
 * an unknown *key* is dropped, so it can't affect old installs regardless of
 * value — which is exactly why the separate-field approach was chosen over
 * widening [Outcome], where an unknown *enum value* would crash any build that
 * shipped before the [Outcome.UNKNOWN] default (see `OutcomeFallbackTest`).
 */
class LifecycleStatusFallbackTest {
    @Test fun known_status_strings_decode_exactly() {
        assertEquals(
            LifecycleStatus.INTRODUCED,
            LenientJson.decodeFromString(Bill.serializer(), billJson("introduced")).lifecycleStatus,
        )
        assertEquals(
            LifecycleStatus.IN_COMMITTEE,
            LenientJson.decodeFromString(Bill.serializer(), billJson("in_committee")).lifecycleStatus,
        )
        assertEquals(
            LifecycleStatus.REPORTED,
            LenientJson.decodeFromString(Bill.serializer(), billJson("reported")).lifecycleStatus,
        )
    }

    @Test fun unknown_status_string_coerces_to_null_not_crash() {
        val bill = LenientJson.decodeFromString(Bill.serializer(), billJson("some_future_status"))
        assertNull(bill.lifecycleStatus)
    }

    @Test fun explicit_unknown_wire_value_decodes_to_UNKNOWN() {
        assertEquals(
            LifecycleStatus.UNKNOWN,
            LenientJson.decodeFromString(Bill.serializer(), billJson("unknown")).lifecycleStatus,
        )
    }

    @Test fun absent_status_field_decodes_to_null() {
        val bill = LenientJson.decodeFromString(Bill.serializer(), billJson(status = null))
        assertNull(bill.lifecycleStatus)
    }
}
