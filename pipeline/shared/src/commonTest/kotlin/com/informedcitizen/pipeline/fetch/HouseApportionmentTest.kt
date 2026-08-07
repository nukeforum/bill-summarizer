package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.model.Member
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Mirrors Python `test_house_apportionment.py`. findVacantHouseDistricts
 * tells a real currently-vacant seat (no sworn-in member, correctly omitted
 * by Congress.gov's currentMember=true filter) apart from an actual
 * fetch/parse defect (#106). It is not itself a "fix" for a vacancy — a
 * vacant seat is expected to stay absent from the fetched members until
 * Congress.gov reports a successor — it is the audit that makes that fact
 * visible instead of silent.
 */
class HouseApportionmentTest {

    private fun houseMember(state: String, district: Int, bid: String = "X000001") = Member(
        bioguideId = bid,
        name = "Test Member",
        party = "D",
        state = state,
        district = district,
        chamber = "house",
    )

    private fun senateMember(state: String, bid: String = "X000002") = Member(
        bioguideId = bid,
        name = "Test Senator",
        party = "D",
        state = state,
        district = null,
        chamber = "senate",
    )

    /** Every apportioned seat filled, except (state, district) pairs in omit. */
    private fun fullHouse(omit: Set<Pair<String, Int>> = emptySet()): List<Member> =
        HOUSE_APPORTIONMENT.entries.flatMap { (state, count) ->
            (1..count)
                .filter { district -> (state to district) !in omit }
                .map { district -> houseMember(state, district, bid = "$state$district") }
        }

    @Test fun no_vacancies_when_every_apportioned_seat_is_present() {
        assertEquals(emptyList(), findVacantHouseDistricts(fullHouse()))
    }

    @Test fun reports_a_single_missing_district_ga13() {
        // Regression for #106: GA has 14 districts; omitting GA-13 (the seat
        // David Scott held until his death) must surface as a vacancy.
        val members = fullHouse(omit = setOf("GA" to 13))
        assertEquals(listOf("GA" to 13), findVacantHouseDistricts(members))
    }

    @Test fun reports_multiple_states_sorted_by_state_then_district() {
        val members = fullHouse(
            omit = setOf("GA" to 13, "CA" to 14, "TX" to 23, "FL" to 20),
        )
        assertEquals(
            listOf("CA" to 14, "FL" to 20, "GA" to 13, "TX" to 23),
            findVacantHouseDistricts(members),
        )
    }

    @Test fun ignores_senators_and_at_large_delegate_district_zero() {
        val members = fullHouse() + senateMember("GA") + houseMember("VT", 0, bid = "VT0")
        assertEquals(emptyList(), findVacantHouseDistricts(members))
    }

    @Test fun apportionment_table_excludes_at_large_and_delegate_jurisdictions() {
        val atLargeAndDelegates = setOf("AK", "DE", "ND", "SD", "VT", "WY", "DC", "AS", "GU", "MP", "PR", "VI")
        assertTrue(atLargeAndDelegates.none { it in HOUSE_APPORTIONMENT })
        // 50 states minus the 6 at-large ones = 44 multi-district states.
        assertEquals(44, HOUSE_APPORTIONMENT.size)
    }
}
