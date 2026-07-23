package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.KNOWN_STATE_CODES
import com.informedcitizen.pipeline.federalGeneralElectionDate
import com.informedcitizen.pipeline.model.ElectionCalendar
import com.informedcitizen.pipeline.model.ElectionEvent
import com.informedcitizen.pipeline.model.ElectionType
import com.informedcitizen.pipeline.model.RegistrationDeadline
import com.informedcitizen.pipeline.nextFederalGeneralElectionYear
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

/**
 * KMP parity shadow of Python `_election_calendar.py` /
 * `build_election_calendar.py` (issue #23, epic #16 — "cast their vote").
 *
 * Emits the [ElectionCalendar] wire contract to
 * `<outputDir>/election_calendar.json`: the statutory nationwide federal
 * general-election row for the next federal cycle (computed, never
 * curated — see [federalGeneralElectionDate]) merged with the
 * operator-curated per-state primary rows from
 * `data-pipeline/data/election_primaries.json`. A curated row that fails
 * validation is dropped with a warning rather than published, because a
 * missing date is safer than a wrong one (issue #23).
 */

/**
 * Default calendar-level attribution. Byte-identical to Python
 * `_election_calendar._DEFAULT_SOURCE` so the two pipelines emit the same
 * `source`. (Python's `json.dumps` escapes the `§` to a `\uXXXX`
 * sequence; this writer emits it as UTF-8 — the app decodes both
 * identically. The bytes differ only in that escaping.)
 */
const val DEFAULT_ELECTION_SOURCE: String =
    "Federal general-election date is statutory (2 U.S.C. §7, 3 U.S.C. §1); " +
        "state primary dates are operator-curated in data/election_primaries.json."

/** Curated primary types the builder accepts, mapped to their wire enum. */
private val CURATED_PRIMARY_TYPES: Map<String, ElectionType> = mapOf(
    "primary" to ElectionType.PRIMARY,
    "primary_runoff" to ElectionType.PRIMARY_RUNOFF,
)

/**
 * One row of the operator-curated `election_primaries.json` `primaries`
 * list. Fields are nullable so a partially-malformed row decodes rather
 * than failing the whole file parse, then is validated by
 * [validateCuratedPrimary] exactly as Python's `_validate_primary` walks
 * a dynamic dict.
 */
@Serializable
data class CuratedPrimary(
    val state: String? = null,
    val date: String? = null,
    val type: String? = null,
    @SerialName("election_year") val electionYear: Int? = null,
    val source: String? = null,
    val registration: CuratedRegistration? = null,
)

/**
 * One curated `registration` block on a primary row (issue #35). Fields are
 * nullable so a partially-supplied block decodes rather than failing the
 * whole file parse, then is validated by [validateRegistration] exactly as
 * Python's `_validate_registration` walks a dynamic dict.
 *
 * Unlike Python — which reads untyped dict values and warns on a non-string
 * date or non-bool `same_day` — the strongly-typed decode here rejects those
 * shapes at parse time, so those two warning branches have no KMP analogue;
 * the surviving validation (unparseable/after-election date drop, actionable
 * collapse to null) is mirrored field-for-field.
 */
@Serializable
data class CuratedRegistration(
    val online: String? = null,
    @SerialName("by_mail") val byMail: String? = null,
    @SerialName("in_person") val inPerson: String? = null,
    @SerialName("same_day") val sameDay: Boolean? = null,
    val source: String? = null,
)

/**
 * Coerce a curated [CuratedRegistration] block to a wire
 * [RegistrationDeadline], or return `null` if nothing verifiable survives
 * (issue #35). Mirrors Python `_election_calendar._validate_registration`.
 *
 * Correctness over coverage: a deadline dated *after* its election is
 * nonsensical (registration closes on or before election day) so that field
 * is dropped with a warning, and a block that contributes nothing
 * actionable — no valid method deadline and no same-day availability —
 * collapses to `null` so the event omits `registration` rather than carrying
 * an empty husk.
 */
fun validateRegistration(
    reg: CuratedRegistration?,
    eventDate: LocalDate,
    label: String,
    onWarn: (String) -> Unit = {},
): RegistrationDeadline? {
    if (reg == null) return null

    fun keepDate(field: String, value: String?): String? {
        if (value == null) return null
        val parsed = try {
            LocalDate.parse(value)
        } catch (e: IllegalArgumentException) {
            onWarn("dropping $label registration.$field: unparseable $value")
            return null
        }
        if (parsed > eventDate) {
            onWarn("dropping $label registration.$field $value: after election $eventDate")
            return null
        }
        return value
    }

    val online = keepDate("online", reg.online)
    val byMail = keepDate("by_mail", reg.byMail)
    val inPerson = keepDate("in_person", reg.inPerson)
    val sameDay = reg.sameDay

    // A block is useful only if it carries at least one method deadline or
    // advertises same-day registration; source alone (or an explicit
    // same_day:false with no dates) tells the voter nothing to act on.
    val hasDeadline = online != null || byMail != null || inPerson != null
    if (!hasDeadline && sameDay != true) {
        val anythingSupplied = sameDay != null || reg.source != null ||
            reg.online != null || reg.byMail != null || reg.inPerson != null
        if (anythingSupplied) {
            onWarn("dropping $label registration: no usable deadline survived")
        }
        return null
    }

    return RegistrationDeadline(
        online = online,
        byMail = byMail,
        inPerson = inPerson,
        sameDay = sameDay,
        source = reg.source?.takeIf { it.isNotEmpty() },
    )
}

