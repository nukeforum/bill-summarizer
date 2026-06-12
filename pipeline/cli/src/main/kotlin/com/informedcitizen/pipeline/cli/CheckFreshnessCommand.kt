package com.informedcitizen.pipeline.cli

import com.informedcitizen.pipeline.congressForYear
import com.informedcitizen.pipeline.fetch.checkFreshness
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * `check-freshness` subcommand. Direct port of Python
 * `check_freshness.py`'s `main`. Same exit codes:
 *  - `0` everything fresh
 *  - `1` one or more artifacts stale/missing (each axis printed to
 *    stderr)
 *
 * No API key required — purely local file inspection.
 *
 * Flags:
 *  - `--output-dir <path>` — published-data directory. Default
 *    `./docs/data`.
 *  - `--state-dir <path>` — pipeline state directory. Default
 *    `./data-pipeline/state`.
 */
object CheckFreshnessCommand {
    fun run(args: List<String>): Int {
        val outputDir = parseFlag(args, "--output-dir") ?: "docs/data"
        val stateDir = parseFlag(args, "--state-dir") ?: "data-pipeline/state"

        val now = Clock.System.now()
        val congress = congressForYear(now.toLocalDateTime(TimeZone.UTC).year)

        val failures = checkFreshness(
            fileSystem = FileSystem.SYSTEM,
            outputDir = outputDir.toPath(),
            stateDir = stateDir.toPath(),
            congress = congress,
            now = now,
        )

        if (failures.isNotEmpty()) {
            System.err.println("Pipeline freshness check FAILED:")
            for (line in failures) {
                System.err.println("  - $line")
            }
            return 1
        }
        println("Pipeline freshness check OK.")
        return 0
    }

    private fun parseFlag(args: List<String>, name: String): String? {
        val idx = args.indexOf(name)
        return if (idx >= 0 && idx + 1 < args.size) args[idx + 1] else null
    }
}
