package com.informedcitizen.pipeline.cli

import com.informedcitizen.pipeline.fetch.FileElectionCalendarStore
import com.informedcitizen.pipeline.fetch.buildElectionCalendar
import com.informedcitizen.pipeline.fetch.loadCuratedPrimaries
import com.informedcitizen.pipeline.fetch.nowIso
import com.informedcitizen.pipeline.model.ElectionType
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * `build-election-calendar` subcommand. Parity shadow of Python
 * `build_election_calendar.py`'s `main`. Same exit codes:
 *  - `0` success
 *  - `1` unrecoverable error (broken curated file, write failure)
 *
 * No API key or network required — the federal general date is statutory
 * and the primaries are read from an in-repo curated file.
 *
 * Flags:
 *  - `--output-dir <path>` — published-data directory. Default
 *    `./docs/data` (matches Python's `OUTPUT`).
 *  - `--primaries <path>` — curated primaries JSON. Default
 *    `./data-pipeline/data/election_primaries.json` (matches Python's
 *    `CURATED_PRIMARIES`). A missing file is tolerated (general row only).
 */
object BuildElectionCalendarCommand {
    fun run(args: List<String>): Int {
        val outputDir = parseFlag(args, "--output-dir") ?: "docs/data"
        val primariesPath = parseFlag(args, "--primaries")
            ?: "data-pipeline/data/election_primaries.json"

        val now = Clock.System.now()
        val today = now.toLocalDateTime(TimeZone.UTC).date

        try {
            val curated = loadCuratedPrimaries(
                fileSystem = FileSystem.SYSTEM,
                path = primariesPath.toPath(),
                onWarn = { System.err.println("  ! $it") },
            )
            val calendar = buildElectionCalendar(
                today = today,
                generatedAt = nowIso(now),
                curatedPrimaries = curated,
                onWarn = { System.err.println("  ! $it") },
            )
            val path = FileElectionCalendarStore.system(outputDir.toPath()).save(calendar)
            val primaries = calendar.elections.count { it.type != ElectionType.GENERAL }
            println("OK: wrote $path (${calendar.elections.size} events, $primaries curated primaries)")
            return 0
        } catch (e: Exception) {
            System.err.println("ERROR: ${e.message}")
            return 1
        }
    }

    private fun parseFlag(args: List<String>, name: String): String? {
        val idx = args.indexOf(name)
        return if (idx >= 0 && idx + 1 < args.size) args[idx + 1] else null
    }
}
