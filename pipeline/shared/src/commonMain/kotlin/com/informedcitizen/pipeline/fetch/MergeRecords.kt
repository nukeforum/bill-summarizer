package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.model.Bill

/** Per-batch outcome counts. Mirrors Python `_common.MergeStats`. */
data class MergeStats(
    val added: Int = 0,
    val updated: Int = 0,
    val unchanged: Int = 0,
)

/**
 * Merge [incoming] bills into [existing] keyed by `Bill.id`. Incoming
 * wins on id collision (a record is a snapshot of current truth, not
 * a history log). Bills present only in [existing] are preserved.
 * Output is sorted by `latestAction.date` descending so the manifest
 * stays newest-first regardless of which batch contributed each
 * record. Mirrors Python `_common.merge_records`.
 */
fun mergeBillRecords(
    existing: List<Bill>,
    incoming: List<Bill>,
): Pair<List<Bill>, MergeStats> {
    val existingById = existing.associateBy { it.id }
    val merged = existingById.toMutableMap()
    var added = 0
    var updated = 0
    var unchanged = 0
    for (rec in incoming) {
        val prior = existingById[rec.id]
        when {
            prior == null -> {
                merged[rec.id] = rec
                added++
            }
            prior != rec -> {
                merged[rec.id] = rec
                updated++
            }
            else -> unchanged++
        }
    }
    val sorted = merged.values.sortedByDescending { it.latestAction.date }
    return sorted to MergeStats(added, updated, unchanged)
}
