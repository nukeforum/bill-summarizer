package com.informedcitizen.pipeline.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Per-Congress roll-call index published at
 * `docs/data/congress<N>_votes.json` (mirroring the
 * `congress<N>_bills.json` naming). Lists every vote minus the
 * per-member positions, so the app can render vote lists and resolve
 * `bill_id -> vote files` without downloading position data; the full
 * [RollCallVote] for a given entry lives at [VoteRef.path].
 */
@Serializable
data class VotesIndex(
    @SerialName("generated_at") val generatedAt: String,
    val congress: Int,
    @SerialName("vote_count") val voteCount: Int,
    val votes: List<VoteRef>,
)

/**
 * One row of [VotesIndex.votes]: a [RollCallVote] minus positions,
 * plus [path] — the location of the full vote file relative to
 * `docs/data/` (e.g. `votes/congress119/house-1-17.json`), following
 * the `manifest_path` convention in [CongressEntry].
 *
 * [partySplit] maps each position wire value (`yea`, `nay`, `present`,
 * `not_voting`) to per-party counts, e.g.
 * `{"yea": {"D": 210, "R": 6, "I": 2}}` — position keys appear in wire
 * order and only when at least one member with a known party holds that
 * position; party keys are sorted. Members with no recorded party are
 * left out, so party counts may sum below the position total; consumers
 * render the numbers as published rather than reconciling them.
 */
@Serializable
data class VoteRef(
    val id: String,
    val chamber: Chamber,
    val session: Int,
    @SerialName("roll_number") val rollNumber: Int,
    val date: String,
    val question: String,
    val result: String,
    @SerialName("bill_id") val billId: String? = null,
    val totals: VoteTotals,
    @SerialName("party_split") val partySplit: Map<String, Map<String, Int>> = emptyMap(),
    val path: String,
)
