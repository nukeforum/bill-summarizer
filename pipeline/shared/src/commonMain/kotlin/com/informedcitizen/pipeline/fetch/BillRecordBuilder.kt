package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.classifyTextFormatUrl
import com.informedcitizen.pipeline.cleanSponsorName
import com.informedcitizen.pipeline.http.CongressClient
import com.informedcitizen.pipeline.model.Action
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.Sponsor
import com.informedcitizen.pipeline.outcomeFromWireString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Build a fully-enriched [Bill] from a list-endpoint summary. Issues
 * four sequential Congress.gov GETs per bill (detail / summaries /
 * text / subjects) — same shape as Python `_common.build_bill_record`.
 * Suspending so the orchestrator can fan it out across coroutines.
 */
suspend fun buildBillRecord(
    client: CongressClient,
    congress: Int,
    billSummary: JsonObject,
    outcome: String,
): Bill {
    val billType = (billSummary.stringField("type") ?: "").lowercase()
    val billNumber = billSummary.stringField("number") ?: ""
    val latestAction = billSummary.jsonObjectField("latestAction") ?: JsonObject(emptyMap())

    val detail = client.get("/bill/$congress/$billType/$billNumber")
        .jsonObjectField("bill") ?: JsonObject(emptyMap())

    val sponsors = (detail["sponsors"] as? JsonArray) ?: JsonArray(emptyList())
    val sponsorJson = sponsors.firstOrNull() as? JsonObject ?: JsonObject(emptyMap())
    val sponsorName = cleanSponsorName(
        sponsorJson.stringField("fullName")
            ?: sponsorJson.stringField("lastName")
            ?: "Unknown"
    )

    val summaryText = fetchLatestCrsSummary(client, congress, billType, billNumber)
    val textUrls = fetchTextUrls(client, congress, billType, billNumber)
    val subjects = fetchSubjects(client, congress, billType, billNumber)

    val actionDate = (latestAction.stringField("actionDate") ?: latestAction.stringField("date") ?: "")
        .take(10)

    return Bill(
        id = "$billType$billNumber-$congress",
        congress = congress,
        type = billType,
        number = billNumber,
        title = detail.stringField("title") ?: billSummary.stringField("title") ?: "",
        shortTitle = extractShortTitle(detail),
        sponsor = Sponsor(
            name = sponsorName,
            party = normalizePartyCode(sponsorJson.stringField("party")),
            state = (sponsorJson.stringField("state") ?: "").uppercase(),
        ),
        introducedDate = detail.stringField("introducedDate") ?: "",
        latestAction = Action(
            date = actionDate,
            text = latestAction.stringField("text") ?: "",
        ),
        outcome = outcomeFromWireString(outcome)
            ?: error("buildBillRecord called with unknown outcome wire string: '$outcome'"),
        policyArea = policyAreaName(detail["policyArea"]),
        summaryCrs = summaryText,
        textUrlHtml = textUrls["html"],
        textUrlXml = textUrls["xml"],
        textUrlPdf = textUrls["pdf"],
        congressGovUrl = congressGovUrl(congress, billType, billNumber),
        subjects = subjects,
    )
}

/**
 * Find the entry in `detail.titles` whose `titleType` contains "short
 * title" (case-insensitive). Mirrors Python `_extract_short_title`'s
 * permissive scan — `titles` is sometimes a list of `{title,
 * titleType}` dicts, sometimes a `{url}` pointer, sometimes a list of
 * plain strings. Short title is a nice-to-have, not load-bearing.
 */
internal fun extractShortTitle(detail: JsonObject): String? {
    val titles = detail["titles"] as? JsonArray ?: return null
    for (entry in titles) {
        val obj = entry as? JsonObject ?: continue
        val titleType = obj.stringField("titleType")?.lowercase() ?: continue
        if ("short title" in titleType) {
            return obj.stringField("title")
        }
    }
    return null
}

/**
 * Fetch a bill's Congress.gov legislative-subject terms (issue #28).
 *
 * The `/subjects` endpoint returns `{"subjects": {"legislativeSubjects":
 * [{"name": ...}], "policyArea": {...}}}`. We keep only the finer-grained
 * `legislativeSubjects` names (`policyArea` is already carried by the record's
 * [Bill.policyArea] field). Names are de-duplicated and sorted so the published
 * order is deterministic — the API's ordering is not guaranteed stable, and a
 * stable order is what keeps this KMP shadow byte-identical to Python
 * `_common._fetch_subjects`. Returns `[]` for a bill with no subjects (a
 * bare-introduced bill often has none), matching the wire model's empty default.
 */
internal suspend fun fetchSubjects(
    client: CongressClient,
    congress: Int,
    billType: String,
    billNumber: String,
): List<String> {
    val body = client.get("/bill/$congress/$billType/$billNumber/subjects")
    val subjects = body.jsonObjectField("subjects") ?: return emptyList()
    val legislative = subjects["legislativeSubjects"] as? JsonArray ?: return emptyList()
    val names = mutableSetOf<String>()
    for (entry in legislative) {
        val obj = entry as? JsonObject ?: continue
        val name = obj.stringField("name")
        if (name != null && name.isNotEmpty()) names.add(name)
    }
    return names.sorted()
}

internal suspend fun fetchLatestCrsSummary(
    client: CongressClient,
    congress: Int,
    billType: String,
    billNumber: String,
): String? {
    val body = client.get("/bill/$congress/$billType/$billNumber/summaries")
    val summaries = body["summaries"] as? JsonArray ?: return null
    val dictSummaries = summaries.mapNotNull { it as? JsonObject }
    if (dictSummaries.isEmpty()) return null
    val latest = dictSummaries.maxBy { it.stringField("updateDate") ?: "" }
    val text = latest.stringField("text")
    return text?.takeIf { it.isNotEmpty() }
}

internal suspend fun fetchTextUrls(
    client: CongressClient,
    congress: Int,
    billType: String,
    billNumber: String,
): Map<String, String> {
    val body = client.get("/bill/$congress/$billType/$billNumber/text")
    val versions = body["textVersions"] as? JsonArray ?: return emptyMap()
    val dictVersions = versions.mapNotNull { it as? JsonObject }
    if (dictVersions.isEmpty()) return emptyMap()
    // Latest first, mirroring Python's sort-by-date-desc.
    val sortedDesc = dictVersions.sortedByDescending { it.stringField("date") ?: "" }
    // Walk in order; first version with non-empty formats wins.
    val formats = sortedDesc.firstNotNullOfOrNull { v ->
        (v["formats"] as? JsonArray)?.takeIf { it.isNotEmpty() }
    } ?: return emptyMap()
    val out = mutableMapOf<String, String>()
    for (fmt in formats) {
        val obj = fmt as? JsonObject ?: continue
        val url = obj.stringField("url") ?: continue
        val kind = classifyTextFormatUrl(url) ?: continue
        if (kind !in out) out[kind] = url
    }
    return out
}
