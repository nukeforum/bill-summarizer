package com.informedcitizen.pipeline.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

class MemberTest {
    @Test fun decodes_next_election_year_from_wire() {
        val json = """
            {
              "bioguide_id": "H001234",
              "name": "Rep. Doe, Jane",
              "party": "D",
              "state": "CA",
              "district": 5,
              "chamber": "house",
              "next_election_year": 2026
            }
        """.trimIndent()
        val member = LenientJson.decodeFromString(Member.serializer(), json)
        assertEquals(2026, member.nextElectionYear)
    }

    @Test fun absent_next_election_year_defaults_to_null() {
        // The pipeline omits the key entirely for ambiguous/appointed cases;
        // lenient parsing must read that as null, never crash (data-first ship).
        val json = """
            {
              "bioguide_id": "S009999",
              "name": "Sen. Roe, Sam",
              "party": "R",
              "state": "TX",
              "chamber": "senate"
            }
        """.trimIndent()
        val member = LenientJson.decodeFromString(Member.serializer(), json)
        assertNull(member.nextElectionYear)
    }

    @Test fun roundtrips_next_election_year_through_publish_config() {
        val member = Member(
            bioguideId = "H001234",
            name = "Rep. Doe, Jane",
            party = "D",
            state = "CA",
            district = 5,
            chamber = "house",
            nextElectionYear = 2026,
        )
        val encoded = PublishJson.encodeToString(Member.serializer(), member)
        assertTrue("\"next_election_year\": 2026" in encoded)
        assertEquals(member, PublishJson.decodeFromString(Member.serializer(), encoded))
    }
}
