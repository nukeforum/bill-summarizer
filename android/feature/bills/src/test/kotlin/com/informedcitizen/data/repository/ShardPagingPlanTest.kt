package com.informedcitizen.data.repository

import com.informedcitizen.data.cache.ShardCursor
import com.informedcitizen.pipeline.model.BillShard
import com.informedcitizen.pipeline.model.BillShardIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ShardPagingPlanTest {

    private fun shard(page: Int) = BillShard(page = page, path = "congress119_bills_p%03d.json".format(page), count = 500)

    private fun index(pageCount: Int, pageSize: Int = 500) =
        BillShardIndex(
            congress = 119,
            pageSize = pageSize,
            totalBills = pageCount * pageSize,
            shards = (1..pageCount).map { shard(it) },
        )

    @Test
    fun `unsharded refresh loads the whole manifest`() {
        assertSame(ShardStep.WholeManifest, nextShardStep(index = null, cursor = null, refresh = true))
    }

    @Test
    fun `unsharded append is complete - nothing more than the one manifest page`() {
        assertSame(ShardStep.Complete, nextShardStep(index = null, cursor = null, refresh = false))
    }

    @Test
    fun `empty shard index refresh falls back to the whole manifest`() {
        val empty = BillShardIndex(congress = 119, pageSize = 500, shards = emptyList())
        assertSame(ShardStep.WholeManifest, nextShardStep(index = empty, cursor = null, refresh = true))
    }

    @Test
    fun `sharded refresh fetches the newest shard first`() {
        val step = nextShardStep(index = index(pageCount = 3), cursor = null, refresh = true) as ShardStep.Fetch
        assertEquals(0, step.shardIndex)
        assertEquals(1, step.shard.page)
        assertEquals(3, step.totalShards)
        assertEquals(500, step.pageSize)
    }

    @Test
    fun `sharded refresh ignores the cursor and restarts from shard zero`() {
        val cursor = ShardCursor(nextShardIndex = 2, totalShards = 3, pageSize = 500)
        val step = nextShardStep(index = index(pageCount = 3), cursor = cursor, refresh = true) as ShardStep.Fetch
        assertEquals(0, step.shardIndex)
    }

    @Test
    fun `sharded append follows the cursor to the next shard`() {
        val cursor = ShardCursor(nextShardIndex = 1, totalShards = 3, pageSize = 500)
        val step = nextShardStep(index = index(pageCount = 3), cursor = cursor, refresh = false) as ShardStep.Fetch
        assertEquals(1, step.shardIndex)
        assertEquals(2, step.shard.page)
        assertEquals(3, step.totalShards)
    }

    @Test
    fun `append with no cursor behaves like the first page`() {
        val step = nextShardStep(index = index(pageCount = 3), cursor = null, refresh = false) as ShardStep.Fetch
        assertEquals(0, step.shardIndex)
    }

    @Test
    fun `append at the last shard is complete`() {
        val cursor = ShardCursor(nextShardIndex = 3, totalShards = 3, pageSize = 500)
        assertSame(ShardStep.Complete, nextShardStep(index = index(pageCount = 3), cursor = cursor, refresh = false))
    }

    @Test
    fun `append total shards re-derives from a grown index, not the stale cursor`() {
        // Cursor was written when the index had 3 shards; the index has since grown to 5.
        val cursor = ShardCursor(nextShardIndex = 3, totalShards = 3, pageSize = 500)
        val step = nextShardStep(index = index(pageCount = 5), cursor = cursor, refresh = false) as ShardStep.Fetch
        assertEquals(3, step.shardIndex)
        assertEquals(5, step.totalShards)
    }

    @Test
    fun `append whose cursor points past a shrunk index is complete`() {
        // Cursor points at shard 4 but the index shrank back to 2 shards.
        val cursor = ShardCursor(nextShardIndex = 4, totalShards = 5, pageSize = 500)
        assertSame(ShardStep.Complete, nextShardStep(index = index(pageCount = 2), cursor = cursor, refresh = false))
    }
}
