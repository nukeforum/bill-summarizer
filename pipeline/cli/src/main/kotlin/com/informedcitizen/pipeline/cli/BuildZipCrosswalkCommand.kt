package com.informedcitizen.pipeline.cli

import com.informedcitizen.pipeline.ErrorCollector
import com.informedcitizen.pipeline.fetch.FileZipCrosswalkStore
import com.informedcitizen.pipeline.fetch.HUD_STATE_QUERIES
import com.informedcitizen.pipeline.fetch.buildZipCrosswalkFromApi
import com.informedcitizen.pipeline.http.HudClient
import com.informedcitizen.pipeline.http.createPipelineHttpClient
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath

/**
 * `build-zip-crosswalk` subcommand. Direct port of Python
 * `build_zip_crosswalk.py`'s API mode (the CSV manual-download mode is
 * not ported — CI never used it). Same exit codes:
 *  - `0` success
 *  - `1` unrecoverable error (zero ZIPs collected)
 *  - `2` HUDUSER_API_KEY unset
 *
 * Flags:
 *  - `--output <path>` — asset file path. Default
 *    `android/app/src/main/assets/zip_to_cd.json` (the production
 *    bundled asset, matching the canonical workflow invocation).
 *  - `--year <int>` / `--quarter <1-4>` — HUD data period (omit both
 *    for the latest published quarter).
 *  - `--sleep <seconds>` — pause between per-state requests (HUD rate
 *    limits are tight; the workflow passes 0.1).
 */
object BuildZipCrosswalkCommand {
    fun run(args: List<String>): Int {
        val output = parseFlag(args, "--output")
            ?: "android/app/src/main/assets/zip_to_cd.json"
        val year = parseFlag(args, "--year")?.toIntOrNull()
        val quarter = parseFlag(args, "--quarter")?.toIntOrNull()
        val sleepSeconds = parseFlag(args, "--sleep")?.toDoubleOrNull() ?: 0.0

        val apiKey = System.getenv("HUDUSER_API_KEY")
        if (apiKey.isNullOrEmpty()) {
            System.err.println("HUDUSER_API_KEY not set in environment")
            return 2
        }

        val period = if (year != null && quarter != null) "year=$year q$quarter"
        else "latest published quarter"
        println("Fetching ZIP-CD crosswalk per state ($period)")

        val httpClient = createPipelineHttpClient()
        try {
            val hud = HudClient(httpClient, apiKey)
            val errors = ErrorCollector()
            val result = runBlocking {
                buildZipCrosswalkFromApi(
                    hud = hud,
                    year = year,
                    quarter = quarter,
                    sleepMillis = (sleepSeconds * 1000).toLong(),
                    errors = errors,
                    onStateDone = { state, added, skipped ->
                        val suffix = if (skipped > 0) " ($skipped skipped)" else ""
                        println("  + $state: $added ZIP×CD pairs$suffix")
                    },
                )
            }
            println(
                "\nFetched ${result.statesFetched}/${HUD_STATE_QUERIES.size} states; " +
                    "unique ZIPs: ${result.byZip.size}; misses: ${result.misses}"
            )
            if (errors.hasErrors) {
                System.err.println(errors.renderSummary(label = "build_zip_crosswalk"))
            }
            val path = FileZipCrosswalkStore.system(output.toPath()).save(result.byZip)
            println("wrote $path")
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
