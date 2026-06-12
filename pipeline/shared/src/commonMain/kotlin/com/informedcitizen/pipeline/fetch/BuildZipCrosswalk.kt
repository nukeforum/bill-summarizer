package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.ErrorCollector
import com.informedcitizen.pipeline.http.HudClient
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

/**
 * Build a compact ZIP→{state, [districts]} JSON for the Android app.
 * Direct port of Python `build_zip_crosswalk.py`'s API mode — iterate
 * states (type=5, zip-cd) against HUD's `/usps` endpoint and reduce to
 * ZIPs. The CSV mode (manual-download fallback) is not ported; it
 * predates the API mode and CI never uses it.
 */

@Serializable
data class ZipCrosswalkEntry(
    val state: String,
    val districts: List<Int>,
)

/**
 * 50 states + DC + 5 territories, in Python `_STATE_QUERIES` order.
 * The order is load-bearing for byte parity: output keys appear in
 * first-encounter order, which follows this walk.
 */
val HUD_STATE_QUERIES: List<String> = listOf(
    "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA",
    "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD",
    "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ",
    "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC",
    "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY",
    "DC", "AS", "GU", "MP", "PR", "VI",
)

/**
 * Normalize HUD's 4-digit CD GEOID (FIPS+CD, e.g. "0501") to a
 * district int. The last 2 digits are the CD code. "00" (at-large) and
 * "98" (non-voting delegate) both map to 0 in our output for UI
 * uniformity.
 *
 * Returns `null` for values that can't be parsed (e.g. HUD's "**"
 * marker for ZIPs with no valid CD mapping — typically military
 * APO/FPO and PO-box ZIPs). Callers skip rows where this returns null.
 */
fun normalizeCdCode(raw: String): Int? {
    val s = raw.trim()
    if (s.isEmpty()) return null
    val cdDigits = s.takeLast(2).trimStart('0').ifEmpty { "0" }
    if (!cdDigits.all { it.isDigit() }) return null
    val n = cdDigits.toInt()
    return if (n == 98) 0 else n
}

/**
 * Adaptively pull the results array from a HUD API response — the
 * shape isn't fully documented; try common keys. Mirrors Python
 * `_extract_results` (KeyError → [IllegalStateException]).
 */
fun extractHudResults(body: JsonObject): List<JsonObject> {
    val data = (body["data"] as? JsonObject) ?: body
    for (key in listOf("results", "result", "items")) {
        (data[key] as? JsonArray)?.let { return it.filterIsInstance<JsonObject>() }
    }
    (body["results"] as? JsonArray)?.let { return it.filterIsInstance<JsonObject>() }
    error("could not find results list in response; top keys: ${body.keys.toList()}")
}

fun extractHudZip(row: JsonObject): String? {
    for (key in listOf("zip", "ZIP", "Zip", "zipcode", "zip_code")) {
        (row[key] as? JsonPrimitive)?.let { return it.content.padStart(5, '0') }
    }
    return null
}

/**
 * Pull the 4-digit CD GEOID from a type=5 result row. The docs say the
 * per-row geometry field name varies by crosswalk type ('cd' for
 * zip-cd); it also might be exposed as 'geoid'. Try both.
 */
fun extractHudCdValue(row: JsonObject): String? {
    for (key in listOf("cd", "CD", "geoid", "GEOID")) {
        (row[key] as? JsonPrimitive)?.let { return it.content }
    }
    return null
}

data class BuildZipCrosswalkResult(
    val byZip: Map<String, ZipCrosswalkEntry>,
    val statesFetched: Int,
    val misses: Int,
)

/**
 * Walk every state query against HUD's `/usps` endpoint and reduce to
 * a ZIP-keyed map. Per-state failures (HTTP error, unrecognized
 * response shape) are recorded in [errors] and counted as misses; an
 * entirely empty result throws rather than letting the caller publish
 * an empty asset. Mirrors Python `build_from_api`.
 *
 * Note: Python raises on ANY non-200 including 404, while [HudClient]
 * maps 404 to an empty body — the empty body then fails result
 * extraction, so a 404 still lands in the miss count, just keyed
 * `extract_results` instead of `hud_get`.
 */
