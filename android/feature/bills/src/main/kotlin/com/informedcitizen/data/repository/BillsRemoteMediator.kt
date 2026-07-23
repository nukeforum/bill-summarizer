package com.informedcitizen.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.informedcitizen.data.cache.BillCache
import com.informedcitizen.data.cache.BillSource
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.BillShard
import com.informedcitizen.pipeline.model.BillShardIndex
import com.informedcitizen.pipeline.model.BillsManifest

/**
 * Paging 3 mediator for the sharded bills list (backlog #41, epic #38).
 *
 * On each load it discovers the #40 shard index, reads the persisted
 * [com.informedcitizen.data.cache.ShardCursor], asks the pure
 * [nextShardStep] decision core which page to fetch next, then fetches and
 * appends exactly that page into [BillCache] (which advances the cursor).
 * Paging serves rows out of the DB via a `PagingSource` (wired in the
 * `pagedBills` slice), so the ~10k-bill broadened manifest never has to be
 * held in memory as one list.
 *
 * The mediator is a **thin adapter** over already-tested pieces — the
 * page-selection logic lives in [nextShardStep], the network reads in
 * [BillRepository.fetchShardIndex]/[BillRepository.fetchShard]/
 * [BillRepository.fetchCurrentManifest], and the transactional persistence
 * in [BillCache.appendShard]. Collaborators are passed as suspend function
 * references so the mediator is unit-testable with a fake cache and stub
 * fetchers, without constructing the whole repository.
 *
 * ## Load types
 * - `REFRESH` starts over from the newest shard (shard 0), ignoring the
 *   cursor — [nextShardStep] with `refresh = true`.
 * - `APPEND` continues from `cursor.nextShardIndex`.
 * - `PREPEND` is a no-op: the list only grows toward older bills, so there
 *   is never a page before the newest shard.
 *
 * ## Dual-publish / no-regression
 * A Congress with no published `shard_index_path` ([fetchShardIndex]
 * returns null) is paged as exactly one shard: `REFRESH` fetches the whole
 * manifest and appends it as shard 0 with `totalShards = 1`, reporting
 * end-of-pagination immediately. This is the identical-to-today path for
 * the 256-bill Congress.
 */
@OptIn(ExperimentalPagingApi::class)
internal class BillsRemoteMediator(
    private val source: BillSource,
    private val billCache: BillCache,
    private val fetchShardIndex: suspend () -> BillShardIndex?,
    private val fetchShard: suspend (BillShard) -> BillsManifest,
    private val fetchWholeManifest: suspend () -> BillsManifest,
    private val now: () -> Long = { System.currentTimeMillis() },
) : RemoteMediator<Int, Bill>() {

    override suspend fun load(loadType: LoadType, state: PagingState<Int, Bill>): MediatorResult {
        if (loadType == LoadType.PREPEND) {
            // Newest bills are page 1; there is nothing before them.
            return MediatorResult.Success(endOfPaginationReached = true)
        }
        return try {
            val refresh = loadType == LoadType.REFRESH
            val index = fetchShardIndex()
            val cursor = index?.let { billCache.loadShardCursor(it.congress, source) }
            when (val step = nextShardStep(index, cursor, refresh)) {
                ShardStep.WholeManifest -> {
                    val manifest = fetchWholeManifest()
                    appendPage(manifest, shardIndex = 0, totalShards = 1, pageSize = wholeManifestPageSize(manifest))
                    MediatorResult.Success(endOfPaginationReached = true)
                }

                is ShardStep.Fetch -> {
                    val page = fetchShard(step.shard)
                    appendPage(page, step.shardIndex, step.totalShards, step.pageSize)
                    MediatorResult.Success(
                        endOfPaginationReached = step.shardIndex + 1 >= step.totalShards,
                    )
                }

                ShardStep.Complete -> MediatorResult.Success(endOfPaginationReached = true)
            }
        } catch (error: Exception) {
            // Surfaced to the UI as an append/refresh LoadState.Error — the
            // "more pages exist but failed to load" footer, never a silent
            // partial-list-as-complete.
            MediatorResult.Error(error)
        }
    }

    private suspend fun appendPage(page: BillsManifest, shardIndex: Int, totalShards: Int, pageSize: Int) {
        billCache.appendShard(
            congress = page.congress,
            source = source,
            shardIndex = shardIndex,
            bills = page.bills,
            generatedAt = page.generatedAt,
            fetchedAtMillis = now(),
            totalShards = totalShards,
            pageSize = pageSize,
        )
    }

    /**
     * Page size recorded for the whole-manifest case. There is no shard
     * index to read it from, so the manifest's own bill count is the page
     * size (a single full page); coerced to at least 1 so an empty
     * manifest still writes a sane cursor.
     */
    private fun wholeManifestPageSize(manifest: BillsManifest): Int =
        manifest.bills.size.coerceAtLeast(1)
}
