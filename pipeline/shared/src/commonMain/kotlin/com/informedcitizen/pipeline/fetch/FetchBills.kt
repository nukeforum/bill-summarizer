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

/**
 * Sentinel for [fetchBills]'s `maxNew`: enrich every kept summary with
 * no per-run cap. The CLI/CI default — a server has the full 5,000
 * requests/hour budget and re-enriches bills to pick up in-window
 * updates. The BYOK path (backlog #43) passes a real cap instead so a
 * phone never blows one key's hourly budget in a single tick.
 */
const val NO_ENRICHMENT_CAP: Int = Int.MAX_VALUE

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
    /**
     * Bills that matched the filter and are not yet in the manifest but
     * were left for a future run because the `maxNew` enrichment cap was
     * reached (backlog #43). Always 0 for an uncapped run; a nonzero
     * value means a capped (BYOK) run will top up on its next tick.
     */
    val newBillsDeferred: Int = 0,
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
 *  6. Merge into the existing manifest ([mergeBillRecords]), with the
 *     derived `votes` field stripped from existing records first and
 *     re-attached from [votesStore]'s index after ([attachVoteRefs]).
 *  7. Persist via [manifestStore].
 *
 * [maxNew] caps how many *new* bills (those not already in the manifest)
 * are enriched this run — the expensive step, three Congress.gov GETs
 * each. The default [NO_ENRICHMENT_CAP] disables the cap and re-enriches
 * every kept summary, so the CLI/CI behaviour (and its Python parity) is
 * unchanged. A finite [maxNew] (the BYOK path, backlog #43) instead
 * skips summaries already in the manifest for free — the manifest is the
 * resume cursor, mirroring how [fetchVotes] skips a roll call already on
 * disk — and spends the budget only on bills not yet fetched, deferring
 * the rest to a later run ([FetchBillsResult.newBillsDeferred]).
 *
 * Note: this slice does NOT rewrite `congresses.json` — the CLI
 * wrapper ([com.informedcitizen.pipeline.cli.FetchBillsCommand])
 * does that as a separate step after this returns, mirroring
 * Python `fetch_bills.main`'s flow (`save_manifest` then
 * `rebuild_index`). Keeps this orchestrator focused on fetch + merge.
 */
suspend fun fetchBills(
    client: CongressClient,
    congress: Int,
    cutoff: Instant,
    nowIso: String,
    manifestStore: FileBillsManifestStore,
    errors: ErrorCollector,
    votesStore: FileVotesStore? = null,
    maxListPages: Int = LIST_PAGES_MAX,
    maxWorkers: Int = ENRICH_WORKERS,
    maxNew: Int = NO_ENRICHMENT_CAP,
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

    // Load the existing manifest once: it is both the merge base and —
    // for a capped run — the resume cursor. Its `votes` field is derived
    // data, stripped here so the eventual equality merge compares bill
    // data only (re-attached on the way out).
    val existing = stripVoteRefs(manifestStore.load(congress)?.bills.orEmpty())

    // Choose which kept summaries to enrich. Uncapped: all of them
    // (re-enriching in-window updates, unchanged CLI/CI parity). Capped
    // (BYOK #43): only bills not already in the manifest, newest-updated
    // first (listRecentBills sorts updateDate desc), up to [maxNew]; the
    // rest are deferred to a future tick.
    val toEnrich: List<Pair<JsonObject, String>>
    val newBillsDeferred: Int
    if (maxNew == NO_ENRICHMENT_CAP) {
        toEnrich = keptSummaries
        newBillsDeferred = 0
    } else {
        val existingIds = existing.mapTo(mutableSetOf()) { it.id }
        val newSummaries = keptSummaries.filter { (summary, _) ->
            billIdForSummary(summary, congress) !in existingIds
        }
        toEnrich = newSummaries.take(maxNew.coerceAtLeast(0))
        newBillsDeferred = newSummaries.size - toEnrich.size
    }

    // Phase 2: parallel enrichment.
    val (freshRecords, buildFailures) =
        buildBillRecordsParallel(client, congress, toEnrich, errors, maxWorkers)
    if (buildFailures > 0) {
        rejectCounts.bump(RejectionReasons.BUILD_ERROR, buildFailures)
    }

    // Dedupe by id (rare cross-page repeats), then sort desc by
    // latest_action.date with id as tiebreaker — same ordering as
    // mergeBillRecords, so the "+ id" log lines are deterministic.
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
    val sorted = deduped.sortedWith(
        compareByDescending<Bill> { it.latestAction.date }.thenBy { it.id }
    )

    // Merge into the existing manifest and persist. `votes` is derived
    // data: `existing` was already stripped above, and fresh records
    // never carry it (Congress.gov isn't the vote source), so equality
    // compares bill data only; the derived `votes` is re-attached from
    // the current votes index on the way out. Mirrors Python
    // `fetch_bills.main`'s strip/attach pair.
    val (merged, mergeStats) = mergeBillRecords(existing, sorted)
    val enriched = attachVoteRefs(merged, votesStore?.loadVoteRefs(congress).orEmpty())
    val finalManifest = manifestStore.save(congress, enriched, nowIso)

    return FetchBillsResult(
        congress = congress,
        cutoff = cutoff,
        evaluated = totalEvaluated,
        keptRecords = sorted,
        rejectionCounts = rejectCounts.toMap(),
        rejectionSamples = rejectionSamples.toList(),
        mergeStats = mergeStats,
        finalManifest = finalManifest,
        newBillsDeferred = newBillsDeferred,
    )
}

/**
 * The [Bill.id] a list-endpoint summary will enrich into —
 * `"${type}${number}-${congress}"` with a lowercased type, matching
 * [buildBillRecord]. Lets a capped run (backlog #43) recognise a summary
 * already in the manifest without paying its three-GET enrichment cost.
 */
private fun billIdForSummary(summary: JsonObject, congress: Int): String {
    val type = (summary.stringField("type") ?: "").lowercase()
    val number = summary.stringField("number") ?: ""
    return "$type$number-$congress"
}

// `MutableMap.merge` is JVM-only — Kotlin Native (iOS targets) lacks it.
// Hand-rolled increment keeps commonMain portable.
private fun MutableMap<String, Int>.bump(key: String, by: Int = 1) {
    this[key] = (this[key] ?: 0) + by
}
