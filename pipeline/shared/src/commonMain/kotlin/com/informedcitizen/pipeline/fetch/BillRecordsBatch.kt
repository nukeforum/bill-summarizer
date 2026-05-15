package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.ErrorCollector
import com.informedcitizen.pipeline.http.CongressClient
import com.informedcitizen.pipeline.model.Bill
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonObject

/** Concurrent enrichment workers. Mirrors Python `BUILD_RECORD_WORKERS = 4`. */
const val ENRICH_WORKERS: Int = 4

/**
 * Build [Bill] records for each `(summary, outcome)` in [items],
 * fanned out across coroutines with at most [maxWorkers] in flight.
 * Each call to [buildBillRecord] issues three sequential Congress.gov
 * GETs (detail / summaries / text); independent across bills, so
 * we let the API's per-key rate limit bind rather than wall-clock
 * sequence.
 *
 * Build failures are recorded into [errors] keyed by
 * `"build_bill_record"` and excluded from the returned list. Result
 * order is NOT stable (futures complete in arbitrary order); callers
 * that need a sort must sort the returned list.
 *
 * Returns `(records, failureCount)`. Mirrors Python
 * `_common.build_bill_records_parallel`.
 */
suspend fun buildBillRecordsParallel(
    client: CongressClient,
    congress: Int,
    items: List<Pair<JsonObject, String>>,
    errors: ErrorCollector,
    maxWorkers: Int = ENRICH_WORKERS,
): Pair<List<Bill>, Int> {
    if (items.isEmpty()) return emptyList<Bill>() to 0
    val semaphore = Semaphore(maxWorkers)
    val results: List<Result<Bill>> = coroutineScope {
        items.map { (summary, outcome) ->
            async {
                semaphore.withPermit {
                    runCatching { buildBillRecord(client, congress, summary, outcome) }
                        .onFailure { exc ->
                            val ref = "${summary.stringField("type")}${summary.stringField("number")}"
                            errors.record(
                                kind = "build_bill_record",
                                identifier = ref,
                                errorClass = exc::class.simpleName ?: "Throwable",
                                message = exc.message ?: "",
                            )
                        }
                }
            }
        }.awaitAll()
    }
    val records = results.mapNotNull { it.getOrNull() }
    val failures = results.count { it.isFailure }
    return records to failures
}
