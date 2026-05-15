package com.informedcitizen.pipeline.state

import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

/**
 * JSON config tuned to produce output byte-identical to Python's
 * `json.dump(state, f, ensure_ascii=False, indent=2, sort_keys=False)`:
 * 2-space indent, field-order preserved (kotlinx-serialization keeps
 * constructor order), `null` written explicitly for absent fields,
 * non-ASCII passes through unescaped. The file write below appends a
 * trailing newline to match Python's `f.write("\n")`.
 */
internal val PipelineStateJson: Json = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    // Python's `json.dump` writes every dict key — including empty
    // lists (`"completed": []`) and explicit `null`s (`"last_run_at":
    // null`). Match that exactly so a mid-cut-over reload of a file
    // either side wrote can't seed-default a present-but-null field
    // into a fresh queue. Both flags REQUIRED for byte parity:
    encodeDefaults = true
    explicitNulls = true
}

/**
 * okio-backed [PipelineStateStore]. Production wires it via
 * [FilePipelineStateStore.system] with [FileSystem.SYSTEM]; tests pass
 * a `FakeFileSystem` for hermetic round-trips.
 *
 * The [backfillPath] is the full path to `backfill_state.json`, not
 * its parent directory — explicit so the JVM CLI can point at
 * `data-pipeline/state/backfill_state.json` and inherit the existing
 * Python cursor without renaming or migrating.
 */
class FilePipelineStateStore(
    private val fileSystem: FileSystem,
    private val backfillPath: Path,
) : PipelineStateStore {

    override fun loadBackfillState(initial: () -> BackfillState): BackfillState {
        if (!fileSystem.exists(backfillPath)) return initial()
        return try {
            val text = fileSystem.source(backfillPath).buffer().use { it.readUtf8() }
            PipelineStateJson.decodeFromString(BackfillState.serializer(), text)
        } catch (_: Throwable) {
            // Match Python: any read/parse failure falls back to initial
            // state. A corrupted file never wedges the next scheduled run.
            initial()
        }
    }

    override fun saveBackfillState(state: BackfillState) {
        backfillPath.parent?.let { fileSystem.createDirectories(it) }
        val text = PipelineStateJson.encodeToString(BackfillState.serializer(), state) + "\n"
        fileSystem.sink(backfillPath).buffer().use { it.writeUtf8(text) }
    }

    companion object {
        const val BACKFILL_STATE_FILE: String = "backfill_state.json"

        /**
         * Standard store rooted at [stateDir] on the real filesystem.
         * Wires to `<stateDir>/backfill_state.json` — same filename
         * the Python pipeline uses, so the JVM CLI can point at
         * `data-pipeline/state/` and pick up live cursors mid-crawl.
         */
        fun system(stateDir: Path): FilePipelineStateStore =
            FilePipelineStateStore(
                fileSystem = FileSystem.SYSTEM,
                backfillPath = stateDir / BACKFILL_STATE_FILE,
            )
    }
}
