package com.informedcitizen.pipeline.cli

import com.informedcitizen.pipeline.ErrorCollector
import com.informedcitizen.pipeline.congressForYear
import com.informedcitizen.pipeline.fetch.FileBillsManifestStore
import com.informedcitizen.pipeline.fetch.FileCongressesIndexStore
import com.informedcitizen.pipeline.fetch.backfillBills
import com.informedcitizen.pipeline.fetch.nowIso
import com.informedcitizen.pipeline.http.CongressClient
import com.informedcitizen.pipeline.http.createPipelineHttpClient
import com.informedcitizen.pipeline.state.FilePipelineStateStore
import com.informedcitizen.pipeline.state.initialBackfillState
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.Path.Companion.toPath

/**
 * `backfill-bills` subcommand. Direct port of Python
 * `backfill_bills.py`'s `main`. Same exit codes:
 *  - `0` success (including queue-empty no-op)
 *  - `2` CONGRESS_API_KEY unset
 *
 * Output directory defaults to `./docs/data`; state directory defaults
 * to `./data-pipeline/state` so the JVM CLI reads/writes the same
 * `backfill_state.json` the Python pipeline did (zero migration at
 * cut-over). Override with `--output-dir <path>` / `--state-dir <path>`.
 */
object BackfillBillsCommand {
    fun run(args: List<String>): Int {
        val outputDir = parseArg(args, "--output-dir") ?: "docs/data"
        val stateDir = parseArg(args, "--state-dir") ?: "data-pipeline/state"

        val apiKey = System.getenv("CONGRESS_API_KEY")
        if (apiKey.isNullOrEmpty()) {
            System.err.println("CONGRESS_API_KEY is not set in the environment.")
            return 2
        }

        val now = Clock.System.now()
        val stateStore = FilePipelineStateStore.system(stateDir.toPath())
        val currentCongress = congressForYear(now.toLocalDateTime(TimeZone.UTC).year)
        val state = stateStore.loadBackfillState { initialBackfillState(currentCongress) }

        if (state.activeCongress == null) {
            println("Backfill complete: queue is empty.")
            val indexStore = FileCongressesIndexStore.system(outputDir.toPath())
            val index = indexStore.rebuild(
                currentCongress = currentCongress,
                completed = state.completed.toSet(),
                nowIso = nowIso(now),
            )
            println("Wrote ${indexStore.pathFor()}: ${index.congresses.size} congress entries.")
            return 0
        }
        println(
            "Backfilling Congress ${state.activeCongress} starting at offset " +
                "${state.activeOffset}"
        )

        val httpClient = createPipelineHttpClient()
        try {
            val congressClient = CongressClient(httpClient, apiKey)
            val errors = ErrorCollector()
            val manifestStore = FileBillsManifestStore.system(outputDir.toPath())

            val result = runBlocking {
                backfillBills(
                    client = congressClient,
                    state = state,
                    nowIso = nowIso(now),
                    manifestStore = manifestStore,
                    errors = errors,
                )
            }

            println(
                "Evaluated ${result.evaluated} bills across ${result.pagesConsumed} page(s); " +
                    "kept ${result.keptRecords.size}."
            )
            if (result.rejectionCounts.isNotEmpty()) {
                println("Rejections:")
                result.rejectionCounts.entries
                    .sortedByDescending { it.value }
                    .forEach { (reason, count) -> println("  - $reason: $count") }
            }
            if (errors.hasErrors) {
                System.err.println(
                    errors.renderSummary(label = "backfill_bills congress=${result.activeCongress}")
                )
            }

            // Persist the advanced state. Python's `save_state` is unconditional
            // when work was done; mirror that. The queue-empty no-op case
            // already returned above.
            stateStore.saveBackfillState(result.newState)

            // Mirror Python's `rebuild_index()` at the tail of `backfill_bills.main`.
            // The completed set may have advanced if this run finished a Congress.
            val indexStore = FileCongressesIndexStore.system(outputDir.toPath())
            val index = indexStore.rebuild(
                currentCongress = currentCongress,
                completed = result.newState.completed.toSet(),
                nowIso = nowIso(now),
            )
            println("Wrote ${indexStore.pathFor()}: ${index.congresses.size} congress entries.")

            result.mergeStats?.let { ms ->
                println(
                    "merge: +${ms.added} added, ~${ms.updated} updated, " +
                        "=${ms.unchanged} unchanged (manifest now ${result.finalManifest!!.bills.size} bills)"
                )
            }

            when {
                result.congressCompleted -> println(
                    "Congress ${result.activeCongress} complete; next: ${result.newState.activeCongress}"
                )
                result.cursorHeld -> println(
                    "Empty page at offset ${result.priorOffset} with no prior progress; " +
                        "treating as transient and holding cursor on Congress ${result.activeCongress}."
                )
                else -> println(
                    "Next run resumes at Congress ${result.activeCongress} " +
                        "offset ${result.newState.activeOffset}"
                )
            }
            return 0
        } finally {
            httpClient.close()
        }
    }

    private fun parseArg(args: List<String>, flag: String): String? {
        val idx = args.indexOf(flag)
        return if (idx >= 0 && idx + 1 < args.size) args[idx + 1] else null
    }
}
