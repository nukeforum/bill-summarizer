package com.informedcitizen.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.informedcitizen.data.cache.BillSource
import com.informedcitizen.pipeline.model.Action
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.BillShard
import com.informedcitizen.pipeline.model.BillShardIndex
import com.informedcitizen.pipeline.model.BillsManifest
import com.informedcitizen.pipeline.model.Outcome
import com.informedcitizen.pipeline.model.Sponsor
import com.informedcitizen.testutil.FakeBillCache
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalPagingApi::class)
class BillsRemoteMediatorTest {

    @Test
    fun `unsharded refresh fetches the whole manifest as a single shard and ends pagination`() = runTest {
        val cache = FakeBillCache()
        val manifest = BillsManifest(generatedAt = "2026-05-01", congress = 119, bills = listOf(bill("hr1-119")))
        var wholeCalls = 0
        val mediator = mediator(
            cache = cache,
            shardIndex = null,
            wholeManifest = { wholeCalls++; manifest },
        )

        val result = mediator.load(LoadType.REFRESH, emptyState())

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(1, wholeCalls)
        assertEquals(1, cache.appends.size)
        val append = cache.appends.single()
        assertEquals(0, append.shardIndex)
        assertEquals(BillSource.PUBLISHED, append.source)
        // Whole manifest is one full page → totalShards 1, cursor complete.
        val cursor = cache.loadShardCursor(119, BillSource.PUBLISHED)
        assertEquals(1, cursor?.totalShards)
        assertTrue(cursor?.isComplete == true)
    }

    @Test
    fun `unsharded append has nothing to fetch and ends pagination`() = runTest {
        val cache = FakeBillCache()
        var wholeCalls = 0
        val mediator = mediator(
            cache = cache,
            shardIndex = null,
            wholeManifest = { wholeCalls++; BillsManifest(generatedAt = "x", congress = 119, bills = emptyList()) },
        )

        val result = mediator.load(LoadType.APPEND, emptyState())

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals("append never fetches the whole manifest", 0, wholeCalls)
        assertTrue(cache.appends.isEmpty())
    }

    @Test
    fun `prepend is a no-op that ends pagination without any fetch`() = runTest {
        val cache = FakeBillCache()
        var fetches = 0
        val mediator = mediator(
            cache = cache,
            shardIndex = { fetches++; null },
        )

        val result = mediator.load(LoadType.PREPEND, emptyState())

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals("prepend never even discovers the index", 0, fetches)
        assertTrue(cache.appends.isEmpty())
    }

