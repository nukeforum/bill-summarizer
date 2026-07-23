package com.informedcitizen.data.repository

import com.informedcitizen.data.cache.ShardCursor
import com.informedcitizen.pipeline.model.BillShard
import com.informedcitizen.pipeline.model.BillShardIndex

/**
 * The next shard-fetch action the issue #41 paging mediator should take,
 * derived purely from the discovered [BillShardIndex] (or its absence)
 * and the persisted [ShardCursor]. Keeping this decision out of the
 * (Paging-3-bound) `RemoteMediator` makes the "which page next" logic
 * unit-testable in isolation — the mediator becomes a thin adapter that
 * fetches whatever [nextShardStep] names and persists it.
 */
sealed interface ShardStep {
    /**
     * The Congress is unsharded — no `shard_index_path` published yet, the
     * dual-publish transition case (issue #40). The whole
     * `congress<N>_bills.json` manifest is the single page: fetch it once
     * and report end-of-pagination. This is where a small Congress (today's
     * 256-bill manifest) lives until #39's pre-floor bills grow it past a
     * page.
     */
    data object WholeManifest : ShardStep

    /**
     * Fetch [shard] (position [shardIndex] of [totalShards], each page
     * holding up to [pageSize] bills) and append it via
     * [com.informedcitizen.data.cache.BillCache.appendShard]. [totalShards]
     * and [pageSize] flow straight into the cursor the mediator advances,
     * and drive the honest "N of ~M" list footer.
     */
    data class Fetch(
        val shard: BillShard,
        val shardIndex: Int,
        val totalShards: Int,
        val pageSize: Int,
    ) : ShardStep

    /** Every shard for this Congress is already cached — end of pagination. */
    data object Complete : ShardStep
}

/**
 * Decide the next [ShardStep] for the current load.
 *
 * @param index the shard index discovered via
 *   [BillRepository.fetchShardIndex], or `null` when the Congress is
 *   unsharded (page the whole manifest instead).
 * @param cursor the persisted append cursor, or `null` before the first
 *   append (an append with no cursor behaves like the first page).
 * @param refresh `true` for a Paging `REFRESH` load (start over from the
 *   newest shard, ignoring the cursor); `false` for an `APPEND` load
 *   (continue from [ShardCursor.nextShardIndex]).
 *
 * [totalShards] is re-derived from the freshly-discovered `index.shards`
 * on every call, so a shard index that grew (or shrank) between loads is
 * honoured rather than trusting a stale cursor total: an append whose
 * cursor points past the current end simply returns [ShardStep.Complete].
 */
fun nextShardStep(
    index: BillShardIndex?,
    cursor: ShardCursor?,
    refresh: Boolean,
): ShardStep {
    if (index == null || index.shards.isEmpty()) {
        // Unsharded (or an empty index): the whole manifest is the one page.
        // Load it on refresh; an append has nothing further to fetch.
        return if (refresh) ShardStep.WholeManifest else ShardStep.Complete
    }
    val totalShards = index.shards.size
    val nextIndex = if (refresh) 0 else (cursor?.nextShardIndex ?: 0)
    if (nextIndex >= totalShards) return ShardStep.Complete
    return ShardStep.Fetch(
        shard = index.shards[nextIndex],
        shardIndex = nextIndex,
        totalShards = totalShards,
        pageSize = index.pageSize,
    )
}
