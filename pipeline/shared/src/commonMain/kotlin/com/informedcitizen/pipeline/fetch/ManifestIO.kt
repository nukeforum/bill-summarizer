package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.BillsManifest
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

/**
 * JSON config for the pipeline's published manifests. Byte-compatible
 * with Python `_common._write_json`'s
 * `json.dump(payload, f, ensure_ascii=False, indent=2, sort_keys=False)`
 * + trailing newline. `encodeDefaults = true` and `explicitNulls = true`
 * keep null fields (e.g. `"short_title": null`) and empty defaults in
 * output so the published JSON shape matches the Python pipeline's
 * output exactly during the parallel-run period before CI cuts over.
 */
internal val ManifestJson: Json = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    encodeDefaults = true
    explicitNulls = true
}

/** Mirrors Python `manifest_path_for`. */
fun manifestFileName(congress: Int): String = "congress${congress}_bills.json"

/**
 * Hosts the per-Congress bills manifest at `<outputDir>/congressNNN_bills.json`.
 * Parallels [com.informedcitizen.pipeline.state.FilePipelineStateStore]
 * but for output JSON, not run state.
 */
class FileBillsManifestStore(
    private val fileSystem: FileSystem,
    private val outputDir: Path,
) {
    fun pathFor(congress: Int): Path = outputDir / manifestFileName(congress)

    /** Read the manifest; returns `null` if the file is absent. */
    fun load(congress: Int): BillsManifest? {
        val path = pathFor(congress)
        if (!fileSystem.exists(path)) return null
        return try {
            val text = fileSystem.source(path).buffer().use { it.readUtf8() }
            ManifestJson.decodeFromString(BillsManifest.serializer(), text)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Persist the manifest for [congress] with [bills], stamping a
     * fresh `generatedAt` ([nowIso]). Returns the manifest written
     * (caller can echo to logs). Matches Python `save_manifest`.
     */
    fun save(congress: Int, bills: List<Bill>, nowIso: String): BillsManifest {
        val manifest = BillsManifest(
            generatedAt = nowIso,
            congress = congress,
            bills = bills,
        )
        outputDir.let { fileSystem.createDirectories(it) }
        val text = ManifestJson.encodeToString(BillsManifest.serializer(), manifest) + "\n"
        fileSystem.sink(pathFor(congress)).buffer().use { it.writeUtf8(text) }
        return manifest
    }

    companion object {
        fun system(outputDir: Path): FileBillsManifestStore =
            FileBillsManifestStore(FileSystem.SYSTEM, outputDir)
    }
}
