package com.informedcitizen.pipeline.cli

import com.informedcitizen.pipeline.ErrorCollector
import com.informedcitizen.pipeline.congressForYear
import com.informedcitizen.pipeline.fetch.FileBillsManifestStore
import com.informedcitizen.pipeline.fetch.FileCongressesIndexStore
import com.informedcitizen.pipeline.fetch.RECENT_DAYS
import com.informedcitizen.pipeline.fetch.fetchBills
import com.informedcitizen.pipeline.fetch.nowIso
import com.informedcitizen.pipeline.http.CongressClient
import com.informedcitizen.pipeline.http.createPipelineHttpClient
import com.informedcitizen.pipeline.state.FilePipelineStateStore
import com.informedcitizen.pipeline.state.initialBackfillState
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import okio.Path.Companion.toPath

/**
 * `fetch-bills` subcommand. Direct port of Python `fetch_bills.py`'s
 * `main`. Same exit codes:
 *  - `0` success
 *  - `2` CONGRESS_API_KEY unset
 *
 * Flags:
 *  - `--output-dir <path>` — published-data directory.
 *    Default `./docs/data` (matches Python's `OUTPUT_DIR`).
 *  - `--state-dir <path>` — pipeline state directory; the `completed`
 *    list from `backfill_state.json` populates each index entry's
 *    `backfill_complete` flag. Default `./data-pipeline/state` so the
 *    JVM CLI reads the same cursor Python writes — no migration
 *    needed when canonical ownership flips. Missing/absent state file
 *    is fine; treated as empty completed set.
 */
object FetchBillsCommand {
    fun run(args: List<String>): Int {
        val outputDir = parseFlag(args, "--output-dir") ?: "docs/data"
        val stateDir = parseFlag(args, "--state-dir") ?: "data-pipeline/state"
        val apiKey = System.getenv("CONGRESS_API_KEY")
        if (apiKey.isNullOrEmpty()) {
            System.err.println("CONGRESS_API_KEY is not set in the environment.")
            return 2
        }

        val now = Clock.System.now()
        val cutoff = now.minus(RECENT_DAYS, DateTimeUnit.DAY, TimeZone.UTC)
        val congress = congressForYear(now.toLocalDateTime(TimeZone.UTC).year)
        println("Fetching bills for the ${congress}th Congress " +
            "(cutoff ${cutoff.toLocalDateTime(TimeZone.UTC).date})")

        val httpClient = createPipelineHttpClient()
        try {
            val congressClient = CongressClient(httpClient, apiKey)
            val errors = ErrorCollector()
            val store = FileBillsManifestStore.system(outputDir.toPath())

            val result = runBlocking {
                fetchBills(
                    client = congressClient,
                    congress = congress,
                    cutoff = cutoff,
                    nowIso = nowIso(now),
                    manifestStore = store,
                    errors = errors,
                )
            }

            for (record in result.keptRecords) {
                println("  + ${record.id}: ${record.outcome.name.lowercase()} on ${record.latestAction.date}")
            }
            println()
            println("Evaluated ${result.evaluated} bills, kept ${result.keptRecords.size}.")
            if (result.rejectionCounts.isNotEmpty()) {
                println("Rejections:")
                result.rejectionCounts.entries
                    .sortedByDescending { it.value }
                    .forEach { (reason, count) -> println("  - $reason: $count") }
            }
            if (result.rejectionSamples.isNotEmpty()) {
                println("Sample latestAction texts that did not match any outcome rule:")
                for (sample in result.rejectionSamples) {
                    println("  · $sample")
                }
            }
            if (errors.hasErrors) {
                System.err.println(errors.renderSummary(label = "fetch_bills"))
            }
            println(
                "merge: +${result.mergeStats.added} added, " +
                    "~${result.mergeStats.updated} updated, " +
                    "=${result.mergeStats.unchanged} unchanged " +
                    "(manifest now ${result.finalManifest.bills.size} bills)"
            )

            // Rewrite docs/data/congresses.json. Mirrors Python's
            // `rebuild_index()` call at the tail of `fetch_bills.main`.
            // Reads the `completed` list from the same state file the
            // Python backfill workflow maintains.
            val stateStore = FilePipelineStateStore.system(stateDir.toPath())
            val state = stateStore.loadBackfillState { initialBackfillState(congress) }
            val indexStore = FileCongressesIndexStore.system(outputDir.toPath())
            val index = indexStore.rebuild(
                currentCongress = congress,
                completed = state.completed.toSet(),
                nowIso = nowIso(now),
            )
            println("Wrote ${indexStore.pathFor()}: ${index.congresses.size} congress entries.")
            return 0
        } finally {
            httpClient.close()
        }
    }

    private fun parseFlag(args: List<String>, name: String): String? {
        val idx = args.indexOf(name)
        return if (idx >= 0 && idx + 1 < args.size) args[idx + 1] else null
    }
}
