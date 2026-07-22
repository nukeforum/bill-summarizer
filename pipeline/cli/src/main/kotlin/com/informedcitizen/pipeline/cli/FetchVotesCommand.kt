package com.informedcitizen.pipeline.cli

import com.informedcitizen.pipeline.ErrorCollector
import com.informedcitizen.pipeline.congressForYear
import com.informedcitizen.pipeline.fetch.FETCH_VOTES_MAX_NEW_DEFAULT
import com.informedcitizen.pipeline.fetch.FetchVotesProgress
import com.informedcitizen.pipeline.fetch.FileBillsManifestStore
import com.informedcitizen.pipeline.fetch.FileVotesStore
import com.informedcitizen.pipeline.fetch.fetchVotes
import com.informedcitizen.pipeline.fetch.manifestFileName
import com.informedcitizen.pipeline.fetch.nowIso
import com.informedcitizen.pipeline.http.SenateVotesClient
import com.informedcitizen.pipeline.http.createPipelineHttpClient
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.Path.Companion.toPath

/**
 * `fetch-votes` subcommand. Direct port of Python `fetch_votes.py`'s
 * `main`. Same exit codes:
 *  - `0` success (per-vote failures are recorded and retried next run)
 *  - `1` unrecoverable error (no vote menu, menu identity mismatch,
 *    non-404 menu/YAML fetch failure)
 *
 * No API key required — senate.gov LIS XML, clerk.house.gov EVS XML,
 * and the congress-legislators YAMLs are all public.
 *
 * Flags:
 *  - `--output-dir <path>` — published-data directory. Default
 *    `./docs/data` (matches Python's `OUTPUT_DIR`).
 *  - `--congress <N>` — Congress number. Default: the current Congress.
 *  - `--max-new <N>` — cap on new votes fetched per run (default 1000).
 */
object FetchVotesCommand {
    fun run(args: List<String>): Int {
        val outputDir = parseFlag(args, "--output-dir") ?: "docs/data"
        val congressFlag = parseFlag(args, "--congress")?.toIntOrNull()
        val maxNew = parseFlag(args, "--max-new")?.toIntOrNull() ?: FETCH_VOTES_MAX_NEW_DEFAULT

        val now = Clock.System.now()
        val congress = congressFlag ?: congressForYear(now.toLocalDateTime(TimeZone.UTC).year)
        val store = FileVotesStore.system(outputDir.toPath())
        val manifestStore = FileBillsManifestStore.system(outputDir.toPath())

        val httpClient = createPipelineHttpClient()
        try {
            val client = SenateVotesClient(httpClient)
            val errors = ErrorCollector()
            val result = runBlocking {
                fetchVotes(
                    client = client,
                    store = store,
                    congress = congress,
                    nowIso = nowIso(now),
                    errors = errors,
                    manifestStore = manifestStore,
                    maxNew = maxNew,
                    progress = FetchVotesProgress(
                        onMenus = { totalListed, sessions ->
                            println(
                                "Senate menu for Congress $congress: $totalListed " +
                                    "roll calls across session(s) $sessions",
                            )
                        },
                        onVoteSaved = { vote ->
                            println("  + ${vote.id}: ${vote.question} — ${vote.result}")
                        },
                        onMaxNewReached = { cap ->
                            println("Reached --max-new $cap; remaining votes deferred to next run")
                        },
                        onHouseProbeStart = {
                            println("House probe for Congress $congress (clerk.house.gov EVS):")
                        },
                        onHouseDeferred = {
                            println("House probe deferred to next run: --max-new budget exhausted")
                        },
                        onUnsupportedVote = { voteKey, message ->
                            println("  - house-$voteKey skipped: $message")
                        },
                        onConsecutiveFailures = { count, session ->
                            println(
                                "  ! $count consecutive House parse failures; " +
                                    "stopping session $session probe",
                            )
                        },
                    ),
                )
            }
            if (result.billManifestRefreshed) {
                println("bill manifest: ${manifestFileName(congress)} vote refs refreshed")
            }
            println(
                "member shards: ${result.memberShardsWritten} written, " +
                    "${result.memberShardsUnchanged} unchanged",
            )
            val summary = errors.renderSummary(label = "fetch-votes")
            if (summary.isNotEmpty()) System.err.println(summary)
            println(
                "OK: ${result.fetched} new " +
                    "(${result.senateFetched} Senate, ${result.houseFetched} House), " +
                    "${result.skipped} already on disk, " +
                    "${result.unsupported} unsupported, ${errors.size} failed; " +
                    "index now ${result.index.voteCount} votes",
            )
            return 0
        } catch (e: Exception) {
            System.err.println("ERROR: ${e.message}")
            return 1
        } finally {
            httpClient.close()
        }
    }

    private fun parseFlag(args: List<String>, name: String): String? {
        val idx = args.indexOf(name)
        return if (idx >= 0 && idx + 1 < args.size) args[idx + 1] else null
    }
}
