package com.informedcitizen.data.cache

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.informedcitizen.cache.BillSummaryDatabase
import com.informedcitizen.data.ai.BillSummary
import com.informedcitizen.data.ai.BillTopic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val MODEL = "gemini-nano-test"
private const val PROMPT = 1

class SqlDelightBillSummaryCacheTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var cache: SqlDelightBillSummaryCache

    @Before fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        BillSummaryDatabase.Schema.create(driver)
        cache = SqlDelightBillSummaryCache(
            db = BillSummaryDatabase(driver),
            modelVersion = MODEL,
            promptVersion = PROMPT,
        )
    }

    @After fun tearDown() { driver.close() }

    @Test fun `putSuccess and get round-trip`() = runTest {
        cache.putSuccess("hr-1", BillSummary("A short title", BillTopic.Tech), 1000)
        val entry = cache.get("hr-1")
        assertNotNull(entry)
        assertEquals("A short title", entry!!.summary?.generatedTitle)
        assertEquals(BillTopic.Tech, entry.summary?.topic)
        assertNull(entry.errorKind)
    }

    @Test fun `putError stores tombstone with no summary`() = runTest {
        cache.putError("hr-2", "PARSE_FAILED", 2000)
        val entry = cache.get("hr-2")!!
        assertNull(entry.summary)
        assertEquals("PARSE_FAILED", entry.errorKind)
    }

    @Test fun `observeAll emits inserted rows`() = runTest {
        cache.putSuccess("hr-3", BillSummary("Hi", BillTopic.Other), 3000)
        val all = cache.observeAll().first()
        assertEquals(1, all.size)
        assertTrue(all.containsKey("hr-3"))
    }

    @Test fun `model_version mismatch hides rows from observeAll and get`() = runTest {
        cache.putSuccess("hr-4", BillSummary("Hi", BillTopic.Other), 4000)
        val staleCache = SqlDelightBillSummaryCache(
            db = BillSummaryDatabase(driver),
            modelVersion = "different-model",
            promptVersion = PROMPT,
        )
        assertNull(staleCache.get("hr-4"))
        assertEquals(0, staleCache.observeAll().first().size)
    }

    @Test fun `enqueue then nextPending returns highest priority oldest first`() = runTest {
        cache.enqueue("a", priority = 0, bypassCap = false, enqueuedAtMillis = 100)
        cache.enqueue("b", priority = 100, bypassCap = true, enqueuedAtMillis = 200)
        cache.enqueue("c", priority = 0, bypassCap = false, enqueuedAtMillis = 50)
        val next = cache.nextPending()!!
        assertEquals("b", next.billId)
        assertTrue(next.bypassCap)
        cache.dequeue("b")
        assertEquals("c", cache.nextPending()!!.billId)
    }

    @Test fun `incrementAttemptsToday accumulates per local-date key`() = runTest {
        cache.incrementAttemptsToday("2026-05-08")
        cache.incrementAttemptsToday("2026-05-08")
        cache.incrementAttemptsToday("2026-05-09")
        assertEquals(2L, cache.attemptsToday("2026-05-08"))
        assertEquals(1L, cache.attemptsToday("2026-05-09"))
        assertEquals(0L, cache.attemptsToday("2026-05-10"))
    }
}
