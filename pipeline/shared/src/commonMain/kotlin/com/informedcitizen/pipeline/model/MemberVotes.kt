package com.informedcitizen.pipeline.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Per-member roll-call positions shard published at
 * `docs/data/votes/members/<bioguideId>.json` (mirroring the per-member
 * `<bioguideId>_sponsored.json` layout under `docs/data/members`).
 *
 * The full [RollCallVote] artifacts are the wrong shape for the app's
 * "your reps" surfaces: the app only ever needs positions for a
 * handful of saved members across many bills, so it fetches one shard
 * per saved rep (a few tens of KB each) instead of one positions file
 * per bill on screen. The same shard backs the member-detail "Recent
 * votes" history. Rows span every Congress with published votes and
 * are newest first, with the same tiebreak as [VotesIndex].
 */
@Serializable
data class MemberVotes(
    @SerialName("generated_at") val generatedAt: String,
    @SerialName("bioguide_id") val bioguideId: String,
    @SerialName("vote_count") val voteCount: Int,
    val votes: List<MemberVoteRow>,
)

/**
 * One row of [MemberVotes.votes]: this member's position on one roll
 * call. [voteId] matches [VoteRef.id] / [RollCallVote.id], so a row
 * joins to a bill's attached [VoteRef]s without extra fetches.
 *
 * [billId] matches [Bill.id] exactly (the string the app keys caches
 * and navigation on); [type], [number], and [shortTitle] are display
 * conveniences so vote history renders without loading any manifest.
 * All four are null for roll calls not tied to a bill (nominations,
 * quorum calls); [shortTitle] is also null when the bill has none or
 * left the published manifests.
 */
@Serializable
data class MemberVoteRow(
    @SerialName("vote_id") val voteId: String,
    val congress: Int,
    val date: String,
    val question: String,
    val result: String,
    val position: VotePosition,
    @SerialName("bill_id") val billId: String? = null,
    val type: String? = null,
    val number: String? = null,
    @SerialName("short_title") val shortTitle: String? = null,
)
