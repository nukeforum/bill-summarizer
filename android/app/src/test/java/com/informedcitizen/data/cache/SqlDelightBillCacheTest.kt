package com.informedcitizen.data.cache

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.informedcitizen.cache.BillSummaryDatabase
import com.informedcitizen.pipeline.model.Action
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.Outcome
import com.informedcitizen.pipeline.model.Sponsor
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun bill(
    id: String,
    congress: Int = 119,
    date: String = "2026-04-01",
    title: String = "T",
    outcome: Outcome = Outcome.ENACTED,
): Bill = Bill(
    id = id,
    congress = congress,
    type = id.takeWhile { it.isLetter() },
    number = id.dropWhile { it.isLetter() }.substringBefore("-"),
    title = title,
    sponsor = Sponsor("X", "D", "CA"),
    introducedDate = "2026-01-01",
    latestAction = Action(date = date, text = "any"),
    outcome = outcome,
    congressGovUrl = "https://example/$id",
)

class SqlDelightBillCacheTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var cache: SqlDelightBillCache

    @Before fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        BillSummaryDatabase.Schema.create(driver)
        cache = SqlDelightBillCache(BillSummaryDatabase(driver))
    }

    @After fun tearDown() { driver.close() }

    @Test fun `replace and load round-trip preserves order desc by latest_action_date`() = runTest {
        cache.replaceForSource(
            congress = 119,
            source = BillSource.PUBLISHED,
            bills = listOf(
                bill("hr1-119", date = "2026-04-05"),
                bill("hr2-119", date = "2026-04-10"),
                bill("hr3-119", date = "2026-04-01"),
            ),
            generatedAt = "2026-05-15T00:00:00Z",
            fetchedAtMillis = 1_700_000_000_000L,
        )
        val loaded = cache.loadBills(119, BillSource.PUBLISHED)
        assertEquals(listOf("hr2-119", "hr1-119", "hr3-119"), loaded.map { it.id })
    }

    @Test fun `replaceForSource clears prior rows for that source`() = runTest {
        cache.replaceForSource(119, BillSource.PUBLISHED, listOf(bill("hr1-119")), "g1", 1L)
        cache.replaceForSource(119, BillSource.PUBLISHED, listOf(bill("hr2-119")), "g2", 2L)
        val loaded = cache.loadBills(119, BillSource.PUBLISHED)
        assertEquals(listOf("hr2-119"), loaded.map { it.id })
    }

    @Test fun `published and byok sources are isolated`() = runTest {
        cache.replaceForSource(119, BillSource.PUBLISHED, listOf(bill("hr-pub-1")), "g", 1L)
        cache.replaceForSource(119, BillSource.BYOK, listOf(bill("hr-byok-1")), "g", 2L)
        val pub = cache.loadBills(119, BillSource.PUBLISHED).map { it.id }
        val byok = cache.loadBills(119, BillSource.BYOK).map { it.id }
        assertEquals(listOf("hr-pub-1"), pub)
        assertEquals(listOf("hr-byok-1"), byok)
        val all = cache.loadAllForCongress(119).map { it.id }.toSet()
        assertEquals(setOf("hr-pub-1", "hr-byok-1"), all)
    }

    @Test fun `loadManifest returns the most recent stamp`() = runTest {
        cache.replaceForSource(119, BillSource.PUBLISHED, emptyList(), "2026-05-15T00:00:00Z", 12345L)
        val meta = cache.loadManifest(119, BillSource.PUBLISHED)
        assertNotNull(meta)
        assertEquals("2026-05-15T00:00:00Z", meta!!.generatedAt)
        assertEquals(12345L, meta.fetchedAtMillis)
    }

    @Test fun `loadManifest returns null when no rows for the source`() = runTest {
        val meta = cache.loadManifest(119, BillSource.PUBLISHED)
        assertNull(meta)
    }

    @Test fun `bill payload survives round-trip with all fields including nullable text URLs`() = runTest {
        val original = Bill(
            id = "hr1-119",
            congress = 119,
            type = "hr",
            number = "1",
            title = "Full Title",
            shortTitle = "Short",
            sponsor = Sponsor("Sponsor Name", "R", "TX"),
            introducedDate = "2026-01-15",
            latestAction = Action("2026-04-01", "Became Public Law"),
            outcome = Outcome.ENACTED,
            summaryCrs = "CRS summary content",
            textUrlHtml = "https://x/bill.htm",
            textUrlXml = null,
            textUrlPdf = "https://x/bill.pdf",
            congressGovUrl = "https://www.congress.gov/bill/119th-congress/house-bill/1",
        )
        cache.replaceForSource(119, BillSource.PUBLISHED, listOf(original), "g", 1L)
        val loaded = cache.loadBills(119, BillSource.PUBLISHED).single()
        assertEquals(original, loaded)
    }

    @Test fun `clearSource removes rows for the targeted source only`() = runTest {
        cache.replaceForSource(119, BillSource.PUBLISHED, listOf(bill("hr1-119")), "g", 1L)
        cache.replaceForSource(119, BillSource.BYOK, listOf(bill("hr2-119")), "g", 2L)
        cache.clearSource(119, BillSource.PUBLISHED)
        assertTrue(cache.loadBills(119, BillSource.PUBLISHED).isEmpty())
        assertEquals(listOf("hr2-119"), cache.loadBills(119, BillSource.BYOK).map { it.id })
    }

    @Test fun `clearAll wipes every row and manifest`() = runTest {
        cache.replaceForSource(119, BillSource.PUBLISHED, listOf(bill("hr1-119")), "g", 1L)
        cache.replaceForSource(118, BillSource.PUBLISHED, listOf(bill("hr2-118", 118)), "g", 2L)
        cache.clearAll()
        assertTrue(cache.loadAllForCongress(119).isEmpty())
        assertTrue(cache.loadAllForCongress(118).isEmpty())
        assertNull(cache.loadManifest(119, BillSource.PUBLISHED))
    }

    @Test fun `bill_source fromWire is exhaustive over wireString values`() {
        assertEquals(BillSource.PUBLISHED, BillSource.fromWire("published"))
        assertEquals(BillSource.BYOK, BillSource.fromWire("byok"))
        assertNull(BillSource.fromWire("unknown"))
    }
}
