package com.informedcitizen.data.byok

import com.informedcitizen.pipeline.fetch.LIST_PAGES_MAX
import com.informedcitizen.pipeline.fetch.RECENT_DAYS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ByokBillsCoverageTest {

    @Test
    fun `window mirrors the shared fetch cutoff`() {
        assertEquals(RECENT_DAYS, BYOK_BILL_WINDOW_DAYS)
    }

    @Test
    fun `request estimate is list walk plus four enrichment GETs per bill`() {
        // 100 bills * 4 GETs + the list-page walk.
        assertEquals(LIST_PAGES_MAX + 400, byokBillSyncRequestEstimate(100))
        // No bills still pays the list walk.
        assertEquals(LIST_PAGES_MAX, byokBillSyncRequestEstimate(0))
    }

    @Test
    fun `request estimate honours a custom list-page count`() {
        assertEquals(2 + 40, byokBillSyncRequestEstimate(billsEnriched = 10, listPages = 2))
    }

    @Test
    fun `request estimate rejects negative inputs`() {
        assertThrows(IllegalArgumentException::class.java) { byokBillSyncRequestEstimate(-1) }
        assertThrows(IllegalArgumentException::class.java) {
            byokBillSyncRequestEstimate(billsEnriched = 1, listPages = -1)
        }
    }

    @Test
    fun `per-run cap keeps a full sync inside one key's hourly budget`() {
        val cap = byokMaxBillsPerRun()
        // The cap must be positive and a run of exactly the cap must fit.
        assertTrue(cap > 0)
        assertTrue(byokSyncFitsBudget(cap))
        // One more bill than the cap must NOT fit — the cap is the true ceiling.
        assertFalse(byokSyncFitsBudget(cap + 1))
    }

    @Test
    fun `per-run cap reserves the list-page budget`() {
        // (5000 - 8) / 4 = 1248, using the real defaults.
        assertEquals((CONGRESS_HOURLY_REQUEST_BUDGET - LIST_PAGES_MAX) / REQUESTS_PER_BILL, byokMaxBillsPerRun())
    }

    @Test
    fun `a budget too small for even the list walk yields a zero cap`() {
        assertEquals(0, byokMaxBillsPerRun(budget = LIST_PAGES_MAX))
        assertEquals(0, byokMaxBillsPerRun(budget = 0))
    }

    @Test
    fun `naive full broadened-set fetch is correctly reported as over budget`() {
        // ~10,000 bills * 4 GETs = 40,000 requests — far past the 5,000/hr budget.
        assertFalse(byokSyncFitsBudget(10_000))
    }

    @Test
    fun `coverage statement names the window and the published fallback`() {
        assertTrue(BYOK_BILLS_COVERAGE_STATEMENT.contains(BYOK_BILL_WINDOW_DAYS.toString()))
        assertTrue(BYOK_BILLS_COVERAGE_STATEMENT.contains("published data"))
    }
}
