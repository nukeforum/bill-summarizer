package com.informedcitizen.pipeline.cli

import com.informedcitizen.pipeline.congressForYear
import com.informedcitizen.pipeline.fetch.FileBillShardStore
import com.informedcitizen.pipeline.fetch.FileCongressesIndexStore
import com.informedcitizen.pipeline.fetch.SHARD_PAGE_SIZE
import com.informedcitizen.pipeline.fetch.nowIso
import com.informedcitizen.pipeline.state.FilePipelineStateStore
import com.informedcitizen.pipeline.state.initialBackfillState
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * `build-bill-shards` subcommand. Parity shadow of Python
 * `build_bill_shards.py`'s `main`. Splits each on-disk
 * `congress<N>_bills.json` into recency-first pages
 * (`congress<N>_bills_p<NNN>.json`) plus an index
 * (`congress<N>_bills_index.json`), then rebuilds `congresses.json` so
 * each sharded Congress's `shard_index_path` discovery hook points at
 * its index. The single manifest is left untouched (dual-publish).
 *
 * Pure re-shape of already-fetched data — no API key, no network.
 * Exit codes: `0` success, `1` bad `--page-size`.
 *
 * Flags:
 *  - `--output-dir <path>` — published-data directory. Default
 *    `./docs/data` (matches Python's `OUTPUT_DIR`).
 *  - `--state-dir <path>` — pipeline state; the `completed` list from
 *    `backfill_state.json` populates each index entry's
 *    `backfill_complete` flag when congresses.json is rebuilt. Default
 *    `./data-pipeline/state`.
 *  - `--congress N` — shard only this Congress (default: all on disk).
 *  - `--page-size N` — max bills per shard (default: [SHARD_PAGE_SIZE]).
 */
object BuildBillShardsCommand {
    private val MANIFEST_RE = Regex("""^congress(\d+)_bills\.json$""")

    fun run(args: List<String>): Int {
        val outputDir = parseFlag(args, "--output-dir") ?: "docs/data"
        val stateDir = parseFlag(args, "--state-dir") ?: "data-pipeline/state"
        val onlyCongress = parseFlag(args, "--congress")?.toIntOrNull()
        val pageSize = parseFlag(args, "--page-size")?.toIntOrNull() ?: SHARD_PAGE_SIZE

        if (pageSize < 1) {
            System.err.println("ERROR: --page-size must be >= 1")
            return 1
        }

        val outputPath = outputDir.toPath()
        val fs = FileSystem.SYSTEM
        val congresses = if (onlyCongress != null) {
            listOf(onlyCongress)
        } else {
            congressesOnDisk(fs, outputPath)
        }
        if (congresses.isEmpty()) {
            System.err.println("no per-Congress manifests found; nothing to shard")
            return 0
        }

        val now = Clock.System.now()
        val shardStore = FileBillShardStore(fs, outputPath)
        for (congress in congresses) {
            val index = shardStore.save(congress, nowIso = nowIso(now), pageSize = pageSize)
            println(
                "OK: sharded congress $congress " +
                    "(${index.totalBills} bills -> ${index.shards.size} shard(s) of <= ${index.pageSize})",
            )
        }

        // Rebuild congresses.json so each sharded Congress's
        // shard_index_path discovery hook is populated (mirrors the
        // runner's rebuild_index() tail call).
        val current = congressForYear(now.toLocalDateTime(TimeZone.UTC).year)
        val stateStore = FilePipelineStateStore.system(stateDir.toPath())
        val state = stateStore.loadBackfillState { initialBackfillState(current) }
        val indexStore = FileCongressesIndexStore(fs, outputPath)
        indexStore.rebuild(
            currentCongress = current,
            completed = state.completed.toSet(),
            nowIso = nowIso(now),
        )
        println("OK: rebuilt congresses.json (shard_index_path populated for sharded congresses)")
        return 0
    }

    private fun congressesOnDisk(fs: FileSystem, outputDir: Path): List<Int> {
        if (!fs.exists(outputDir)) return emptyList()
        return fs.list(outputDir)
            .mapNotNull { MANIFEST_RE.matchEntire(it.name)?.groupValues?.get(1)?.toInt() }
            .sortedDescending()
    }

    private fun parseFlag(args: List<String>, name: String): String? {
        val idx = args.indexOf(name)
        return if (idx >= 0 && idx + 1 < args.size) args[idx + 1] else null
    }
}
