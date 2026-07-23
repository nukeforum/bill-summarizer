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

/**
 * Read-side config for decoding published manifests. Mirrors the
 * Android app's lenient wire parsing (`ignoreUnknownKeys = true` in
 * `:core:network`): a manifest carrying a field the Kotlin models
 * don't know yet must decode as the bills it holds, not fail. Without
 * this the parity shadow would read a forward-compatible manifest as a
 * parse failure — and the swallow-to-`null` load path below would then
 * report it as an *absent* file, silently rebuilding from empty and
 * producing a misleading "Kotlin lost all the bills" parity diff.
 * Kept separate from [ManifestJson] so write-side byte-parity is
 * untouched.
 */
internal val ManifestReadJson: Json = Json {
    ignoreUnknownKeys = true
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

    /**
     * Whether the votes pipeline has published an index for [congress]
     * (`congress<N>_votes.json` beside the manifest). Stamped into
     * [BillsManifest.votesCoverage] on every save; mirrors Python
     * `_common.votes_coverage`.
     */
    fun votesCoverage(congress: Int): Boolean =
        fileSystem.exists(outputDir / votesIndexFileName(congress))

    /**
     * Read the manifest; returns `null` only when the file is absent.
     * A present-but-unparseable manifest throws rather than coercing to
     * `null`: masking corruption as absence lets a caller silently
     * rebuild from an empty manifest, which shows up in the daily parity
     * diff as "Kotlin lost all the bills" with no error logged anywhere.
     * Unknown fields are tolerated (see [ManifestReadJson]) and are not
     * corruption.
     */
    fun load(congress: Int): BillsManifest? {
        val path = pathFor(congress)
        if (!fileSystem.exists(path)) return null
        val text = fileSystem.source(path).buffer().use { it.readUtf8() }
        return try {
            ManifestReadJson.decodeFromString(BillsManifest.serializer(), text)
        } catch (t: Throwable) {
            throw IllegalStateException("Failed to parse bills manifest at $path", t)
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
            votesCoverage = votesCoverage(congress),
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
