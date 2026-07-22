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

private fun voteFixture(): RollCallVote = RollCallVote(
    id = "house-119-1-17",
    congress = 119,
    chamber = Chamber.HOUSE,
    session = 1,
    rollNumber = 17,
    date = "2025-01-23",
    question = "On Passage",
    result = "Passed",
    billId = "hr1234-119",
    totals = VoteTotals(yea = 217, nay = 215, present = 0, notVoting = 3),
    positions = listOf(
        MemberVote("A000055", VotePosition.YEA, party = "R", state = "AL"),
        MemberVote("P000197", VotePosition.NAY, party = "D", state = "CA"),
        MemberVote("S001165", VotePosition.NOT_VOTING),
    ),
    sourceUrl = "https://clerk.house.gov/Votes/202517",
)

class RollCallVoteTest {
    @Test fun decodes_published_wire_json() {
        val json = """
            {
              "id": "senate-119-2-101",
              "congress": 119,
              "chamber": "senate",
              "session": 2,
              "roll_number": 101,
              "date": "2026-03-12",
              "question": "On the Motion to Proceed",
              "result": "Agreed to",
              "bill_id": "s42-119",
              "totals": {"yea": 51, "nay": 49, "present": 0, "not_voting": 0},
              "positions": [
                {"bioguide_id": "O000174", "position": "yea", "party": "D", "state": "GA"},
                {"bioguide_id": "C001098", "position": "not_voting", "party": "R", "state": "TX"}
              ],
              "source_url": "https://www.senate.gov/legislative/LIS/roll_call_votes/vote1192/vote_119_2_00101.xml"
            }
        """.trimIndent()
        val vote = LenientJson.decodeFromString(RollCallVote.serializer(), json)
        assertEquals(Chamber.SENATE, vote.chamber)
        assertEquals(101, vote.rollNumber)
        assertEquals("s42-119", vote.billId)
        assertEquals(51, vote.totals.yea)
        assertEquals(VotePosition.NOT_VOTING, vote.positions[1].position)
    }

    @Test fun roundtrips_through_publish_config() {
        val vote = voteFixture()
        val encoded = PublishJson.encodeToString(RollCallVote.serializer(), vote)
        assertEquals(vote, PublishJson.decodeFromString(RollCallVote.serializer(), encoded))
    }

    @Test fun encodes_snake_case_wire_names() {
        val encoded = PublishJson.encodeToString(RollCallVote.serializer(), voteFixture())
        assertTrue("\"roll_number\": 17" in encoded)
        assertTrue("\"not_voting\": 3" in encoded)
        assertTrue("\"bill_id\": \"hr1234-119\"" in encoded)
        assertTrue("\"bioguide_id\": \"A000055\"" in encoded)
        assertTrue("\"position\": \"not_voting\"" in encoded)
        assertTrue("\"chamber\": \"house\"" in encoded)
    }

    @Test fun non_bill_vote_omits_bill_id_and_lenient_decode_defaults_it() {
        val json = """
            {
              "id": "house-119-1-2",
              "congress": 119,
              "chamber": "house",
              "session": 1,
              "roll_number": 2,
              "date": "2025-01-03",
              "question": "Election of the Speaker",
              "result": "Elected",
              "totals": {"yea": 0, "nay": 0, "present": 0, "not_voting": 0},
              "positions": []
            }
        """.trimIndent()
        val vote = LenientJson.decodeFromString(RollCallVote.serializer(), json)
        assertNull(vote.billId)
        assertNull(vote.sourceUrl)
    }

    @Test fun lenient_decode_ignores_future_fields() {
        val json = """
            {
              "id": "house-119-1-17",
              "congress": 119,
              "chamber": "house",
              "session": 1,
              "roll_number": 17,
              "date": "2025-01-23",
              "question": "On Passage",
              "result": "Passed",
              "totals": {"yea": 1, "nay": 0, "present": 0, "not_voting": 0, "abstain": 0},
              "positions": [
                {"bioguide_id": "A000055", "position": "yea", "pairing": "none"}
              ],
              "tie_breaker": "none"
            }
        """.trimIndent()
        val vote = LenientJson.decodeFromString(RollCallVote.serializer(), json)
        assertEquals(1, vote.positions.size)
        assertNull(vote.positions[0].party)
    }
}

class VotesIndexTest {
    @Test fun decodes_index_and_resolves_paths() {
        val json = """
            {
              "generated_at": "2026-07-21T00:00:00Z",
              "congress": 119,
              "vote_count": 2,
              "votes": [
                {
                  "id": "house-119-1-17",
                  "chamber": "house",
                  "session": 1,
                  "roll_number": 17,
                  "date": "2025-01-23",
                  "question": "On Passage",
                  "result": "Passed",
                  "bill_id": "hr1234-119",
                  "totals": {"yea": 217, "nay": 215, "present": 0, "not_voting": 3},
                  "path": "votes/congress119/house-1-17.json"
                },
                {
                  "id": "senate-119-1-5",
                  "chamber": "senate",
                  "session": 1,
                  "roll_number": 5,
                  "date": "2025-01-25",
                  "question": "On the Nomination",
                  "result": "Confirmed",
                  "totals": {"yea": 56, "nay": 44, "present": 0, "not_voting": 0},
                  "path": "votes/congress119/senate-1-5.json"
                }
              ]
            }
        """.trimIndent()
        val index = LenientJson.decodeFromString(VotesIndex.serializer(), json)
        assertEquals(2, index.voteCount)
        assertEquals("votes/congress119/house-1-17.json", index.votes[0].path)
        assertEquals("hr1234-119", index.votes[0].billId)
        assertNull(index.votes[1].billId)
    }

    @Test fun roundtrips_through_publish_config() {
        val index = VotesIndex(
            generatedAt = "2026-07-21T00:00:00Z",
            congress = 119,
            voteCount = 1,
            votes = listOf(
                VoteRef(
                    id = "house-119-1-17",
                    chamber = Chamber.HOUSE,
                    session = 1,
                    rollNumber = 17,
                    date = "2025-01-23",
                    question = "On Passage",
                    result = "Passed",
                    billId = "hr1234-119",
                    totals = VoteTotals(217, 215, 0, 3),
                    path = "votes/congress119/house-1-17.json",
                ),
            ),
        )
        val encoded = PublishJson.encodeToString(VotesIndex.serializer(), index)
        assertEquals(index, PublishJson.decodeFromString(VotesIndex.serializer(), encoded))
    }
}
