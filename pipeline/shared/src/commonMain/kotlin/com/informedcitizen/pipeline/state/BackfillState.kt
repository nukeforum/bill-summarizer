package com.informedcitizen.pipeline.state

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Live cursor for the historical-bills backfill. Persisted as JSON in
 * the same `backfill_state.json` file the Python pipeline writes
 * (`data-pipeline/state/backfill_state.json` in CI) so the JVM CLI
 * inherits in-flight progress when the workflow cuts over from Python
 * — no migration step needed.
 *
 * Field order matches the Python `_common.initial_state` dict literal
 * so kotlinx-serialization's `prettyPrint` output is byte-identical
 * to Python's `json.dump(indent=2)`. Same `@SerialName`s as the wire
 * format. Default values match the seeded state.
 */
@Serializable
data class BackfillState(
    @SerialName("active_congress") val activeCongress: Int? = null,
    @SerialName("active_offset") val activeOffset: Int = 0,
    val queue: List<Int> = emptyList(),
    val completed: List<Int> = emptyList(),
    @SerialName("last_run_at") val lastRunAt: String? = null,
)

/** Page size of the Congress.gov bill-list endpoint (Python `LIST_PAGE_LIMIT`). */
const val LIST_PAGE_LIMIT: Int = 250

/** Earliest Congress reachable via Congress.gov v3 (Python `OLDEST_API_CONGRESS`). */
const val OLDEST_API_CONGRESS: Int = 93

/** Pages consumed per backfill workflow run (Python `BACKFILL_PAGES_PER_RUN`). */
const val BACKFILL_PAGES_PER_RUN: Int = 4

/**
 * Seed state for a fresh backfill run. Queue counts down from the
 * supplied [currentCongress] to [OLDEST_API_CONGRESS] inclusive, so
 * the active Congress is whichever the user is currently in and the
 * crawler walks backward from there. Mirrors Python `initial_state`.
 */
fun initialBackfillState(currentCongress: Int): BackfillState {
    val queue = (currentCongress downTo OLDEST_API_CONGRESS).toList()
    return BackfillState(
        activeCongress = queue.firstOrNull(),
        activeOffset = 0,
        queue = queue,
        completed = emptyList(),
        lastRunAt = null,
    )
}
