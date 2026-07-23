package com.informedcitizen.pipeline.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Top-level index published at `docs/data/congresses.json`. Produced
 * by Python `_common.rebuild_index` and (in the Kotlin port) by
 * `FileCongressesIndexStore.rebuild`. Field declaration order matches
 * the Python output exactly so byte-parity holds during the
 * parallel-run period before `update-bills.yml`'s canonical ownership
 * flips from Python to Kotlin.
 *
 * `generatedAt` has a default so existing in-memory construction
 * sites (Android tests, fakes) keep compiling — wire reads from the
 * published index always populate it.
 */
@Serializable
data class CongressesIndex(
    @SerialName("generated_at") val generatedAt: String? = null,
    @SerialName("current_congress") val currentCongress: Int,
    val congresses: List<CongressEntry>,
)

/**
 * One row in the [CongressesIndex.congresses] array. Field order
 * mirrors Python `_common.rebuild_index`'s dict literal:
 * `congress`, `bill_count`, `first_action_date`, `last_action_date`,
 * `manifest_path`, `is_current`, `backfill_complete`. All new fields
 * default so existing Android test fakes that only pass
 * `congress` + `manifestPath` + `isCurrent` keep compiling.
 *
 * [shardIndexPath] is the #40 dual-publish discovery hook: when a
 * Congress has been sharded, it points at the [BillShardIndex] file
 * (`congress<N>_bills_index.json`) published *alongside* the unchanged
 * whole [manifestPath]. It is optional and defaulted null so the field
 * is additive — current app versions ignore an unknown key, and a
 * Congress with no shard set omits it entirely.
 */
@Serializable
data class CongressEntry(
    val congress: Int,
    @SerialName("bill_count") val billCount: Int = 0,
    @SerialName("first_action_date") val firstActionDate: String? = null,
    @SerialName("last_action_date") val lastActionDate: String? = null,
    @SerialName("manifest_path") val manifestPath: String,
    @SerialName("is_current") val isCurrent: Boolean = false,
    @SerialName("backfill_complete") val backfillComplete: Boolean = false,
    @SerialName("shard_index_path") val shardIndexPath: String? = null,
)
