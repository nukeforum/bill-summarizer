package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.model.CongressEntry
import com.informedcitizen.pipeline.model.CongressesIndex
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

/**
 * Filename of the published index. Mirrors Python `OUTPUT_DIR / "congresses.json"`.
 */
internal const val CONGRESSES_INDEX_FILE: String = "congresses.json"

private val CONGRESS_FILE_REGEX = Regex("""^congress(\d+)_bills\.json$""")

/**
 * Hosts the cross-Congress index at `<outputDir>/congresses.json`.
 * The companion to [FileBillsManifestStore]: scans the same
 * `outputDir` for per-Congress manifests, derives entries, and writes
 * the index. Direct port of Python `_common.rebuild_index`.
 *
 * The `completed` set is supplied by the caller (typically from
 * [com.informedcitizen.pipeline.state.PipelineStateStore.loadBackfillState]
 * → `BackfillState.completed`). Decoupled so the index builder stays
 * pure-data and doesn't need a state store reference.
 */
class FileCongressesIndexStore(
    private val fileSystem: FileSystem,
    private val outputDir: Path,
) {
    fun pathFor(): Path = outputDir / CONGRESSES_INDEX_FILE

    /** Read the index; returns `null` if the file is absent or unparseable. */
    fun load(): CongressesIndex? {
        val path = pathFor()
        if (!fileSystem.exists(path)) return null
        return try {
            val text = fileSystem.source(path).buffer().use { it.readUtf8() }
            ManifestJson.decodeFromString(CongressesIndex.serializer(), text)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Walk [outputDir] for `congressNNN_bills.json` manifests, derive
     * per-Congress entries (bill count, first/last action date), mark
     * the current Congress and any in [completed], write the result
     * to `congresses.json` stamped with [nowIso]. Returns the
     * constructed index. Mirrors Python `rebuild_index`.
     */
    fun rebuild(
        currentCongress: Int,
        completed: Set<Int>,
        nowIso: String,
    ): CongressesIndex {
        fileSystem.createDirectories(outputDir)
        val entries = fileSystem.list(outputDir)
            .mapNotNull { path ->
                val match = CONGRESS_FILE_REGEX.matchEntire(path.name) ?: return@mapNotNull null
                val congress = match.groupValues[1].toInt()
                val manifest = FileBillsManifestStore(fileSystem, outputDir).load(congress)
                    ?: return@mapNotNull null
                val dates = manifest.bills.mapNotNull { it.latestAction.date.takeIf { d -> d.isNotEmpty() } }
                CongressEntry(
                    congress = congress,
                    billCount = manifest.bills.size,
                    firstActionDate = dates.minOrNull(),
                    lastActionDate = dates.maxOrNull(),
                    manifestPath = path.name,
                    isCurrent = congress == currentCongress,
                    backfillComplete = congress in completed,
                )
            }
            .sortedByDescending { it.congress }

        val index = CongressesIndex(
            generatedAt = nowIso,
            currentCongress = currentCongress,
            congresses = entries,
        )
        val text = ManifestJson.encodeToString(CongressesIndex.serializer(), index) + "\n"
        fileSystem.sink(pathFor()).buffer().use { it.writeUtf8(text) }
        return index
    }

    companion object {
        fun system(outputDir: Path): FileCongressesIndexStore =
            FileCongressesIndexStore(FileSystem.SYSTEM, outputDir)
    }
}
