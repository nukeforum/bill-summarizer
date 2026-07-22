package com.informedcitizen.pipeline.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Chamber a roll-call vote was taken in. */
@Serializable
enum class Chamber {
    @SerialName("house") HOUSE,
    @SerialName("senate") SENATE,
}

/**
 * A member's recorded position on a roll call. Upstream sources use
 * varying labels (House "Aye"/"No" on motions, "Yea"/"Nay" on
 * passage; Senate "Guilty"/"Not Guilty" on impeachment); the pipeline
 * normalizes all of them to these four wire values.
 */
@Serializable
enum class VotePosition {
    @SerialName("yea") YEA,
    @SerialName("nay") NAY,
    @SerialName("present") PRESENT,
    @SerialName("not_voting") NOT_VOTING,
}

/** Chamber-wide tallies for one roll call. */
@Serializable
data class VoteTotals(
    val yea: Int,
    val nay: Int,
    val present: Int,
    @SerialName("not_voting") val notVoting: Int,
)

/** One member's position on one roll call, keyed by bioguide id. */
@Serializable
data class MemberVote(
    @SerialName("bioguide_id") val bioguideId: String,
    val position: VotePosition,
    val party: String? = null,
    val state: String? = null,
)

/**
 * One roll-call vote with per-member positions, published at
 * `docs/data/votes/congress<N>/<chamber>-<session>-<rollNumber>.json`
 * (e.g. `votes/congress119/house-1-17.json`).
 *
 * Votes are sharded one-file-per-vote rather than bundled per
 * Congress: positions run ~435 rows for a House vote and a Congress
 * holds 1,000+ roll calls, so a bundled file would be tens of MB
 * while the app only ever needs the handful of votes attached to the
 * bill on screen. The per-Congress [VotesIndex] carries everything
 * except positions for list rendering and bill lookup.
 *
 * [id] is `<chamber>-<congress>-<session>-<rollNumber>`
 * (e.g. `house-119-1-17`). [billId] matches [Bill.id]
 * (`<type><number>-<congress>`, e.g. `hr1234-119`) and is null for
 * roll calls not tied to a bill (quorum calls, journal votes).
 */
@Serializable
data class RollCallVote(
    val id: String,
    val congress: Int,
    val chamber: Chamber,
    val session: Int,
    @SerialName("roll_number") val rollNumber: Int,
    val date: String,
    val question: String,
    val result: String,
    @SerialName("bill_id") val billId: String? = null,
    val totals: VoteTotals,
    val positions: List<MemberVote>,
    @SerialName("source_url") val sourceUrl: String? = null,
)
