package com.informedcitizen.data.cache

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.informedcitizen.cache.BillSummaryDatabase
import com.informedcitizen.pipeline.model.Action
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.LifecycleStatus
import com.informedcitizen.pipeline.model.Outcome
import com.informedcitizen.pipeline.model.Sponsor
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Schema-level coverage for backlog #41's sharded-paging foundation (extracted
 * `status` / `policy_area` columns, the `cached_shard_cursor` table, and the
 * DB-filtered paging queries) plus the 4.sqm migration — including its repair of
 * the `cached_election_calendar` table that iter-14 added to the .sq for fresh
 * installs but never migrated for upgrading devices.
 */
class BillCachePagingSchemaTest {

    private lateinit var driver: JdbcSqliteDriver
    private val json = Json { ignoreUnknownKeys = true }

    @Before fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    }

    @After fun tearDown() { driver.close() }

    // ---- migration -------------------------------------------------------

    @Test fun `migration v4 to v5 adds paging schema and repairs missing election calendar table`() {
        // Reproduce a device that MIGRATED into schema v4 (v1->v2->v3->v4): it has
        // the pre-#41 cached_bill columns and, crucially, NO cached_election_calendar
        // — that table was only ever created for fresh installs via ArtifactCache.sq.
        BillSummaryDatabase.Schema.migrate(driver, 0, 4)
        assertFalse("v4 migration path never created it", driver.tableExists("cached_election_calendar"))
        assertFalse(driver.tableExists("cached_shard_cursor"))
        assertFalse("shard_index" in driver.columnsOf("cached_bill"))

        BillSummaryDatabase.Schema.migrate(driver, 4, 5)

        assertTrue("4.sqm repairs the missing table", driver.tableExists("cached_election_calendar"))
        assertTrue(driver.tableExists("cached_shard_cursor"))
        assertTrue(
            driver.columnsOf("cached_bill").containsAll(setOf("shard_index", "status", "policy_area")),
        )
    }

    @Test fun `migration v4 to v5 tolerates an already-present election calendar table`() {
        // A device that FRESH-installed a post-iter-14 v4 build already has the table.
        // The 4.sqm CREATE TABLE IF NOT EXISTS must not error on that device.
        BillSummaryDatabase.Schema.migrate(driver, 0, 4)
        driver.execute(
            null,
            """
            CREATE TABLE cached_election_calendar (
                source TEXT NOT NULL PRIMARY KEY,
                payload TEXT NOT NULL,
                generated_at TEXT NOT NULL,
                fetched_at INTEGER NOT NULL
            )
            """.trimIndent(),
            0,
        )
        // Must complete without throwing "table already exists".
        BillSummaryDatabase.Schema.migrate(driver, 4, 5)
        assertTrue(driver.tableExists("cached_election_calendar"))
    }

    // ---- extracted-column filtering + paging -----------------------------

    @Test fun `extracted columns power status and policy-area filtered paging`() = runTest {
        BillSummaryDatabase.Schema.create(driver)
        val db = BillSummaryDatabase(driver)
        val cache = SqlDelightBillCache(db)
        val q = db.billCacheQueries
        cache.replaceForSource(
            congress = 119,
            source = BillSource.PUBLISHED,
            bills = listOf(
                bill("hr1-119", "2026-04-10", LifecycleStatus.INTRODUCED, "Health"),
                bill("hr2-119", "2026-04-09", LifecycleStatus.IN_COMMITTEE, "Health"),
                bill("hr3-119", "2026-04-08", LifecycleStatus.IN_COMMITTEE, "Taxation"),
                // A floor-outcome bill: lifecycleStatus is null, so `status` is null
                // and it is excluded from every status filter (issue #39 precedence).
                bill("hr4-119", "2026-04-07", null, null, outcome = Outcome.ENACTED),
            ),
            generatedAt = "g",
            fetchedAtMillis = 1L,
        )

        // Paging: recency order, stable across LIMIT/OFFSET boundaries.
        assertEquals(
            listOf("hr1-119", "hr2-119"),
            ids(q.selectBillsPaged(119, "published", null, null, limit = 2, offset = 0).executeAsList()),
        )
        assertEquals(
            listOf("hr3-119", "hr4-119"),
            ids(q.selectBillsPaged(119, "published", null, null, limit = 2, offset = 2).executeAsList()),
        )

        // Optional filters (null bind matches everything).
        assertEquals(4L, q.countBillsPaged(119, "published", null, null).executeAsOne())
        assertEquals(2L, q.countBillsPaged(119, "published", "in_committee", null).executeAsOne())
        assertEquals(1L, q.countBillsPaged(119, "published", "introduced", null).executeAsOne())
        assertEquals(2L, q.countBillsPaged(119, "published", null, "Health").executeAsOne())
        assertEquals(1L, q.countBillsPaged(119, "published", "in_committee", "Health").executeAsOne())
        assertEquals(
            listOf("hr2-119"),
            ids(q.selectBillsPaged(119, "published", "in_committee", "Health", 10, 0).executeAsList()),
        )

        // Distinct policy areas for the #10 dropdown — nulls dropped, sorted.
        assertEquals(listOf("Health", "Taxation"), q.selectDistinctPolicyAreas(119, "published").executeAsList())
    }

    @Test fun `shard cursor round-trips and clears per source`() = runTest {
        BillSummaryDatabase.Schema.create(driver)
        val q = BillSummaryDatabase(driver).billCacheQueries
        q.upsertShardCursor(119, "published", next_shard_index = 3, total_shards = 20, page_size = 500)
        val cursor = q.selectShardCursor(119, "published").executeAsOne()
        assertEquals(3L, cursor.next_shard_index)
        assertEquals(20L, cursor.total_shards)
        assertEquals(500L, cursor.page_size)
        q.clearShardCursorForSource(119, "published")
        assertNull(q.selectShardCursor(119, "published").executeAsOneOrNull())
    }

    @Test fun `clearShardForSource removes only the targeted shard's rows`() = runTest {
        BillSummaryDatabase.Schema.create(driver)
        val q = BillSummaryDatabase(driver).billCacheQueries
        q.upsertBill("a-119", 119, "published", "2026-04-01", "{}", 1L, shard_index = 0, status = null, policy_area = null)
        q.upsertBill("b-119", 119, "published", "2026-04-02", "{}", 1L, shard_index = 1, status = null, policy_area = null)
        q.clearShardForSource(119, "published", shardIndex = 0)
        assertEquals(1L, q.countBillsBySource(119, "published").executeAsOne())
    }

    // ---- sharded append + paged reads via the cache API ------------------

    @Test fun `appendShard tags rows, advances the cursor, and powers DB-filtered pages`() = runTest {
        BillSummaryDatabase.Schema.create(driver)
        val cache = SqlDelightBillCache(BillSummaryDatabase(driver))

        cache.appendShard(
            congress = 119,
            source = BillSource.PUBLISHED,
            shardIndex = 0,
            bills = listOf(
                bill("hr1-119", "2026-04-10", LifecycleStatus.INTRODUCED, "Health"),
                bill("hr2-119", "2026-04-09", LifecycleStatus.IN_COMMITTEE, "Taxation"),
            ),
            generatedAt = "g0",
            fetchedAtMillis = 10L,
            totalShards = 2,
            pageSize = 2,
        )
        cache.appendShard(
            congress = 119,
            source = BillSource.PUBLISHED,
            shardIndex = 1,
            bills = listOf(bill("hr3-119", "2026-04-08", LifecycleStatus.REPORTED, "Health")),
            generatedAt = "g1",
            fetchedAtMillis = 20L,
            totalShards = 2,
            pageSize = 2,
        )

        // Cursor advanced to the next shard and now covers every published shard.
        val cursor = cache.loadShardCursor(119, BillSource.PUBLISHED)!!
        assertEquals(2, cursor.nextShardIndex)
        assertEquals(2, cursor.totalShards)
        assertEquals(2, cursor.pageSize)
        assertTrue(cursor.isComplete)

        // Cross-shard recency paging is stable across LIMIT/OFFSET.
        assertEquals(3, cache.countBills(119, BillSource.PUBLISHED, null, null))
        assertEquals(
            listOf("hr1-119", "hr2-119"),
            cache.loadBillsPaged(119, BillSource.PUBLISHED, null, null, limit = 2, offset = 0).map { it.id },
        )
        assertEquals(
            listOf("hr3-119"),
            cache.loadBillsPaged(119, BillSource.PUBLISHED, null, null, limit = 2, offset = 2).map { it.id },
        )

        // DB-side filters span shards (Health lives in shard 0 and shard 1).
        assertEquals(2, cache.countBills(119, BillSource.PUBLISHED, null, "Health"))
        assertEquals(
            listOf("hr1-119", "hr3-119"),
            cache.loadBillsPaged(119, BillSource.PUBLISHED, null, "Health", limit = 10, offset = 0).map { it.id },
        )
        assertEquals(1, cache.countBills(119, BillSource.PUBLISHED, "reported", null))
        assertEquals(listOf("Health", "Taxation"), cache.distinctPolicyAreas(119, BillSource.PUBLISHED))
    }

    @Test fun `re-appending a shard prunes only its own rows`() = runTest {
        BillSummaryDatabase.Schema.create(driver)
        val cache = SqlDelightBillCache(BillSummaryDatabase(driver))
        cache.appendShard(
            119, BillSource.PUBLISHED, shardIndex = 0,
            bills = listOf(bill("hr1-119", "2026-04-10", null, "Health")),
            generatedAt = "g", fetchedAtMillis = 1L, totalShards = 2, pageSize = 1,
        )
        cache.appendShard(
            119, BillSource.PUBLISHED, shardIndex = 1,
            bills = listOf(bill("hr2-119", "2026-04-09", null, "Health")),
            generatedAt = "g", fetchedAtMillis = 1L, totalShards = 2, pageSize = 1,
        )

        // A partial refresh of shard 0 drops its vanished bill without touching shard 1.
        cache.appendShard(
            119, BillSource.PUBLISHED, shardIndex = 0,
            bills = listOf(bill("hr9-119", "2026-04-11", null, "Health")),
            generatedAt = "g2", fetchedAtMillis = 2L, totalShards = 2, pageSize = 1,
        )

        assertEquals(
            listOf("hr9-119", "hr2-119"),
            cache.loadBillsPaged(119, BillSource.PUBLISHED, null, null, limit = 10, offset = 0).map { it.id },
        )
    }

    @Test fun `loadShardCursor is null before the first append and isComplete tracks progress`() = runTest {
        BillSummaryDatabase.Schema.create(driver)
        val cache = SqlDelightBillCache(BillSummaryDatabase(driver))
        assertNull(cache.loadShardCursor(119, BillSource.PUBLISHED))

        cache.appendShard(
            119, BillSource.PUBLISHED, shardIndex = 0,
            bills = listOf(bill("hr1-119", "2026-04-10", null, null)),
            generatedAt = "g", fetchedAtMillis = 1L, totalShards = 3, pageSize = 1,
        )
        assertFalse(cache.loadShardCursor(119, BillSource.PUBLISHED)!!.isComplete)
    }

    // ---- helpers ---------------------------------------------------------

    private fun ids(payloads: List<String>): List<String> =
        payloads.map { json.decodeFromString(Bill.serializer(), it).id }

    private fun bill(
        id: String,
        date: String,
        status: LifecycleStatus?,
        policyArea: String?,
        outcome: Outcome = Outcome.UNKNOWN,
    ): Bill = Bill(
        id = id,
        congress = 119,
        type = id.takeWhile { it.isLetter() },
        number = id.dropWhile { it.isLetter() }.substringBefore("-"),
        title = "T",
        sponsor = Sponsor("X", "D", "CA"),
        introducedDate = "2026-01-01",
        latestAction = Action(date = date, text = "any"),
        outcome = outcome,
        lifecycleStatus = status,
        policyArea = policyArea,
        congressGovUrl = "https://example/$id",
    )

    private fun JdbcSqliteDriver.tableExists(name: String): Boolean =
        executeQuery(
            null,
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = '$name'",
            { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0) ?: 0L)
            },
            0,
        ).value > 0L

    private fun JdbcSqliteDriver.columnsOf(table: String): Set<String> {
        val cols = mutableSetOf<String>()
        executeQuery(
            null,
            "PRAGMA table_info($table)",
            { cursor ->
                while (cursor.next().value) { cols.add(cursor.getString(1)!!) }
                QueryResult.Value(Unit)
            },
            0,
        )
        return cols
    }
}
