package com.informedcitizen.pipeline.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Per-Congress bills manifest published at `congress<N>_bills.json`.
 *
 * [votesCoverage] is the rollout gate for the app's vote surfaces: with
 * lenient parsing an empty [Bill.votes] list is ambiguous ("no roll
 * call" vs "votes not published yet"), so the manifest says whether the
 * votes pipeline has published an index for this Congress. `false` (or
 * absent, on manifests predating the votes pipeline) means vote
 * surfaces must stay hidden rather than claim a bill had no roll call.
 */
@Serializable
data class BillsManifest(
    @SerialName("generated_at") val generatedAt: String,
    val congress: Int,
    @SerialName("votes_coverage") val votesCoverage: Boolean = false,
    val bills: List<Bill>,
)