suspend fun buildZipCrosswalkFromApi(
    hud: HudClient,
    year: Int? = null,
    quarter: Int? = null,
    sleepMillis: Long = 0L,
    stateQueries: List<String> = HUD_STATE_QUERIES,
    errors: ErrorCollector = ErrorCollector(),
    onStateDone: (state: String, added: Int, skipped: Int) -> Unit = { _, _, _ -> },
): BuildZipCrosswalkResult {
    val byZip = LinkedHashMap<String, Pair<String, MutableSet<Int>>>()
    var misses = 0

    for (state in stateQueries) {
        val params = buildMap {
            put("type", "5")
            put("query", state)
            year?.let { put("year", it.toString()) }
            quarter?.let { put("quarter", it.toString()) }
        }
        val body = try {
            hud.get("/usps", params)
        } catch (e: Exception) {
            errors.record(
                kind = "hud_get",
                identifier = state,
                errorClass = e::class.simpleName ?: "Exception",
                message = e.message ?: "",
                params = params,
            )
            misses++
            continue
        }
        val rows = try {
            extractHudResults(body)
        } catch (e: Exception) {
            errors.record(
                kind = "extract_results",
                identifier = state,
                errorClass = e::class.simpleName ?: "Exception",
                message = e.message ?: "",
                params = params,
            )
            misses++
            continue
        }
        var added = 0
        var skipped = 0
        for (row in rows) {
            val zip = extractHudZip(row)
            val cdValue = extractHudCdValue(row)
            if (zip == null || cdValue.isNullOrEmpty()) {
                skipped++
                continue
            }
            val district = normalizeCdCode(cdValue)
            if (district == null) {
                // HUD's "**" marker etc. — ZIP with no clean CD mapping.
                skipped++
                continue
            }
            val entry = byZip.getOrPut(zip) { state to mutableSetOf() }
            // Incoming state wins, matching Python's unconditional
            // entry["state"] = state overwrite.
            byZip[zip] = state to entry.second.apply { add(district) }
            added++
        }
        onStateDone(state, added, skipped)
        if (sleepMillis > 0) delay(sleepMillis)
    }

    if (byZip.isEmpty()) {
        throw RuntimeException("no ZIPs collected — refusing to write empty asset")
    }
    val output = byZip.mapValues { (_, v) ->
        ZipCrosswalkEntry(state = v.first, districts = v.second.sorted())
    }
    return BuildZipCrosswalkResult(
        byZip = output,
        statesFetched = stateQueries.size - misses,
        misses = misses,
    )
}

/**
 * Compact serializer matching Python `_emit`'s
 * `json.dump(output, f, ensure_ascii=False, separators=(",", ":"))` —
 * no whitespace, insertion-ordered keys, no trailing newline.
 */
private val CompactJson = Json

private val ZipCrosswalkSerializer =
    MapSerializer(String.serializer(), ZipCrosswalkEntry.serializer())

fun encodeZipCrosswalk(byZip: Map<String, ZipCrosswalkEntry>): String =
    CompactJson.encodeToString(ZipCrosswalkSerializer, byZip)

/** Writes the asset to an explicit file path (not a directory). */
class FileZipCrosswalkStore(
    private val fileSystem: FileSystem,
    private val outputPath: Path,
) {
    fun save(byZip: Map<String, ZipCrosswalkEntry>): Path {
        outputPath.parent?.let { fileSystem.createDirectories(it) }
        fileSystem.sink(outputPath).buffer().use { it.writeUtf8(encodeZipCrosswalk(byZip)) }
        return outputPath
    }

    companion object {
        fun system(outputPath: Path): FileZipCrosswalkStore =
            FileZipCrosswalkStore(FileSystem.SYSTEM, outputPath)
    }
}
