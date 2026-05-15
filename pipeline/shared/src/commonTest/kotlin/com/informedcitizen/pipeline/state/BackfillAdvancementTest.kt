package com.informedcitizen.pipeline.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val FIXED_NOW = "2026-05-15T12:00:00Z"

class BackfillAdvancementTest {
    @Test
    fun full_page_bumps_offset_keeps_active() {
        val state = initialBackfillState(119)
        val advanced = advanceBackfillState(
            state,
            pageReturned = LIST_PAGE_LIMIT,
            pagesConsumed = 4,
            nowIso = FIXED_NOW,
        )
        assertEquals(119, advanced.activeCongress)
        assertEquals(4 * LIST_PAGE_LIMIT, advanced.activeOffset)
        assertTrue(119 !in advanced.completed)
        assertEquals(FIXED_NOW, advanced.lastRunAt)
    }

    @Test
    fun short_page_marks_complete_and_pops_queue() {
        val state = initialBackfillState(119)
        val advanced = advanceBackfillState(
            state,
            pageReturned = 12,
            pagesConsumed = 1,
            nowIso = FIXED_NOW,
        )
        assertTrue(119 in advanced.completed)
        assertEquals(118, advanced.activeCongress)
        assertEquals(0, advanced.activeOffset)
        assertTrue(119 !in advanced.queue)
        assertEquals(118, advanced.queue.first())
    }

    @Test
    fun short_page_exhausts_last_congress_to_null_active() {
        val state = BackfillState(
            activeCongress = 93,
            activeOffset = 1500,
            queue = listOf(93),
            completed = (94..119).toList(),
            lastRunAt = null,
        )
        val advanced = advanceBackfillState(
            state,
            pageReturned = 5,
            pagesConsumed = 1,
            nowIso = FIXED_NOW,
        )
        assertTrue(93 in advanced.completed)
        assertEquals(emptyList(), advanced.queue)
        assertNull(advanced.activeCongress)
        assertEquals(0, advanced.activeOffset)
    }

    @Test
    fun empty_first_page_at_offset_zero_holds_cursor_as_transient() {
        // Pinned by the 2026-05-05 fix: empty first page at offset 0
        // with no prior non-empty pages MUST be treated as a hiccup,
        // not exhaustion. Recovery would otherwise need manual state-
        // file surgery.
        val state = initialBackfillState(119)
        val advanced = advanceBackfillState(
            state,
            pageReturned = 0,
            pagesConsumed = 1,
            hadNonEmptyPage = false,
            nowIso = FIXED_NOW,
        )
        assertTrue(119 !in advanced.completed)
        assertEquals(119, advanced.activeCongress)
        assertEquals(0, advanced.activeOffset)
        assertEquals(state.queue, advanced.queue)
        // last_run_at still bumped — the run did happen, even if cursor held.
        assertEquals(FIXED_NOW, advanced.lastRunAt)
    }

    @Test
    fun empty_page_mid_congress_marks_complete() {
        // active_offset > 0: previous runs already fetched non-empty
        // pages. An empty page now signals real exhaustion.
        val state = BackfillState(
            activeCongress = 119,
            activeOffset = 500,
            queue = listOf(119, 118),
            completed = emptyList(),
            lastRunAt = null,
        )
        val advanced = advanceBackfillState(
            state,
            pageReturned = 0,
            pagesConsumed = 1,
            hadNonEmptyPage = false,
            nowIso = FIXED_NOW,
        )
        assertTrue(119 in advanced.completed)
        assertEquals(118, advanced.activeCongress)
        assertEquals(0, advanced.activeOffset)
    }

    @Test
    fun empty_page_after_non_empty_in_same_run_marks_complete() {
        // At offset 0 but had_non_empty_page=true: a legitimate short
        // terminal page that follows a non-empty one. Treat as
        // exhaustion.
        val state = initialBackfillState(119)
        val advanced = advanceBackfillState(
            state,
            pageReturned = 0,
            pagesConsumed = 2,
            hadNonEmptyPage = true,
            nowIso = FIXED_NOW,
        )
        assertTrue(119 in advanced.completed)
        assertEquals(118, advanced.activeCongress)
    }

    @Test
    fun completed_congress_is_not_re_added_to_completed() {
        // Idempotence guard: if an already-completed congress somehow
        // surfaces again (data anomaly), don't duplicate it in the
        // completed list. Python `if active not in new["completed"]`.
        val state = BackfillState(
            activeCongress = 119,
            activeOffset = 500,
            queue = listOf(119),
            completed = listOf(119),
            lastRunAt = null,
        )
        val advanced = advanceBackfillState(
            state,
            pageReturned = 5,
            pagesConsumed = 1,
            nowIso = FIXED_NOW,
        )
        assertEquals(listOf(119), advanced.completed)
        assertNull(advanced.activeCongress)
    }

    @Test
    fun timestamp_is_always_updated_even_when_holding_cursor() {
        val before = "2026-05-01T00:00:00Z"
        val state = initialBackfillState(119).copy(lastRunAt = before)
        val advanced = advanceBackfillState(
            state,
            pageReturned = 0,
            pagesConsumed = 1,
            hadNonEmptyPage = false,
            nowIso = FIXED_NOW,
        )
        assertEquals(FIXED_NOW, advanced.lastRunAt)
        assertEquals(state.activeCongress, advanced.activeCongress) // cursor held
    }
}