/**
 * Coerce one curated row to a wire [ElectionEvent], or return `null` (via
 * [onWarn]) if it fails validation. Rows for cycles strictly before
 * [minYear] are dropped silently as stale, not warned. Mirrors Python
 * `_election_calendar._validate_primary` field-for-field, including the
 * order of checks so the same first failure is reported.
 */
fun validateCuratedPrimary(
    row: CuratedPrimary,
    minYear: Int,
    onWarn: (String) -> Unit = {},
): ElectionEvent? {
    val state = row.state
    if (state == null || state !in KNOWN_STATE_CODES) {
        onWarn("skipping primary with unknown state $state")
        return null
    }
    val type = CURATED_PRIMARY_TYPES[row.type]
    if (type == null) {
        onWarn("skipping $state primary with bad type ${row.type}")
        return null
    }
    val year = row.electionYear
    if (year == null || year % 2 != 0) {
        onWarn("skipping $state primary with non-even election_year $year")
        return null
    }
    val date = row.date
    if (date == null) {
        onWarn("skipping $state primary with non-string date null")
        return null
    }
    val parsed = try {
        LocalDate.parse(date)
    } catch (e: IllegalArgumentException) {
        onWarn("skipping $state primary with unparseable date $date")
        return null
    }
    if (parsed.year != year) {
        onWarn("skipping $state primary: date $date not in election_year $year")
        return null
    }
    if (year < minYear) {
        // Stale cycle — a past primary, not an error worth surfacing.
        return null
    }
    return ElectionEvent(
        state = state,
        date = date,
        type = type,
        electionYear = year,
        source = row.source?.takeIf { it.isNotEmpty() },
        registration = validateRegistration(row.registration, parsed, "$state ${row.type}", onWarn),
    )
}

/**
 * Build the `election_calendar.json` payload. Always includes the
 * statutory nationwide general-election row for the next federal cycle
 * ([nextFederalGeneralElectionYear] of [today]); merges any valid curated
 * primary rows for that cycle or later. Events are sorted by
 * `(date, state)` so the app can render them chronologically. Direct port
 * of Python `_election_calendar.build_election_calendar`.
 */
fun buildElectionCalendar(
    today: LocalDate,
    generatedAt: String,
    curatedPrimaries: List<CuratedPrimary> = emptyList(),
    source: String = DEFAULT_ELECTION_SOURCE,
    onWarn: (String) -> Unit = {},
): ElectionCalendar {
    val cycleYear = nextFederalGeneralElectionYear(today)
    val generalDate = federalGeneralElectionDate(cycleYear)

    val elections = mutableListOf(
        ElectionEvent(
            state = ElectionEvent.NATIONWIDE,
            date = generalDate.toString(),
            type = ElectionType.GENERAL,
            electionYear = cycleYear,
        ),
    )
    for (row in curatedPrimaries) {
        validateCuratedPrimary(row, minYear = cycleYear, onWarn = onWarn)?.let { elections.add(it) }
    }
    elections.sortWith(compareBy({ it.date }, { it.state }))

    return ElectionCalendar(
        generatedAt = generatedAt,
        source = source,
        elections = elections,
    )
}

/** Lenient reader for the curated primaries file — unknown keys (the
 * `_meta` block) are ignored, and a malformed field type doesn't abort. */
private val CuratedPrimariesJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Write config for the published calendar. `explicitNulls = false` so the
 * general row (no `source`) omits the key entirely, matching Python's
 * event dicts (which only add `source` when a curated row carries one).
 */
internal val ElectionCalendarJson: Json = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    encodeDefaults = true
    explicitNulls = false
}

const val ELECTION_CALENDAR_FILE_NAME: String = "election_calendar.json"

/**
 * Load the curated primaries from `election_primaries.json`. A missing
 * file is tolerated (the calendar still publishes the statutory general
 * row) via [onWarn]; a present-but-broken file (no `primaries` array) is
 * an error so the operator notices rather than silently shipping an empty
 * primary set. Mirrors Python `build_election_calendar._load_curated_primaries`.
 */
fun loadCuratedPrimaries(
    fileSystem: FileSystem,
    path: Path,
    onWarn: (String) -> Unit = {},
): List<CuratedPrimary> {
    if (!fileSystem.exists(path)) {
        onWarn("curated primaries file $path missing; publishing general only")
        return emptyList()
    }
    val text = fileSystem.source(path).buffer().use { it.readUtf8() }
    val root = CuratedPrimariesJson.parseToJsonElement(text) as? JsonObject
        ?: throw IllegalStateException("$path is not a JSON object")
    val primaries = root["primaries"]
        ?: throw IllegalStateException("$path has no 'primaries' list")
    return primaries.jsonArray.map { CuratedPrimariesJson.decodeFromJsonElement(CuratedPrimary.serializer(), it) }
}

/**
 * Hosts the published calendar at `<outputDir>/election_calendar.json`.
 * Parallels [FileSessionCalendarStore].
 */
class FileElectionCalendarStore(
    private val fileSystem: FileSystem,
    private val outputDir: Path,
) {
    fun pathFor(): Path = outputDir / ELECTION_CALENDAR_FILE_NAME

    fun save(calendar: ElectionCalendar): Path {
        fileSystem.createDirectories(outputDir)
        val text = ElectionCalendarJson.encodeToString(ElectionCalendar.serializer(), calendar) + "\n"
        fileSystem.sink(pathFor()).buffer().use { it.writeUtf8(text) }
        return pathFor()
    }

    companion object {
        fun system(outputDir: Path): FileElectionCalendarStore =
            FileElectionCalendarStore(FileSystem.SYSTEM, outputDir)
    }
}
