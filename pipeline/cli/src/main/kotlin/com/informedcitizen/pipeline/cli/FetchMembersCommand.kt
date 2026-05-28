package com.informedcitizen.pipeline.cli

import com.informedcitizen.pipeline.ErrorCollector
import com.informedcitizen.pipeline.congressForYear
import com.informedcitizen.pipeline.fetch.FetchMembersProgress
import com.informedcitizen.pipeline.fetch.FileMemberLegislationStore
import com.informedcitizen.pipeline.fetch.FileMembersIndexStore
import com.informedcitizen.pipeline.fetch.fetchMembers
import com.informedcitizen.pipeline.fetch.nowIso
import com.informedcitizen.pipeline.http.CongressClient
import com.informedcitizen.pipeline.http.LegislatorsClient
import com.informedcitizen.pipeline.http.createPipelineHttpClient
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.Path.Companion.toPath

/**
 * `fetch-members` subcommand. Direct port of Python `fetch_members.py`'s
 * `main`. Same exit codes:
 *  - `0` success (including time-budget early exit during Phase 2)
 *  - `2` CONGRESS_API_KEY unset, or both `--phase1-only` and
 *    `--phase2-only` passed
 *
 * Flags:
 *  - `--output-dir <path>` — published-data directory.
 *    Default `./docs/data` (matches Python's `OUTPUT_DIR`).
 *  - `--time-budget-minutes <int>` — soft cap on Phase 2 (default 300 =
 *    5 hours). Phase 1 always runs to completion. GitHub Actions has a
 *    6h hard cap; the default leaves 1h headroom.
 *  - `--phase1-only` — publish the members index and exit. Use when the
 *    workflow commits Phase 1 output before a second invocation runs
 *    Phase 2 (keeps the index on origin within ~10 minutes regardless
 *    of Phase 2 duration).
 *  - `--phase2-only` — backfill sponsored / cosponsored legislation
 *    using the existing on-disk index. Errors if no index exists.
 */
object FetchMembersCommand {
    fun run(args: List<String>): Int {
        val outputDir = parseArg(args, "--output-dir") ?: "docs/data"
        val timeBudgetMinutes = parseArg(args, "--time-budget-minutes")?.toIntOrNull() ?: 300
        val phase1Only = "--phase1-only" in args
        val phase2Only = "--phase2-only" in args
        if (phase1Only && phase2Only) {
            System.err.println("--phase1-only and --phase2-only are mutually exclusive.")
            return 2
        }

        val apiKey = System.getenv("CONGRESS_API_KEY")
        if (apiKey.isNullOrEmpty()) {
            System.err.println("CONGRESS_API_KEY is not set in the environment.")
            return 2
        }

        val now = Clock.System.now()
        val congress = congressForYear(now.toLocalDateTime(TimeZone.UTC).year)
        val runPhase1 = !phase2Only
        val runPhase2 = !phase1Only

        val httpClient = createPipelineHttpClient()
        try {
            val congressClient = CongressClient(httpClient, apiKey)
            val legislatorsClient = LegislatorsClient(httpClient)
            val errors = ErrorCollector()
            val indexStore = FileMembersIndexStore.system(outputDir.toPath())
            val legislationStore = FileMemberLegislationStore.system(outputDir.toPath())

            val progress = FetchMembersProgress(
                onContactInfoLoaded = { size, withForm, withSite ->
                    println(
                        "Fetched contact info: $size legislators, " +
                            "$withForm contact forms, $withSite websites"
                    )
                },
                onContactInfoFailed = { msg ->
                    System.err.println(
                        "WARN: contact-info fetch failed ($msg); contact_form and website will be null."
                    )
                },
                onSocialsLoaded = { size, totalHandles ->
                    println("Fetched socials: $size legislators, $totalHandles handles")
                },
                onSocialsFailed = { msg ->
                    System.err.println("WARN: socials fetch failed ($msg); socials will be [].")
                },
                onPhase1Start = { c ->
                    println("Phase 1: fetching member index for the ${c}th Congress")
                },
                onPhase1Member = { m ->
                    val districtPart = if (m.chamber == "house" && m.district != null) "-${m.district}" else ""
                    println("  + ${m.bioguideId} ${m.name} (${m.party}-${m.state}$districtPart)")
                },
                onPhase1Done = { count -> println("Phase 1 done: index has $count members") },
                onPhase2Start = { println("\nPhase 2: backfilling sponsored/cosponsored legislation") },
                onPhase2MemberDone = { bid -> println("  + $bid: legislation backfilled") },
                onPhase2TimeBudgetExceeded = { processed, skipped ->
                    System.err.println(
                        "\n[time-budget] Stopping Phase 2 early; processed $processed new + " +
                            "skipped $skipped cached. Re-run to continue."
                    )
                },
                onPhase2Done = { backfilled, skipped, total ->
                    println(
                        "\nPhase 2 done: backfilled $backfilled members, " +
                            "skipped $skipped already-cached. Index has $total total."
                    )
                },
                onStateWarning = { msg -> System.err.println("  ! $msg") },
            )

            val result = runBlocking {
                fetchMembers(
                    congressClient = congressClient,
                    legislatorsClient = legislatorsClient,
                    congress = congress,
                    nowIso = nowIso(now),
                    indexStore = indexStore,
                    legislationStore = legislationStore,
                    errors = errors,
                    runPhase1 = runPhase1,
                    runPhase2 = runPhase2,
                    timeBudgetMillis = timeBudgetMinutes * 60L * 1000L,
                    progress = progress,
                )
            }

            if (errors.hasErrors) {
                val label = when {
                    result.ranPhase1 && result.ranPhase2 -> "fetch_members"
                    result.ranPhase1 -> "fetch_members Phase 1"
                    result.ranPhase2 -> "fetch_members Phase 2"
                    else -> "fetch_members"
                }
                System.err.println(errors.renderSummary(label = label))
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
