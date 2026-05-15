package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.ErrorCollector
import com.informedcitizen.pipeline.http.CongressClient
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.BillsManifest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject

/** Recent-window cutoff in days. Mirrors Python `fetch_bills.RECENT_DAYS = 60`. */
const val RECENT_DAYS: Int = 60

/** Number of `no_outcome_match` examples to surface in run logs. Mirrors Python `SAMPLE_REJECTIONS = 8`. */
const val SAMPLE_REJECTIONS: Int = 8

/** Summary of a single fetch-bills run, returned by [fetchBills] for CLI output. */
data class FetchBillsResult(
    val congress: Int,
    val cutoff: Instant,
    val evaluated: Int,
    val keptRecords: List<Bill>,
    val rejectionCounts: Map<String, Int>,
    val rejectionSamples: List<String>,
    val mergeStats: MergeStats,
    val finalManifest: BillsManifest,
)

/**
 * Full fetch-bills orchestrator. Mirrors the Python `fetch_bills.main`
 * flow:
 *
 *  1. Paginated list (filtered by `fromDateTime = cutoff`).
 *  2. Per-summary filter ([evaluateBill]); tally rejections.
 *  3. Parallel enrichment ([buildBillRecordsParallel]).
 *  4. Dedupe by id (rare; same bill can appear twice across pages
 *     when its updateDate shifts mid-walk).
 *  5. Sort by latest_action.date desc.
 *  6. Merge into the existing manifest ([mergeBillRecords]).
 *  7. Persist via [manifestStore].
 *
 * Note: this slice does NOT rewrite `congresses.json` — the CI
 * cut-over leaves that step on the Python side until a later slice
 * ports `rebuild_index`. The fetch-bills + index-rebuild can run as
 * two consecutive workflow steps until then.
 */
suspend fun fetchBills(
    client: CongressClient,
    congress: Int,
    cutoff: Instant,
    nowIso: String,
    manifestStore: FileBillsManifestStore,
    errors: ErrorCollector,
    maxListPages: Int = LIST_PAGES_MAX,
    maxWorkers: Int = ENRICH_WORKERS,
): FetchBillsResult {
    // Phase 1: paginated list + filter.
    val rejectCounts = mutableMapOf<String, Int>()
    val rejectionSamples = mutableListOf<String>()
    val keptSummaries = mutableListOf<Pair<JsonObject, String>>()
    var totalEvaluated = 0

    for (summary in listRecentBills(client, congress, cutoff, maxListPages)) {
        totalEvaluated++
        when (val result = evaluateBill(summary, cutoff)) {
            is BillEvaluationResult.Kept -> keptSummaries += summary to result.outcome
            is BillEvaluationResult.Rejected -> {
                rejectCounts.bump(result.reason)
                if (result.reason == RejectionReasons.NO_OUTCOME_MATCH &&
                    rejectionSamples.size < SAMPLE_REJECTIONS
                ) {
                    val latestAction = summary.jsonObjectField("latestAction")
                    val actionText = latestAction?.stringField("text").orEmpty()
                    val ref = "${summary.stringField("type")}${summary.stringField("number")}"
                    rejectionSamples += "$ref: ${actionText.take(140)}"
                }
            }
        }
    }

    // Phase 2: parallel enrichment.
    val (freshRecords, buildFailures) =
        buildBillRecordsParallel(client, congress, keptSummaries, errors, maxWorkers)
    if (buildFailures > 0) {
        rejectCounts.bump(RejectionReasons.BUILD_ERROR, buildFailures)
    }

    // Dedupe by id (rare cross-page repeats), then sort desc by latest_action.date.
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
    val sorted = deduped.sortedByDescending { it.latestAction.date }

    // Merge into the existing manifest and persist.
    val existing = manifestStore.load(congress)?.bills.orEmpty()
    val (merged, mergeStats) = mergeBillRecords(existing, sorted)
    val finalManifest = manifestStore.save(congress, merged, nowIso)

    return FetchBillsResult(
        congress = congress,
        cutoff = cutoff,
        evaluated = totalEvaluated,
        keptRecords = sorted,
        rejectionCounts = rejectCounts.toMap(),
        rejectionSamples = rejectionSamples.toList(),
        mergeStats = mergeStats,
        finalManifest = finalManifest,
    )
}

// `MutableMap.merge` is JVM-only — Kotlin Native (iOS targets) lacks it.
// Hand-rolled increment keeps commonMain portable.
private fun MutableMap<String, Int>.bump(key: String, by: Int = 1) {
    this[key] = (this[key] ?: 0) + by
}
