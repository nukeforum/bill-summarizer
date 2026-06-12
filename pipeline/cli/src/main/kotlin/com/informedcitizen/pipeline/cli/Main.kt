package com.informedcitizen.pipeline.cli

/**
 * Pipeline CLI entrypoint. Subcommands ported so far:
 *  - `fetch-bills` — daily refresh of the per-Congress bills manifest.
 *  - `backfill-bills` — incremental historical backfill walking the
 *    queue in `backfill_state.json`.
 *  - `fetch-members` — current Congress members index + sponsored /
 *    cosponsored legislation backfill.
 *  - `build-session-calendar` — House ICS + Senate XML session days.
 *  - `build-zip-crosswalk` — HUD ZIP→congressional-district asset.
 *  - `check-freshness` — staleness assertions over published outputs.
 *
 * All Python pipeline scripts now have Kotlin counterparts except the
 * operator-facing `build_dashboard.py` (deliberately unported — see
 * TODO "Shared Pipeline (KMP)").
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printUsage()
        return
    }
    val exitCode = when (val cmd = args[0]) {
        "fetch-bills" -> FetchBillsCommand.run(args.drop(1))
        "backfill-bills" -> BackfillBillsCommand.run(args.drop(1))
        "fetch-members" -> FetchMembersCommand.run(args.drop(1))
        "build-session-calendar" -> BuildSessionCalendarCommand.run(args.drop(1))
        "build-zip-crosswalk" -> BuildZipCrosswalkCommand.run(args.drop(1))
        "check-freshness" -> CheckFreshnessCommand.run(args.drop(1))
        "--help", "-h", "help" -> {
            printUsage()
            0
        }
        else -> {
            System.err.println("Unknown subcommand: $cmd")
            printUsage()
            2
        }
    }
    if (exitCode != 0) {
        kotlin.system.exitProcess(exitCode)
    }
}

private fun printUsage() {
    println(
        """
        pipeline-cli — bill-summarizer data pipeline (Kotlin port)

        Usage:
          pipeline-cli <subcommand> [options]

        Subcommands:
          fetch-bills [--output-dir <path>] [--state-dir <path>]
              Daily refresh of the per-Congress bills manifest. Reads
              CONGRESS_API_KEY from the environment. Writes
              <output-dir>/congressNNN_bills.json (default: ./docs/data)
              and rebuilds <output-dir>/congresses.json, reading the
              completed list from <state-dir>/backfill_state.json
              (default: ./data-pipeline/state).
          backfill-bills [--output-dir <path>] [--state-dir <path>]
              Incremental historical backfill. Walks one chunk of
              BACKFILL_PAGES_PER_RUN (4) pages for the active Congress
              in <state-dir>/backfill_state.json, advances the cursor,
              and persists. Reads CONGRESS_API_KEY from the environment.
              Defaults match the Python pipeline: ./docs/data for
              output, ./data-pipeline/state for state.
          fetch-members [--output-dir <path>] [--time-budget-minutes N]
                        [--phase1-only | --phase2-only]
              Members of the current Congress + sponsored / cosponsored
              legislation backfill. Phase 1 writes
              <output-dir>/members_NNN.json (always runs to completion
              when enabled). Phase 2 writes per-member
              <output-dir>/members/{bid}_{kind}.json and is gated by
              --time-budget-minutes (default 300). Reads
              CONGRESS_API_KEY from the environment; pulls
              legislators-current.json from the unitedstates/
              congress-legislators gh-pages branch (no auth).
          build-session-calendar [--output-dir <path>]
              House voting days (USHOR .ics feed) + Senate session days
              (per-year senate.gov XML schedules, candidate years
              today−1..today+1). Writes
              <output-dir>/session_calendar.json (default: ./docs/data).
              No API key required.
          build-zip-crosswalk [--output <path>] [--year N] [--quarter 1-4]
                              [--sleep <seconds>]
              ZIP→{state, [districts]} asset from HUD's usps endpoint
              (type=5 zip-cd, per-state walk). Reads HUDUSER_API_KEY
              from the environment. Writes the compact JSON to
              <output> (default: android/app/src/main/assets/zip_to_cd.json).
          check-freshness [--output-dir <path>] [--state-dir <path>]
              Assert published artifacts are fresh: bills manifest age,
              members index age, session-calendar look-ahead per
              chamber, backfill cursor advancement. Exits 1 when
              anything is stale. No API key required.
          help
              Show this message.
        """.trimIndent(),
    )
}
