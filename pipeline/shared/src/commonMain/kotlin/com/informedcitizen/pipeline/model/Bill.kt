package com.informedcitizen.pipeline.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
