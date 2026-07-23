package com.informedcitizen.pipeline.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Per-Congress **sharded** bills index published at
 * `congress<N>_bills_index.json` (issue #40, epic #38). It lets the
 * published record grow from ~256 bills per Congress to the full
 * ~10,000+ (once #39's pre-floor bills are published) without the app
 * downloading and parsing a tens-of-megabytes single manifest on every
 * refresh.
 *
 * ## Discovery
 * The index is reached the same way the whole-manifest is today: a new
 * optional [CongressEntry.shardIndexPath] on `congresses.json` points at
 * this file, exactly as [CongressEntry.manifestPath] points at the
 * single [BillsManifest]. During the transition both are published
 * ([manifestPath] unchanged so current app versions keep working, the
 * shard set published alongside); the single manifest is retired only
 * after app-side paging (#41) ships and bakes.
 *
 * ## Shard content
 * Each [BillShard.path] file has the **same wire shape as
 * [BillsManifest]** (`generated_at` / `congress` / `votes_coverage` /
 * `bills`), so a shard parses with the existing model and cache path —
 * no new per-shard model is needed. A shard carries only its own page of
 * [Bill]s.
 *
 * ## Ordering & paging
 * [shards] is ordered **most-recent-first** (page 1 = the newest bills by
 * latest-action date), matching the app's recency-first bills list, so a
 * client can render the first page without fetching the rest. Each shard
 * holds up to [pageSize] bills; only the last (oldest) shard may hold
 * fewer. [BillShard.page] is the stable 1-based ordinal.
 *
 * [votesCoverage] mirrors [BillsManifest.votesCoverage] so the vote
 * surfaces' rollout gate can be read from the index without fetching a
 * shard; `false`/absent means vote surfaces stay hidden.
 *
 * [generatedAt] has a default so in-memory construction sites (tests,
 * fakes) keep compiling — wire reads always populate it.
 */
@Serializable
data class BillShardIndex(
    @SerialName("generated_at") val generatedAt: String? = null,
    val congress: Int,
    @SerialName("page_size") val pageSize: Int,
    @SerialName("total_bills") val totalBills: Int = 0,
    @SerialName("votes_coverage") val votesCoverage: Boolean = false,
    val shards: List<BillShard> = emptyList(),
)

/**
 * One entry in [BillShardIndex.shards]. Field declaration order mirrors
 * the Python builder's dict literal so byte-parity holds in the shadow
 * diff (`page`, `path`, `count`, `first_action_date`, `last_action_date`).
 *
 * [path] is the shard filename (e.g. `congress119_bills_p001.json`),
 * resolved relative to the same `data/` directory as [manifestPath].
 * [firstActionDate]/[lastActionDate] bound the shard's recency window so
 * a client can seek a page by date without opening it.
 */
@Serializable
data class BillShard(
    val page: Int,
    val path: String,
    val count: Int = 0,
    @SerialName("first_action_date") val firstActionDate: String? = null,
    @SerialName("last_action_date") val lastActionDate: String? = null,
)
