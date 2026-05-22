package com.informedcitizen.pipeline.fetch

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun parseJsonObject(s: String): JsonObject =
    Json.parseToJsonElement(s) as JsonObject

class MemberParserTest {
    @Test fun parses_house_member_with_full_state_and_district() {
        val raw = parseJsonObject(
            """
            {
              "bioguideId": "S001234",
              "directOrderName": "Adrian Smith",
              "partyName": "Republican",
              "state": "Nebraska",
              "district": 3,
              "terms": [{"chamber": "House of Representatives"}],
              "depiction": {"imageUrl": "https://x/photo.jpg"},
              "officialUrl": "https://smith.house.gov",
              "sponsoredLegislation": {"count": 12},
              "cosponsoredLegislation": {"count": 200},
              "addressInformation": {
                "officeAddress": "123 Cannon HOB",
                "phoneNumber": "(202) 225-0000"
              }
            }
            """.trimIndent(),
        )
        val member = parseMemberSummary(raw)
        assertEquals("S001234", member.bioguideId)
        assertEquals("Adrian Smith", member.name)
        assertEquals("R", member.party)
        assertEquals("NE", member.state)
        assertEquals(3, member.district)
        assertEquals("house", member.chamber)
        assertEquals("https://x/photo.jpg", member.photoUrl)
        assertEquals("https://smith.house.gov", member.officialUrl)
        assertEquals(12, member.sponsoredCount)
        assertEquals(200, member.cosponsoredCount)
        assertEquals("123 Cannon HOB", member.address)
        assertEquals("(202) 225-0000", member.phone)
        // contact_form / website are filled in later by the orchestrator.
        assertNull(member.contactForm)
        assertNull(member.website)
    }

    @Test fun parses_senator_with_two_letter_state_and_null_district() {
        val raw = parseJsonObject(
            """
            {
              "bioguideId": "S002",
              "name": "Bernard Sanders",
              "party": "Independent",
              "state": "VT",
              "terms": [{"chamber": "Senate"}]
            }
            """.trimIndent(),
        )
        val member = parseMemberSummary(raw)
        assertEquals("I", member.party)
        assertEquals("VT", member.state)
        assertNull(member.district)
        assertEquals("senate", member.chamber)
        assertEquals("Bernard Sanders", member.name)
    }

    @Test fun normalizes_null_house_district_to_zero_for_at_large() {
        val raw = parseJsonObject(
            """
            {
              "bioguideId": "X1",
              "name": "Rep. AtLarge",
              "partyName": "Democratic",
              "state": "Wyoming",
              "terms": [{"chamber": "House"}]
            }
            """.trimIndent(),
        )
        // House chamber + null district → 0 (at-large convention).
        assertEquals(0, parseMemberSummary(raw).district)
    }

    @Test fun keeps_senate_district_null_when_missing() {
        val raw = parseJsonObject(
            """
            {
              "bioguideId": "S1",
              "name": "Sen. None",
              "partyName": "Republican",
              "state": "Texas",
              "terms": [{"chamber": "Senate"}]
            }
            """.trimIndent(),
        )
        assertNull(parseMemberSummary(raw).district)
    }

    @Test fun chamber_unknown_when_terms_missing() {
        val raw = parseJsonObject(
            """{"bioguideId": "X", "name": "Q"}""",
        )
        assertEquals("unknown", parseMemberSummary(raw).chamber)
    }

    @Test fun handles_legislators_json_term_shape() {
        // legislators-current.json (gh-pages) uses `type: "rep"`/`"sen"`
        // rather than the Congress.gov `chamber:` field. chamberFromTerms
        // accepts both shapes via the substring/exact-match fallback.
        val raw = parseJsonObject(
            """{"terms": [{"type": "rep"}, {"type": "rep"}]}""",
        )
        assertEquals("house", chamberFromTerms(raw["terms"]))
        val senRaw = parseJsonObject("""{"terms": [{"type": "sen"}]}""")
        assertEquals("senate", chamberFromTerms(senRaw["terms"]))
    }

    @Test fun parses_member_legislation_item_with_policy_object() {
        val raw = parseJsonObject(
            """
            {
              "type": "HR",
              "number": "1234",
              "congress": 119,
              "latestTitle": "A Bill",
              "introducedDate": "2026-01-15",
              "latestAction": {"actionDate": "2026-04-10T00:00:00Z", "text": "Referred."},
              "policyArea": {"name": "Health"}
            }
            """.trimIndent(),
        )
        val item = parseMemberLegislationItem(raw)
        assertEquals("hr1234-119", item.id)
        assertEquals("hr", item.type)
        assertEquals("1234", item.number)
        assertEquals(119, item.congress)
        assertEquals("A Bill", item.title)
        assertEquals("2026-01-15", item.introducedDate)
        assertEquals("2026-04-10", item.latestAction.date)
        assertEquals("Referred.", item.latestAction.text)
        assertEquals("Health", item.policyArea)
    }

    @Test fun parses_member_legislation_item_with_string_policy_area() {
        val raw = parseJsonObject(
            """{"type":"s","number":"1","congress":119,"latestAction":{},"policyArea":"Defense"}""",
        )
        val item = parseMemberLegislationItem(raw)
        assertEquals("Defense", item.policyArea)
    }

    @Test fun parses_member_legislation_item_with_missing_optional_fields() {
        val raw = parseJsonObject("""{"type":"s","number":"42","congress":119}""")
        val item = parseMemberLegislationItem(raw)
        assertEquals("s42-119", item.id)
        assertEquals("", item.title)
        assertEquals("", item.introducedDate)
        assertEquals("", item.latestAction.date)
        assertEquals("", item.latestAction.text)
        assertNull(item.policyArea)
    }
}
