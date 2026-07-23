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

/** Matches ManifestJson's published-output config. */
private val PublishJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    encodeDefaults = true
    explicitNulls = true
}

private fun calendarFixture(): ElectionCalendar = ElectionCalendar(
    generatedAt = "2026-07-22T00:00:00Z",
    source = "Statutory (2 U.S.C. §7) + curated state primary dates",
    elections = listOf(
        ElectionEvent(
            state = ElectionEvent.NATIONWIDE,
            date = "2026-11-03",
            type = ElectionType.GENERAL,
            electionYear = 2026,
        ),
        ElectionEvent(
            state = "TX",
            date = "2026-03-03",
            type = ElectionType.PRIMARY,
            electionYear = 2026,
            source = "Texas Secretary of State",
        ),
    ),
)

class ElectionCalendarTest {
    @Test fun decodes_published_wire_json() {
        val json = """
            {
              "generated_at": "2026-07-22T00:00:00Z",
              "source": "Statutory + curated",
              "elections": [
                {"state": "US", "date": "2026-11-03", "type": "general", "election_year": 2026},
                {"state": "GA", "date": "2026-05-19", "type": "primary", "election_year": 2026,
                 "source": "Georgia Secretary of State"},
                {"state": "GA", "date": "2026-06-16", "type": "primary_runoff", "election_year": 2026}
              ]
            }
        """.trimIndent()
        val cal = LenientJson.decodeFromString(ElectionCalendar.serializer(), json)
        assertEquals(3, cal.elections.size)
        assertEquals(ElectionEvent.NATIONWIDE, cal.elections[0].state)
        assertEquals(ElectionType.GENERAL, cal.elections[0].type)
        assertEquals(2026, cal.elections[0].electionYear)
        assertEquals("Georgia Secretary of State", cal.elections[1].source)
        assertEquals(ElectionType.PRIMARY_RUNOFF, cal.elections[2].type)
    }

    @Test fun roundtrips_through_publish_config() {
        val cal = calendarFixture()
        val encoded = PublishJson.encodeToString(ElectionCalendar.serializer(), cal)
        assertEquals(cal, PublishJson.decodeFromString(ElectionCalendar.serializer(), encoded))
    }

    @Test fun encodes_snake_case_wire_names() {
        val encoded = PublishJson.encodeToString(ElectionCalendar.serializer(), calendarFixture())
        assertEquals(true, "\"generated_at\": \"2026-07-22T00:00:00Z\"" in encoded)
        assertEquals(true, "\"election_year\": 2026" in encoded)
        assertEquals(true, "\"type\": \"general\"" in encoded)
        assertEquals(true, "\"type\": \"primary\"" in encoded)
        assertEquals(true, "\"state\": \"US\"" in encoded)
    }

    @Test fun unknown_type_coerces_to_unknown_fallback() {
        // A future election type must not crash the app's lenient decode
        // (same guarantee as Outcome.UNKNOWN): coerceInputValues rescues it
        // because ElectionEvent.type carries a default.
        val json = """
            {
              "generated_at": "2026-07-22T00:00:00Z",
              "source": "curated",
              "elections": [
                {"state": "CA", "date": "2026-06-02", "type": "special", "election_year": 2026}
              ]
            }
        """.trimIndent()
        val cal = LenientJson.decodeFromString(ElectionCalendar.serializer(), json)
        assertEquals(ElectionType.UNKNOWN, cal.elections[0].type)
    }

    @Test fun lenient_decode_ignores_future_fields() {
        val json = """
            {
              "generated_at": "2026-07-22T00:00:00Z",
              "source": "curated",
              "horizon_days": 500,
              "elections": [
                {"state": "US", "date": "2026-11-03", "type": "general", "election_year": 2026,
                 "polls_close_local": "20:00"}
              ]
            }
        """.trimIndent()
        val cal = LenientJson.decodeFromString(ElectionCalendar.serializer(), json)
        assertEquals(1, cal.elections.size)
        assertEquals("2026-11-03", cal.elections[0].date)
    }
}
