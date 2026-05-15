package com.informedcitizen.pipeline.state

/**
 * Pure: returns a new [BackfillState] reflecting the result of the
 * most recent backfill chunk. Mirrors Python
 * `_common.advance_state(state, page_returned, pages_consumed,
 * had_non_empty_page)`.
 *
 * Exhaustion is signaled by `pageReturned < LIST_PAGE_LIMIT`, but
 * only when there's evidence the cursor actually moved during the
 * run:
 *  - `pageReturned > 0` (legitimate short terminal page), OR
 *  - [hadNonEmptyPage] (a prior page in the same run had data), OR
 *  - `activeOffset > 0` (a previous run already advanced past 0).
 *
 * An empty first page at offset 0 with [hadNonEmptyPage] = false is
 * treated as a transient Congress.gov hiccup, NOT exhaustion. We hold
 * the cursor so the next scheduled run retries instead of needing
 * manual state-file surgery to back out a false-exhausted Congress.
 * Pinned by Python `test_advance_state_empty_first_page_at_offset_zero_does_not_mark_complete`.
 *
 * [nowIso] is the timestamp to stamp into `lastRunAt` (Python uses
 * `now_iso()`). Passed in rather than read from the system clock so
 * the Kotlin port stays a pure function and tests pin behaviour with
 * fixed timestamps. Callers in production use a kotlinx-datetime
 * `Clock.System.now()` derived string.
 */
fun advanceBackfillState(
    state: BackfillState,
    pageReturned: Int,
    pagesConsumed: Int,
    hadNonEmptyPage: Boolean = false,
    nowIso: String,
): BackfillState {
    val priorOffset = state.activeOffset
    val active = state.activeCongress

    if (pageReturned < LIST_PAGE_LIMIT) {
        val sawEvidence = pageReturned > 0 || hadNonEmptyPage || priorOffset > 0
        if (!sawEvidence) {
            // Transient empty page; hold the cursor, just bump the timestamp.
            return state.copy(lastRunAt = nowIso)
        }
        val newCompleted = if (active != null && active !in state.completed) {
            state.completed + active
        } else {
            state.completed
        }
        val newQueue = state.queue.filter { it != active }
        return state.copy(
            activeCongress = newQueue.firstOrNull(),
            activeOffset = 0,
            queue = newQueue,
            completed = newCompleted,
            lastRunAt = nowIso,
        )
    }
    return state.copy(
        activeOffset = priorOffset + pagesConsumed * LIST_PAGE_LIMIT,
        lastRunAt = nowIso,
    )
}
