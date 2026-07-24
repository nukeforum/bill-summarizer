package com.informedcitizen.pipeline.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonObject

@Serializable
data class Bill(
    val id: String,
    val congress: Int,
    val type: String,
    val number: String,
    val title: String,
    @SerialName("short_title") val shortTitle: String? = null,
    val sponsor: Sponsor,
    @SerialName("introduced_date") val introducedDate: String,
    @SerialName("latest_action") val latestAction: Action,
    // Defaulted so the lenient wire config's coerceInputValues can map an
    // unrecognised outcome string to Outcome.UNKNOWN instead of crashing.
    val outcome: Outcome = Outcome.UNKNOWN,
    /**
     * Pre-floor lifecycle status (issue #39) for a bill that has not reached a
     * terminal floor [outcome]. Additive, defaulted null: manifests published
     * before the field existed decode unchanged, and — crucially — a bill that
     * *does* carry it is safely ignored by any app build released before this
     * field existed (`ignoreUnknownKeys`). A real floor [outcome] always wins
     * over a lifecycle status (`classifyBillStatus` precedence), so a bill with
     * a decisive outcome leaves this null. See [LifecycleStatus].
     */
    @SerialName("status") val lifecycleStatus: LifecycleStatus? = null,
    @SerialName("policy_area") val policyArea: String? = null,
    /**
     * Congress.gov finer-grained legislative subjects (issue #28): multi-value
     * terms like "Firearms and explosives" or "Intelligence activities,
     * surveillance, classified information", distinct from the single-value
     * [policyArea]. Fetched per bill from the `/subjects` endpoint during
     * record building and used by #10's topical filtering. Additive and
     * defaulted empty: manifests published before the field existed — and apps
     * released before it — keep decoding unchanged (`ignoreUnknownKeys`).
     */
    val subjects: List<String> = emptyList(),
    @SerialName("summary_crs") val summaryCrs: String? = null,
    @SerialName("text_url_html") val textUrlHtml: String? = null,
    @SerialName("text_url_xml") val textUrlXml: String? = null,
    @SerialName("text_url_pdf") val textUrlPdf: String? = null,
    @SerialName("congress_gov_url") val congressGovUrl: String,
    /**
     * Roll-call votes recorded on this bill: the same [VoteRef] rows as the
     * per-Congress [VotesIndex], newest first. Aggregate totals ride along on
     * each ref so list/detail screens can show a breakdown without fetching
     * per-member positions (those stay in the per-vote file at [VoteRef.path]).
     * Defaults to empty so manifests published before the field existed — and
     * apps released before it — keep decoding unchanged.
     */
    val votes: List<VoteRef> = emptyList(),
)

/**
 * Manifest write serializer that omits the `policy_area` key when its value is
 * null, so the published bills manifest byte-matches the Python pipeline's
 * canonical output.
 *
 * The write [ManifestJson][com.informedcitizen.pipeline.fetch.ManifestJson]
 * keeps null fields explicit (`explicitNulls = true`) because Python emits
 * `"short_title": null`, `"summary_crs": null`, etc. `policy_area` is the one
 * exception: Python omits the key entirely when the value is absent (its record
 * builder drops it, and carried-forward bills round-trip the raw dict, which
 * never carried the key). Without this transform, Kotlin decodes such a
 * carried-forward bill into [Bill.policyArea] = null and re-serializes it as
 * `"policy_area": null`, the sole reproducible divergence in the bills/backfill
 * parity check (issue #74).
 *
 * kotlinx-serialization has no per-property "omit when null" knob that leaves
 * other nulls explicit, so this narrowly drops just that one key on write and
 * leaves every other field — including all other explicit nulls — untouched.
 * Deserialization is unchanged (identity transform): a manifest with or without
 * the key decodes to the same [Bill], so round-trips are preserved.
 */
internal object BillManifestWriteSerializer :
    JsonTransformingSerializer<Bill>(Bill.serializer()) {
    override fun transformSerialize(element: JsonElement): JsonElement {
        val obj = element.jsonObject
        return if (obj["policy_area"] is JsonNull) {
            JsonObject(obj - "policy_area")
        } else {
            element
        }
    }
}
