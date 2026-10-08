package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.model.Member

/**
 * House seat counts by state for the 2020-census apportionment (in effect
 * 2023-2033, i.e. the 118th-121st Congresses). At-large states (AK, DE, ND,
 * SD, VT, WY) and delegate jurisdictions (DC, AS, GU, MP, PR, VI) are
 * deliberately excluded: their lone seat is published with district=0, not
 * a 1..N range, so they fall outside what this table is for (see
 * [findVacantHouseDistricts] below).
 *
 * This exists solely to tell a *real* currently-vacant seat (a member who
 * resigned, died, or was expelled, with no successor sworn in yet — which
 * Congress.gov's currentMember=true filter, mirrored by [listMembers],
 * correctly omits) apart from an actual fetch/parse defect: a district
 * absent from the fetched members that IS in this table is an expected
 * vacancy, not a bug. Mirrors Python `_common.HOUSE_APPORTIONMENT` and the
 * Android `LocationPickerViewModel`'s own `HOUSE_DISTRICT_COUNTS` fallback
 * table — keep all three in sync if apportionment ever changes (next
 * scheduled change: the 2030 census).
 */
val HOUSE_APPORTIONMENT: Map<String, Int> = mapOf(
    "AL" to 7, "AZ" to 9, "AR" to 4, "CA" to 52, "CO" to 8, "CT" to 5,
    "FL" to 28, "GA" to 14, "HI" to 2, "ID" to 2, "IL" to 17, "IN" to 9,
    "IA" to 4, "KS" to 4, "KY" to 6, "LA" to 6, "ME" to 2, "MD" to 8,
    "MA" to 9, "MI" to 13, "MN" to 8, "MS" to 4, "MO" to 8, "MT" to 2,
    "NE" to 3, "NV" to 4, "NH" to 2, "NJ" to 12, "NM" to 3, "NY" to 26,
    "NC" to 14, "OH" to 15, "OK" to 5, "OR" to 6, "PA" to 17, "RI" to 2,
    "SC" to 7, "TN" to 9, "TX" to 38, "UT" to 4, "VA" to 11, "WA" to 10,
    "WV" to 2, "WI" to 8,
)

/**
 * Return the `(state, district)` pairs [HOUSE_APPORTIONMENT] expects but
 * that have no current House member in [members]. Mirrors Python
 * `_common.find_vacant_house_districts`.
 *
 * Sorted by state then district for stable, diffable output. An empty
 * result is the healthy case; a non-empty one is not by itself a pipeline
 * bug — cross-check the reported seats against Congress.gov / the Clerk's
 * vacancy list before assuming otherwise (#106).
 */
fun findVacantHouseDistricts(members: List<Member>): List<Pair<String, Int>> {
    val present = mutableMapOf<String, MutableSet<Int>>()
    for (m in members) {
        if (m.chamber != "house") continue
        val district = m.district
        if (m.state.isEmpty() || district == null || district <= 0) continue
        present.getOrPut(m.state) { mutableSetOf() }.add(district)
    }
    return HOUSE_APPORTIONMENT.entries
        .flatMap { (state, count) ->
            (1..count)
                .filter { district -> district !in present.getOrElse(state) { emptySet() } }
                .map { district -> state to district }
        }
        .sortedWith(compareBy({ it.first }, { it.second }))
}
