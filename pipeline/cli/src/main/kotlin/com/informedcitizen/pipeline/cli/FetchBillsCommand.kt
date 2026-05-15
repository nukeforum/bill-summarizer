package com.informedcitizen.pipeline.cli

import com.informedcitizen.pipeline.ErrorCollector
import com.informedcitizen.pipeline.congressForYear
import com.informedcitizen.pipeline.fetch.FileBillsManifestStore
import com.informedcitizen.pipeline.fetch.RECENT_DAYS
import com.informedcitizen.pipeline.fetch.fetchBills
import com.informedcitizen.pipeline.fetch.nowIso
import com.informedcitizen.pipeline.http.CongressClient
import com.informedcitizen.pipeline.http.createPipelineHttpClient
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
 * Output directory defaults to `./docs/data` (matches Python's
 * `OUTPUT_DIR`). Override via `--output-dir <path>`.
 */
object FetchBillsCommand {
    fun run(args: List<String>): Int {
        val outputDir = parseOutputDir(args) ?: "docs/data"
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
            return 0
        } finally {
            httpClient.close()
        }
    }

    private fun parseOutputDir(args: List<String>): String? {
        val idx = args.indexOf("--output-dir")
        return if (idx >= 0 && idx + 1 < args.size) args[idx + 1] else null
    }
}
