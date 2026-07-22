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

private fun shardFixture(): MemberVotes = MemberVotes(
    generatedAt = "2026-07-22T00:00:00Z",
    bioguideId = "A000382",
    voteCount = 2,
    votes = listOf(
        MemberVoteRow(
            voteId = "senate-119-1-618",
            congress = 119,
            date = "2025-11-10",
            question = "On Passage of the Bill",
            result = "Passed",
            position = VotePosition.NAY,
            billId = "hr5371-119",
            type = "hr",
            number = "5371",
            shortTitle = "Continuing Appropriations Act, 2026",
        ),
        MemberVoteRow(
            voteId = "senate-119-1-5",
            congress = 119,
            date = "2025-01-25",
            question = "On the Nomination",
            result = "Confirmed",
            position = VotePosition.YEA,
        ),
    ),
)

class MemberVotesTest {
    @Test fun decodes_published_wire_json() {
        val json = """
            {
              "generated_at": "2026-07-22T00:00:00Z",
              "bioguide_id": "A000382",
              "vote_count": 2,
              "votes": [
                {
                  "vote_id": "house-119-1-17",
                  "congress": 119,
                  "date": "2025-01-16",
                  "question": "On Passage",
                  "result": "Passed",
                  "position": "yea",
                  "bill_id": "hr30-119",
                  "type": "hr",
                  "number": "30",
                  "short_title": "Laken Riley Act"
                },
                {
                  "vote_id": "house-119-1-1",
                  "congress": 119,
                  "date": "2025-01-03",
                  "question": "Call by States",
                  "result": "Passed",
                  "position": "not_voting",
                  "bill_id": null,
                  "type": null,
                  "number": null,
                  "short_title": null
                }
              ]
            }
        """.trimIndent()
        val shard = LenientJson.decodeFromString(MemberVotes.serializer(), json)
        assertEquals("A000382", shard.bioguideId)
        assertEquals(2, shard.voteCount)
        assertEquals("house-119-1-17", shard.votes[0].voteId)
        assertEquals(VotePosition.YEA, shard.votes[0].position)
        assertEquals("Laken Riley Act", shard.votes[0].shortTitle)
        assertEquals(VotePosition.NOT_VOTING, shard.votes[1].position)
        assertNull(shard.votes[1].billId)
    }

    @Test fun roundtrips_through_publish_config() {
        val shard = shardFixture()
        val encoded = PublishJson.encodeToString(MemberVotes.serializer(), shard)
        assertEquals(shard, PublishJson.decodeFromString(MemberVotes.serializer(), encoded))
    }

    @Test fun encodes_snake_case_wire_names() {
        val encoded = PublishJson.encodeToString(MemberVotes.serializer(), shardFixture())
        assertTrue("\"bioguide_id\": \"A000382\"" in encoded)
        assertTrue("\"vote_count\": 2" in encoded)
        assertTrue("\"vote_id\": \"senate-119-1-618\"" in encoded)
        assertTrue("\"bill_id\": \"hr5371-119\"" in encoded)
        assertTrue("\"short_title\": \"Continuing Appropriations Act, 2026\"" in encoded)
        assertTrue("\"position\": \"nay\"" in encoded)
        // Non-bill rows still publish the bill keys as explicit nulls.
        assertTrue("\"bill_id\": null" in encoded)
        assertTrue("\"short_title\": null" in encoded)
    }

    @Test fun lenient_decode_defaults_missing_bill_fields_and_ignores_future_keys() {
        val json = """
            {
              "generated_at": "2026-07-22T00:00:00Z",
              "bioguide_id": "A000382",
              "vote_count": 1,
              "votes": [
                {
                  "vote_id": "senate-119-2-9",
                  "congress": 119,
                  "date": "2026-01-08",
                  "question": "On the Motion",
                  "result": "Agreed to",
                  "position": "present",
                  "pairing": "none"
                }
              ],
              "chamber_history": []
            }
        """.trimIndent()
        val shard = LenientJson.decodeFromString(MemberVotes.serializer(), json)
        assertEquals(VotePosition.PRESENT, shard.votes[0].position)
        assertNull(shard.votes[0].billId)
        assertNull(shard.votes[0].type)
        assertNull(shard.votes[0].number)
        assertNull(shard.votes[0].shortTitle)
    }
}
