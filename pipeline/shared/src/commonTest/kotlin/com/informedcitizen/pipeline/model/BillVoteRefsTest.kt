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

/** Matches ManifestJson's published-output config. */
private val PublishJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    encodeDefaults = true
    explicitNulls = true
}

private fun billFixture(votes: List<VoteRef> = emptyList()): Bill = Bill(
    id = "hr1234-119",
    congress = 119,
    type = "hr",
    number = "1234",
    title = "An Act",
    sponsor = Sponsor(name = "Test Sponsor", party = "D", state = "XX"),
    introducedDate = "2025-01-15",
    latestAction = Action(date = "2025-01-23", text = "Passed the House."),
    outcome = Outcome.PASSED_HOUSE,
    congressGovUrl = "https://www.congress.gov/bill/119th-congress/house-bill/1234",
    votes = votes,
)

private fun voteRefFixture(): VoteRef = VoteRef(
    id = "house-119-1-17",
    chamber = Chamber.HOUSE,
    session = 1,
    rollNumber = 17,
    date = "2025-01-23",
    question = "On Passage",
    result = "Passed",
    billId = "hr1234-119",
    totals = VoteTotals(yea = 217, nay = 215, present = 0, notVoting = 3),
    path = "votes/congress119/house-1-17.json",
)

class BillVoteRefsTest {
    @Test fun pre_votes_manifest_json_decodes_with_empty_votes() {
        // The exact shape published before the votes field existed — released
        // apps and old manifests must keep working unchanged.
        val json = """
            {
              "id": "hr1234-119",
              "congress": 119,
              "type": "hr",
              "number": "1234",
              "title": "An Act",
              "short_title": null,
              "sponsor": {"name": "Test Sponsor", "party": "D", "state": "XX"},
              "introduced_date": "2025-01-15",
              "latest_action": {"date": "2025-01-23", "text": "Passed the House."},
              "outcome": "passed_house",
              "policy_area": null,
              "summary_crs": null,
              "text_url_html": null,
              "text_url_xml": null,
              "text_url_pdf": null,
              "congress_gov_url": "https://www.congress.gov/bill/119th-congress/house-bill/1234"
            }
        """.trimIndent()
        val bill = LenientJson.decodeFromString(Bill.serializer(), json)
        assertEquals(emptyList(), bill.votes)
    }

    @Test fun decodes_votes_with_totals_and_path() {
        val json = """
            {
              "id": "hr1234-119",
              "congress": 119,
              "type": "hr",
              "number": "1234",
              "title": "An Act",
              "sponsor": {"name": "Test Sponsor", "party": "D", "state": "XX"},
              "introduced_date": "2025-01-15",
              "latest_action": {"date": "2025-01-23", "text": "Passed the House."},
              "outcome": "passed_house",
              "congress_gov_url": "https://www.congress.gov/bill/119th-congress/house-bill/1234",
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
                }
              ]
            }
        """.trimIndent()
        val bill = LenientJson.decodeFromString(Bill.serializer(), json)
        assertEquals(1, bill.votes.size)
        assertEquals(217, bill.votes[0].totals.yea)
        assertEquals("votes/congress119/house-1-17.json", bill.votes[0].path)
    }

    @Test fun publish_config_always_emits_votes_key() {
        // encodeDefaults = true means Kotlin always writes "votes": [] — the
        // Python pipeline mirrors this by always setting the key, so the two
        // outputs stay byte-comparable.
        val encoded = PublishJson.encodeToString(Bill.serializer(), billFixture())
        assertTrue("\"votes\": []" in encoded)
    }

    @Test fun roundtrips_votes_through_publish_config() {
        val bill = billFixture(votes = listOf(voteRefFixture()))
        val encoded = PublishJson.encodeToString(Bill.serializer(), bill)
        assertEquals(bill, PublishJson.decodeFromString(Bill.serializer(), encoded))
        assertTrue("\"roll_number\": 17" in encoded)
    }
}
