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
    val path: String,
)
