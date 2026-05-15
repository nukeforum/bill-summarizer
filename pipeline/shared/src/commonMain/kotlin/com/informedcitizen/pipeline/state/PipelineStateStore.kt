package com.informedcitizen.pipeline.state

/**
 * Persistence surface for the pipeline's run-state cursors. The shared
 * concern across the JVM CLI (running in GitHub Actions), Android (when
 * BYOK is active), and the future iOS app — each platform supplies a
 * path; the store handles the serialization. See [FilePipelineStateStore]
 * for the production impl.
 *
 * Currently scoped to backfill state; future state surfaces
 * (per-feed last-fetched timestamps, in-flight queues for Phase 2
 * members fetch, rate-limit budgets) will add their own methods here.
 */
interface PipelineStateStore {
    /**
     * Read the persisted [BackfillState]. If the file is missing or
     * unparseable, returns the result of [initial] — matches Python's
     * "fall back to a freshly seeded state" semantics so a corrupted
     * state file never wedges the pipeline.
     */
    fun loadBackfillState(initial: () -> BackfillState): BackfillState

    /** Persist [state]. Creates parent directories if missing. */
    fun saveBackfillState(state: BackfillState)
}