    @Test
    fun `sharded refresh fetches shard zero and reports more pages remain`() = runTest {
        val cache = FakeBillCache()
        val index = shardIndex(pages = 2)
        val fetched = mutableListOf<String>()
        val mediator = mediator(
            cache = cache,
            shardIndex = index,
            shard = { shard ->
                fetched += shard.path
                BillsManifest(generatedAt = "x", congress = 119, bills = listOf(bill("hr-${shard.page}")))
            },
        )

        val result = mediator.load(LoadType.REFRESH, emptyState())

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertFalse(
            "two shards → shard 0 is not the end",
            (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached,
        )
        assertEquals(listOf("congress119_bills_p001.json"), fetched)
        val append = cache.appends.single()
        assertEquals(0, append.shardIndex)
        val cursor = cache.loadShardCursor(119, BillSource.PUBLISHED)
        assertEquals(1, cursor?.nextShardIndex)
        assertEquals(2, cursor?.totalShards)
        assertFalse(cursor?.isComplete == true)
    }

    @Test
    fun `sharded append follows the cursor and ends pagination on the last shard`() = runTest {
        val cache = FakeBillCache()
        // Seed the cursor as if shard 0 was already appended.
        cache.appendShard(
            congress = 119,
            source = BillSource.PUBLISHED,
            shardIndex = 0,
            bills = listOf(bill("hr-0")),
            generatedAt = "x",
            fetchedAtMillis = 1L,
            totalShards = 2,
            pageSize = 500,
        )
        val fetched = mutableListOf<String>()
        val mediator = mediator(
            cache = cache,
            shardIndex = shardIndex(pages = 2),
            shard = { shard ->
                fetched += shard.path
                BillsManifest(generatedAt = "x", congress = 119, bills = listOf(bill("hr-${shard.page}")))
            },
        )

        val result = mediator.load(LoadType.APPEND, emptyState())

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertTrue(
            "shard 1 of 2 is the last page",
            (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached,
        )
        assertEquals(listOf("congress119_bills_p002.json"), fetched)
        assertEquals(2, cache.loadShardCursor(119, BillSource.PUBLISHED)?.nextShardIndex)
    }

    @Test
    fun `sharded append past the last shard is complete with no fetch`() = runTest {
        val cache = FakeBillCache()
        cache.appendShard(
            congress = 119,
            source = BillSource.PUBLISHED,
            shardIndex = 1,
            bills = listOf(bill("hr-1")),
            generatedAt = "x",
            fetchedAtMillis = 1L,
            totalShards = 2,
            pageSize = 500,
        )
        cache.appends.clear()
        var shardFetches = 0
        val mediator = mediator(
            cache = cache,
            shardIndex = shardIndex(pages = 2),
            shard = { shardFetches++; BillsManifest(generatedAt = "x", congress = 119, bills = emptyList()) },
        )

        val result = mediator.load(LoadType.APPEND, emptyState())

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals("cursor already past the end → no shard fetched", 0, shardFetches)
        assertTrue(cache.appends.isEmpty())
    }

    @Test
    fun `a fetch failure surfaces as a mediator error, never a silent partial list`() = runTest {
        val cache = FakeBillCache()
        val boom = IOException("offline")
        val mediator = mediator(
            cache = cache,
            shardIndex = { throw boom },
        )

        val result = mediator.load(LoadType.REFRESH, emptyState())

        assertTrue(result is RemoteMediator.MediatorResult.Error)
        assertEquals(boom, (result as RemoteMediator.MediatorResult.Error).throwable)
        assertTrue("no rows written on failure", cache.appends.isEmpty())
    }

    @Test
    fun `initialize skips the initial refresh when the shard set is fully cached`() = runTest {
        val cache = FakeBillCache()
        cache.appendShard(
            congress = 119,
            source = BillSource.PUBLISHED,
            shardIndex = 0,
            bills = listOf(bill("hr-0")),
            generatedAt = "x",
            fetchedAtMillis = 1L,
            totalShards = 1,
            pageSize = 500,
        )
        val mediator = mediator(
            cache = cache,
            shardIndex = { error("initialize must not fetch") },
            loadInitialCursor = { cache.loadShardCursor(119, BillSource.PUBLISHED) },
        )

        assertEquals(
            RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH,
            mediator.initialize(),
        )
    }

    @Test
    fun `initialize launches the initial refresh when the cursor is absent or incomplete`() = runTest {
        val cache = FakeBillCache()
        val cold = mediator(
            cache = cache,
            shardIndex = { null },
            loadInitialCursor = { cache.loadShardCursor(119, BillSource.PUBLISHED) },
        )
        assertEquals(
            "no cursor yet → cold start refreshes",
            RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH,
            cold.initialize(),
        )

        cache.appendShard(
            congress = 119,
            source = BillSource.PUBLISHED,
            shardIndex = 0,
            bills = listOf(bill("hr-0")),
            generatedAt = "x",
            fetchedAtMillis = 1L,
            totalShards = 2,
            pageSize = 500,
        )
        val partial = mediator(
            cache = cache,
            shardIndex = { null },
            loadInitialCursor = { cache.loadShardCursor(119, BillSource.PUBLISHED) },
        )
        assertEquals(
            "shard 0 of 2 cached → still more to fetch",
            RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH,
            partial.initialize(),
        )
    }

    // --- helpers ---------------------------------------------------------

    private fun mediator(
        cache: FakeBillCache,
        shardIndex: suspend () -> BillShardIndex?,
        shard: suspend (BillShard) -> BillsManifest = { error("shard fetch not expected") },
        wholeManifest: suspend () -> BillsManifest = { error("whole manifest fetch not expected") },
        loadInitialCursor: suspend () -> com.informedcitizen.data.cache.ShardCursor? = { null },
    ) = BillsRemoteMediator(
        source = BillSource.PUBLISHED,
        billCache = cache,
        fetchShardIndex = shardIndex,
        fetchShard = shard,
        fetchWholeManifest = wholeManifest,
        loadInitialCursor = loadInitialCursor,
        now = { 42L },
    )

    private fun mediator(
        cache: FakeBillCache,
        shardIndex: BillShardIndex?,
        shard: suspend (BillShard) -> BillsManifest = { error("shard fetch not expected") },
        wholeManifest: suspend () -> BillsManifest = { error("whole manifest fetch not expected") },
    ) = mediator(cache, { shardIndex }, shard, wholeManifest)

    private fun shardIndex(pages: Int) = BillShardIndex(
        generatedAt = "x",
        congress = 119,
        pageSize = 500,
        totalBills = pages,
        shards = (1..pages).map { page ->
            BillShard(page = page, path = "congress119_bills_p%03d.json".format(page), count = 1)
        },
    )

    private fun emptyState() = PagingState<Int, Bill>(
        pages = emptyList(),
        anchorPosition = null,
        config = PagingConfig(pageSize = 20),
        leadingPlaceholderCount = 0,
    )

    private fun bill(id: String) = Bill(
        id = id,
        congress = 119,
        type = "hr",
        number = "1",
        title = "Sample $id",
        sponsor = Sponsor(name = "Test", party = "D", state = "CA"),
        introducedDate = "2026-01-01",
        latestAction = Action(date = "2026-01-02", text = "Test"),
        outcome = Outcome.PASSED_HOUSE,
        congressGovUrl = "https://example.com/$id",
    )
}
