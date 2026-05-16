package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.ErrorCollector
import com.informedcitizen.pipeline.http.CongressClient
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.BillsManifest
import com.informedcitizen.pipeline.state.BACKFILL_PAGES_PER_RUN
import com.informedcitizen.pipeline.state.BackfillState
import com.informedcitizen.pipeline.state.LIST_PAGE_LIMIT
import com.informedcitizen.pipeline.state.advanceBackfillState
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject

/**
 * Effectively no date floor — the backfill keeps every passage-action
 * bill it finds, regardless of how long ago the action was. Python:
 * `NO_DATE_CUTOFF = datetime(1970, 1, 1, tzinfo=timezone.utc)`.
 */
private val BACKFILL_NO_DATE_CUTOFF: Instant = Instant.fromEpochSeconds(0)

/** Summary of a single backfill run, returned by [backfillBills]. */
data class BackfillBillsResult(
    val activeCongress: Int?,
    val priorOffset: Int,
    val evaluated: Int,
    val keptRecords: List<Bill>,
    val pagesConsumed: Int,
    val lastPageSize: Int,
    val hadNonEmptyPage: Boolean,
    val rejectionCounts: Map<String, Int>,
    val mergeStats: MergeStats?,
    val finalManifest: BillsManifest?,
    val newState: BackfillState,
    /** True iff the queue was already exhausted at run start; nothing was fetched. */
    val queueWasEmpty: Boolean,
    /** True iff the empty-page guard fired and the cursor stayed put. */
    val cursorHeld: Boolean,
    /** True iff this run finished the active Congress (short page + had evidence). */
    val congressCompleted: Boolean,
)

/**
 * Historical backfill orchestrator. Direct port of Python
 * `backfill_bills.main`'s flow:
 *
 *  1. If `state.activeCongress == null`, the queue is exhausted: return
 *     early with [BackfillBillsResult.queueWasEmpty] = true. Caller
 *     should NOT save state in this case (matches Python's "don't
 *     bump `last_run_at` on a no-op run").
 *  2. Otherwise, walk up to [pagesPerRun] pages of `/bill/{congress}`
 *     starting at `state.activeOffset`, sorted ascending by
 *     `updateDate`. Apply [evaluateBill] with no date cutoff —
 *     historical bills of any age are kept if they match an outcome
 *     rule.
 *  3. Enrich kept bills in parallel via [buildBillRecordsParallel].
 *  4. Dedupe by id.
 *  5. Merge into the existing manifest for the active Congress.
 *  6. Persist the merged manifest.
 *  7. Compute the new state via [advanceBackfillState] (which
 *     preserves the empty-page guard from the 2026-05-05 fix —
 *     transient empty first pages at offset 0 leave the cursor in
 *     place instead of marking the Congress complete).
 *  8. Return the new state plus run summary; caller persists.
 *
 * `congresses.json` rebuild is NOT performed here — same scope
 * boundary as [fetchBills]. The CI workflow keeps the Python
 * `build_dashboard.py` step until that builder is ported.
 */
suspend fun backfillBills(
    client: CongressClient,
    state: BackfillState,
    nowIso: String,
    manifestStore: FileBillsManifestStore,
    errors: ErrorCollector,
    pagesPerRun: Int = BACKFILL_PAGES_PER_RUN,
    maxWorkers: Int = ENRICH_WORKERS,
): BackfillBillsResult {
    val active = state.activeCongress
    if (active == null) {
        return BackfillBillsResult(
            activeCongress = null,
            priorOffset = state.activeOffset,
            evaluated = 0,
            keptRecords = emptyList(),
            pagesConsumed = 0,
            lastPageSize = 0,
            hadNonEmptyPage = false,
            rejectionCounts = emptyMap(),
            mergeStats = null,
            finalManifest = null,
            newState = state,
            queueWasEmpty = true,
            cursorHeld = false,
            congressCompleted = false,
        )
    }

    val priorOffset = state.activeOffset
    val rejectCounts = mutableMapOf<String, Int>()
    val keptSummaries = mutableListOf<Pair<JsonObject, String>>()
    var totalEvaluated = 0
    var lastPageSize = LIST_PAGE_LIMIT
    var pagesConsumed = 0
    var hadNonEmptyPage = false

    // Phase 1: paginate sequentially. Filter on the way through.
    for (page in 0 until pagesPerRun) {
        val pageOffset = priorOffset + page * LIST_PAGE_LIMIT
        val bills = listCongressPage(client, active, pageOffset)
        lastPageSize = bills.size
        pagesConsumed++
        if (lastPageSize > 0) hadNonEmptyPage = true

        for (summary in bills) {
            totalEvaluated++
            when (val result = evaluateBill(summary, BACKFILL_NO_DATE_CUTOFF)) {
                is BillEvaluationResult.Kept -> keptSummaries += summary to result.outcome
                is BillEvaluationResult.Rejected -> rejectCounts.bump(result.reason)
            }
        }

        if (lastPageSize < LIST_PAGE_LIMIT) break
    }

    // Phase 2: parallel enrichment.
    val (freshRecords, buildFailures) =
        buildBillRecordsParallel(client, active, keptSummaries, errors, maxWorkers)
    if (buildFailures > 0) rejectCounts.bump(RejectionReasons.BUILD_ERROR, buildFailures)

    // Dedupe by id (rare cross-page repeats).
    val seenIds = mutableSetOf<String>()
    val deduped = mutableListOf<Bill>()
    for (rec in freshRecords) {
        if (rec.id in seenIds) {
            rejectCounts.bump(RejectionReasons.DUPLICATE)
            continue
        }
        seenIds += rec.id
        deduped += rec
    }

    // Merge into the existing manifest and persist.
    val existing = manifestStore.load(active)?.bills.orEmpty()
    val (merged, mergeStats) = mergeBillRecords(existing, deduped)
    val finalManifest = manifestStore.save(active, merged, nowIso)

    // Advance state. The empty-page guard inside `advanceBackfillState`
    // distinguishes "real exhaustion" (we saw evidence) from "transient
    // empty page at offset 0 with nothing to back it up" (hold cursor).
    val newState = advanceBackfillState(
        state = state,
        pageReturned = lastPageSize,
        pagesConsumed = pagesConsumed,
        hadNonEmptyPage = hadNonEmptyPage,
        nowIso = nowIso,
    )

    // `cursorHeld` ⇒ the empty-page guard fired; we stayed on the same
    // (congress, offset). `congressCompleted` ⇒ `advanceBackfillState`
    // moved `active` into `completed`. Mutually exclusive; the third
    // case (offset bumped within the same congress) is the default.
    val cursorHeld =
        newState.activeCongress == active && newState.activeOffset == priorOffset
    val congressCompleted =
        active in newState.completed && active !in state.completed

    return BackfillBillsResult(
        activeCongress = active,
        priorOffset = priorOffset,
        evaluated = totalEvaluated,
        keptRecords = deduped,
        pagesConsumed = pagesConsumed,
        lastPageSize = lastPageSize,
        hadNonEmptyPage = hadNonEmptyPage,
        rejectionCounts = rejectCounts.toMap(),
        mergeStats = mergeStats,
        finalManifest = finalManifest,
        newState = newState,
        queueWasEmpty = false,
        cursorHeld = cursorHeld,
        congressCompleted = congressCompleted,
    )
}

// `MutableMap.merge` is JVM-only — duplicate of the helper in FetchBills.kt
// since kept private there. Trivial enough to repeat rather than promote.
private fun MutableMap<String, Int>.bump(key: String, by: Int = 1) {
    this[key] = (this[key] ?: 0) + by
}
