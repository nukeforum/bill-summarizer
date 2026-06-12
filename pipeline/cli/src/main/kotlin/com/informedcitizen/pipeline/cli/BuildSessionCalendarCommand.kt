package com.informedcitizen.pipeline.cli

import com.informedcitizen.pipeline.fetch.FileSessionCalendarStore
import com.informedcitizen.pipeline.fetch.buildSessionCalendar
import com.informedcitizen.pipeline.fetch.nowIso
import com.informedcitizen.pipeline.http.SessionCalendarClient
import com.informedcitizen.pipeline.http.createPipelineHttpClient
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.Path.Companion.toPath

/**
 * `build-session-calendar` subcommand. Direct port of Python
 * `build_session_calendar.py`'s `main`. Same exit codes:
 *  - `0` success
 *  - `1` unrecoverable error (HTTP failure, parse failure, empty
 *    calendar)
 *
 * No API key required — both feeds are public `.gov` publications.
 *
 * Flags:
 *  - `--output-dir <path>` — published-data directory. Default
 *    `./docs/data` (matches Python's `OUTPUT`).
 */
object BuildSessionCalendarCommand {
    fun run(args: List<String>): Int {
        val outputDir = parseFlag(args, "--output-dir") ?: "docs/data"

        val now = Clock.System.now()
        val today = now.toLocalDateTime(TimeZone.UTC).date
        val store = FileSessionCalendarStore.system(outputDir.toPath())

        val httpClient = createPipelineHttpClient()
        try {
            val client = SessionCalendarClient(httpClient)
            val result = runBlocking {
                buildSessionCalendar(
                    client = client,
                    today = today,
                    nowIso = nowIso(now),
                    onHouseParsed = { count ->
                        System.err.println("House: parsed $count voting days")
                    },
                    onSenateParsed = { count, years ->
                        System.err.println("Senate: parsed $count session days from years $years")
                    },
                )
            }
            val path = store.save(result.calendar)
            println("OK: wrote $path (House: ${result.houseDayCount}, Senate: ${result.senateDayCount})")
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
