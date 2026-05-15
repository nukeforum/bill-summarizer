package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.model.Action
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.Outcome
import com.informedcitizen.pipeline.model.Sponsor
import kotlin.test.Test
import kotlin.test.assertEquals

private fun bill(id: String, date: String, title: String = "T", outcome: Outcome = Outcome.ENACTED): Bill =
    Bill(
        id = id,
        congress = 119,
        type = id.substringBefore('-').takeWhile { it.isLetter() },
        number = id.substringBefore('-').dropWhile { it.isLetter() },
        title = title,
        sponsor = Sponsor("X", "D", "CA"),
        introducedDate = "2026-01-01",
        latestAction = Action(date = date, text = "any"),
        outcome = outcome,
        congressGovUrl = "https://example/$id",
    )

class MergeRecordsTest {
    @Test fun adds_new_bills_increments_added() {
        val (merged, stats) = mergeBillRecords(
            existing = emptyList(),
            incoming = listOf(bill("hr1-119", "2026-04-01"), bill("hr2-119", "2026-04-02")),
        )
        assertEquals(2, merged.size)
        assertEquals(2, stats.added)
        assertEquals(0, stats.updated)
        assertEquals(0, stats.unchanged)
    }

    @Test fun unchanged_bills_count_unchanged() {
        val b = bill("hr1-119", "2026-04-01")
        val (_, stats) = mergeBillRecords(existing = listOf(b), incoming = listOf(b))
        assertEquals(0, stats.added)
        assertEquals(0, stats.updated)
        assertEquals(1, stats.unchanged)
    }

    @Test fun differing_bills_count_updated() {
        val old = bill("hr1-119", "2026-04-01", title = "old")
        val new = bill("hr1-119", "2026-04-01", title = "new")
        val (merged, stats) = mergeBillRecords(existing = listOf(old), incoming = listOf(new))
        assertEquals(1, merged.size)
        assertEquals("new", merged.single().title)
        assertEquals(1, stats.updated)
        assertEquals(0, stats.added)
    }

    @Test fun output_sorted_by_latest_action_date_descending() {
        val (merged, _) = mergeBillRecords(
            existing = listOf(bill("hr1-119", "2026-04-05")),
            incoming = listOf(
                bill("hr2-119", "2026-04-10"),
                bill("hr3-119", "2026-04-01"),
            ),
        )
        val dates = merged.map { it.latestAction.date }
        assertEquals(listOf("2026-04-10", "2026-04-05", "2026-04-01"), dates)
    }

    @Test fun bills_only_in_existing_are_preserved() {
        val (merged, _) = mergeBillRecords(
            existing = listOf(bill("hr1-119", "2026-04-01"), bill("hr2-119", "2026-04-02")),
            incoming = listOf(bill("hr2-119", "2026-04-02")),
        )
        assertEquals(setOf("hr1-119", "hr2-119"), merged.map { it.id }.toSet())
    }
}
