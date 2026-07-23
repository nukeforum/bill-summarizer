package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.model.Action
import com.informedcitizen.pipeline.model.Member
import com.informedcitizen.pipeline.model.MemberLegislationItem
import com.informedcitizen.pipeline.stateCode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pull the chamber off the most recent term. Mirrors Python
 * `_common._chamber_from_terms`. Terms may be absent (e.g. when a
 * Congress.gov detail response omits them) — defaults to `"unknown"`.
 *
 * The legislators-current.json shape (terms[].type ∈ "rep"/"sen") and
 * the Congress.gov detail shape (terms[].chamber ∈ "House of
 * Representatives"/"Senate") both produce a substring match here.
 */
internal fun chamberFromTerms(terms: JsonElement?): String {
    val array = terms as? JsonArray ?: return "unknown"
    if (array.isEmpty()) return "unknown"
    val last = array.last() as? JsonObject ?: return "unknown"
    val chamber = (last.stringField("chamber") ?: last.stringField("type") ?: "").lowercase()
    return when {
        "senate" in chamber || chamber == "sen" -> "senate"
        "house" in chamber || chamber == "rep" -> "house"
        else -> "unknown"
    }
}

/**
 * Statutory full-term span in years by chamber: House seats are up every 2
 * years, Senate every 6. A current term whose span differs is anomalous —
 * a mid-term appointment, a special-election winner filling a partial term,
 * or bad upstream data — so we omit the next-election year rather than guess
 * (correctness over coverage, #32). Mirrors Python `_common._FULL_TERM_YEARS`.
 */
private val FULL_TERM_YEARS = mapOf("house" to 2, "senate" to 6)

/**
 * Derive the November general-election year this member next faces from the
 * current (most recent) term's expiry. Mirrors Python
 * `_common.next_election_year_from_terms`.
 *
 * Both chambers share the arithmetic: the general is the even year before
 * the January term expiry, i.e. `endYear - 1` (House 2025–2027 → 2026;
 * Senate 2025–2031 → 2030). Returns `null` (omit the field) when the data is
 * ambiguous: no terms, unknown chamber, missing start/end year, a term span
 * that isn't the chamber's statutory length, or a derived odd (non-general)
 * year.
 */
internal fun nextElectionYearFromTerms(terms: JsonElement?, chamber: String): Int? {
    val expectedSpan = FULL_TERM_YEARS[chamber] ?: return null
    val array = terms as? JsonArray ?: return null
    val current = array.lastOrNull() as? JsonObject ?: return null
    val start = current.intField("startYear") ?: return null
    val end = current.intField("endYear") ?: return null
    if (end - start != expectedSpan) return null
    val year = end - 1
    if (year % 2 != 0) return null
    return year
}

/**
 * Build a [Member] from a Congress.gov member detail payload (merged
 * with the list-endpoint summary, per the orchestrator's
 * `{**summary, **detail}` shape). Mirrors Python `_common.parse_member_summary`.
 *
 * Both `contact_form` and `website` default to `null`; the orchestrator
 * fills them in from the legislators-current.json side index after
 * this returns.
 */
fun parseMemberSummary(raw: JsonObject, warn: (String) -> Unit = {}): Member {
    val addr = raw.jsonObjectField("addressInformation") ?: JsonObject(emptyMap())
    val sponsored = raw.jsonObjectField("sponsoredLegislation") ?: JsonObject(emptyMap())
    val cosponsored = raw.jsonObjectField("cosponsoredLegislation") ?: JsonObject(emptyMap())
    val depiction = raw.jsonObjectField("depiction") ?: JsonObject(emptyMap())
    val name = raw.stringField("directOrderName") ?: raw.stringField("name") ?: "Unknown"
    val terms = raw["terms"]
    val chamber = chamberFromTerms(terms)
    // At-large House reps: Congress.gov inconsistently returns null or 0
    // for the lone district in at-large states (VT/AK/DE/ND/SD/WY) and
    // delegate jurisdictions (DC/AS/GU/MP/PR/VI). Normalize null → 0 for
    // House chamber so downstream matching is consistent. Senators keep
    // district = null.
    val rawDistrict = raw.intField("district")
    val district = if (chamber == "house" && rawDistrict == null) 0 else rawDistrict
    return Member(
        bioguideId = raw.stringField("bioguideId") ?: "",
        name = name,
        party = normalizePartyCode(raw.stringField("partyName") ?: raw.stringField("party")),
        state = stateCode(raw.stringField("state"), warn),
        district = district,
        chamber = chamber,
        photoUrl = depiction.stringField("imageUrl"),
        officialUrl = raw.stringField("officialUrl"),
        sponsoredCount = sponsored.intField("count") ?: 0,
        cosponsoredCount = cosponsored.intField("count") ?: 0,
        address = addr.stringField("officeAddress"),
        phone = addr.stringField("phoneNumber"),
        contactForm = null,
        website = null,
        nextElectionYear = nextElectionYearFromTerms(terms, chamber),
    )
}

/**
 * Build a [MemberLegislationItem] from one Congress.gov
 * sponsored-legislation / cosponsored-legislation list row. Mirrors
 * Python `_common.parse_member_legislation_item`. The published
 * `id` shape is `{type}{number}-{congress}`, matching the bills
 * manifest so the Android app does O(1) "is this in the cache?"
 * lookups.
 *
 * `policy_area` arrives as either an object `{name, ...}` or a bare
 * string; both are accepted.
 */
fun parseMemberLegislationItem(raw: JsonObject): MemberLegislationItem {
    val billType = (raw.stringField("type") ?: "").lowercase()
    val number = raw.stringField("number") ?: ""
    val congress = raw.intField("congress") ?: 0
    val latest = raw.jsonObjectField("latestAction") ?: JsonObject(emptyMap())
    val actionDate = (latest.stringField("actionDate") ?: latest.stringField("date") ?: "").take(10)
    val policyArea = policyAreaName(raw["policyArea"])
    return MemberLegislationItem(
        id = "$billType$number-$congress",
        type = billType,
        number = number,
        congress = congress,
        title = raw.stringField("latestTitle") ?: raw.stringField("title") ?: "",
        introducedDate = raw.stringField("introducedDate") ?: "",
        latestAction = Action(date = actionDate, text = latest.stringField("text") ?: ""),
        policyArea = policyArea,
    )
}

internal fun JsonObject.intField(key: String): Int? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    return primitive.intOrNull ?: primitive.contentOrNull?.toIntOrNull()
}
