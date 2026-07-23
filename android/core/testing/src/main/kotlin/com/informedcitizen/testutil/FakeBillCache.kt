package com.informedcitizen.testutil

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.informedcitizen.data.cache.BillCache
import com.informedcitizen.data.cache.BillSource
import com.informedcitizen.data.cache.CachedManifestMeta
import com.informedcitizen.data.cache.FreshestBills
import com.informedcitizen.data.cache.ShardCursor
import com.informedcitizen.pipeline.lifecycleStatusToWireString
import com.informedcitizen.pipeline.model.Bill

/**
 * In-memory fake of [BillCache] for repository / view-model tests.
 * Records every write so tests can assert what was persisted under
 * which [BillSource] without spinning up SQLDelight.
 *
 * Rows are stored shard-tagged so the whole-manifest path
 * ([replaceForSource], shard 0) and the sharded paging path
 * ([appendShard]) share one store; the paged reads mirror the SQL
 * `ORDER BY latest_action_date DESC, bill_id` + optional
 * `status`/`policy_area` filters.
 */
class FakeBillCache : BillCache {
    data class WriteCall(
        val congress: Int,
        val source: BillSource,
        val bills: List<Bill>,
        val generatedAt: String,
        val fetchedAtMillis: Long,
    )

    data class AppendCall(
        val congress: Int,
        val source: BillSource,
        val shardIndex: Int,
        val bills: List<Bill>,
    )

    private data class Row(val shardIndex: Int, val bill: Bill)

    val writes: MutableList<WriteCall> = mutableListOf()
    val appends: MutableList<AppendCall> = mutableListOf()
    private val rows: MutableMap<Pair<Int, BillSource>, MutableList<Row>> = mutableMapOf()
    private val manifestByKey: MutableMap<Pair<Int, BillSource>, CachedManifestMeta> = mutableMapOf()
    private val cursors: MutableMap<Pair<Int, BillSource>, ShardCursor> = mutableMapOf()

    override suspend fun replaceForSource(
        congress: Int,
        source: BillSource,
        bills: List<Bill>,
        generatedAt: String,
        fetchedAtMillis: Long,
    ) {
        writes += WriteCall(congress, source, bills, generatedAt, fetchedAtMillis)
        rows[congress to source] = bills.map { Row(shardIndex = 0, bill = it) }.toMutableList()
        manifestByKey[congress to source] = CachedManifestMeta(generatedAt, fetchedAtMillis)
    }

    override suspend fun loadBills(congress: Int, source: BillSource): List<Bill> =
        rows[congress to source].orEmpty().map { it.bill }

    override suspend fun loadAllForCongress(congress: Int): List<Bill> =
        rows.entries.filter { it.key.first == congress }.flatMap { it.value }.map { it.bill }

    override suspend fun loadManifest(congress: Int, source: BillSource): CachedManifestMeta? =
        manifestByKey[congress to source]

    override suspend fun loadFreshest(): FreshestBills? =
        manifestByKey.entries.maxByOrNull { it.value.fetchedAtMillis }?.let { (key, meta) ->
            FreshestBills(
                congress = key.first,
                source = key.second,
                bills = rows[key].orEmpty().map { it.bill },
                generatedAt = meta.generatedAt,
                fetchedAtMillis = meta.fetchedAtMillis,
            )
        }

    override suspend fun clearSource(congress: Int, source: BillSource) {
        rows.remove(congress to source)
        manifestByKey.remove(congress to source)
        cursors.remove(congress to source)
    }

    override suspend fun clearAll() {
        rows.clear()
        manifestByKey.clear()
        cursors.clear()
        writes.clear()
        appends.clear()
    }

    override suspend fun appendShard(
        congress: Int,
        source: BillSource,
        shardIndex: Int,
        bills: List<Bill>,
        generatedAt: String,
        fetchedAtMillis: Long,
        totalShards: Int,
        pageSize: Int,
    ) {
        appends += AppendCall(congress, source, shardIndex, bills)
        val list = rows.getOrPut(congress to source) { mutableListOf() }
        list.removeAll { it.shardIndex == shardIndex }
        list += bills.map { Row(shardIndex = shardIndex, bill = it) }
        cursors[congress to source] = ShardCursor(
            nextShardIndex = shardIndex + 1,
            totalShards = totalShards,
            pageSize = pageSize,
        )
        manifestByKey[congress to source] = CachedManifestMeta(generatedAt, fetchedAtMillis)
    }

    override suspend fun loadShardCursor(congress: Int, source: BillSource): ShardCursor? =
        cursors[congress to source]

    override suspend fun loadBillsPaged(
        congress: Int,
        source: BillSource,
        status: String?,
        policyArea: String?,
        limit: Int,
        offset: Int,
    ): List<Bill> =
        matching(congress, source, status, policyArea)
            .drop(offset)
            .take(limit)

    override suspend fun countBills(
        congress: Int,
        source: BillSource,
        status: String?,
        policyArea: String?,
    ): Int = matching(congress, source, status, policyArea).size

    override fun billsPagingSource(
        congress: Int,
        source: BillSource,
        status: String?,
        policyArea: String?,
    ): PagingSource<Int, Bill> =
        OffsetSnapshotPagingSource { matching(congress, source, status, policyArea) }

    override suspend fun distinctPolicyAreas(congress: Int, source: BillSource): List<String> =
        rows[congress to source].orEmpty()
            .mapNotNull { it.bill.policyArea }
            .distinct()
            .sorted()

    private fun matching(
        congress: Int,
        source: BillSource,
        status: String?,
        policyArea: String?,
    ): List<Bill> =
        rows[congress to source].orEmpty()
            .map { it.bill }
            .filter { bill ->
                (status == null || bill.lifecycleStatus?.let(::lifecycleStatusToWireString) == status) &&
                    (policyArea == null || bill.policyArea == policyArea)
            }
            .sortedWith(compareByDescending<Bill> { it.latestAction.date }.thenBy { it.id })
}

/**
 * An offset-keyed [PagingSource] over an in-memory snapshot, mirroring the
 * page semantics of SQLDelight's `OffsetQueryPagingSource` (the key is the
 * absolute row offset) so repository / view-model tests exercise the real
 * Paging flow without a driver. The snapshot is re-read on each [load] so a
 * write between loads is reflected, matching the DB source's behaviour.
 */
private class OffsetSnapshotPagingSource(
    private val snapshot: () -> List<Bill>,
) : PagingSource<Int, Bill>() {

    override fun getRefreshKey(state: PagingState<Int, Bill>): Int? =
        state.anchorPosition?.let { anchor ->
            val page = state.closestPageToPosition(anchor)
            page?.prevKey?.plus(page.data.size) ?: page?.nextKey?.minus(page.data.size)
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Bill> {
        val all = snapshot()
        val offset = params.key ?: 0
        val page = all.drop(offset).take(params.loadSize)
        val nextOffset = offset + page.size
        return LoadResult.Page(
            data = page,
            prevKey = if (offset == 0) null else (offset - params.loadSize).coerceAtLeast(0),
            nextKey = if (nextOffset >= all.size) null else nextOffset,
        )
    }
}
