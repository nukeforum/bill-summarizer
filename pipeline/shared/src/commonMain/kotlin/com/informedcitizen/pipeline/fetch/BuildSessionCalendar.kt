package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.http.SessionCalendarApiException
import com.informedcitizen.pipeline.http.SessionCalendarClient
import com.informedcitizen.pipeline.model.ChamberCalendar
import com.informedcitizen.pipeline.model.SessionCalendar
import com.informedcitizen.pipeline.model.SessionCalendarSource
import kotlinx.datetime.LocalDate
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

/** Unrecoverable build failure — the CLI reports it and exits 1. */
class SessionCalendarBuildException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

data class BuildSessionCalendarResult(
    val calendar: SessionCalendar,
    val houseDayCount: Int,
    val senateDayCount: Int,
    /** Candidate years whose Senate schedule was fetched and parsed. */
    val senateYears: List<Int>,
)

/**
 * Build the session calendar from the upstream feeds. Direct port of
 * Python `build_session_calendar.py`'s `build()`:
 *
 *  1. Fetch + parse the House ICS; zero voting days is an error.
 *  2. Walk Senate candidate years (today's year − 1, +0, +1) and merge
 *     whatever parses. 404s are expected (superseded years disappear);
 *     other fetch/parse failures are remembered as `lastError`. A
 *     schedule whose `<year>` doesn't match the URL's year is treated
 *     as invalid and skipped.
 *  3. Zero Senate days across all candidates is an error, reporting
 *     `lastError` when one was seen.
 *
 * Progress callbacks mirror the Python script's stderr lines.
 */
suspend fun buildSessionCalendar(
    client: SessionCalendarClient,
    today: LocalDate,
    nowIso: String,
    onHouseParsed: (dayCount: Int) -> Unit = {},
    onSenateParsed: (dayCount: Int, years: List<Int>) -> Unit = { _, _ -> },
): BuildSessionCalendarResult {
    val houseDays = parseHouseIcs(client.fetchHouseIcs())
    if (houseDays.isEmpty()) {
        throw SessionCalendarBuildException("House ICS returned no Vote Day events")
    }
    onHouseParsed(houseDays.size)

    val senateDays = mutableSetOf<LocalDate>()
    val fetchedYears = mutableListOf<Int>()
    var lastError: Throwable? = null
    for (year in listOf(today.year - 1, today.year, today.year + 1)) {
        val text = try {
            client.fetchSenateXml(year)
        } catch (e: SessionCalendarApiException) {
            // 404 is expected for years not yet published or already
            // removed. Other statuses are remembered, not fatal.
            if (e.status != 404) lastError = e
            continue
        } catch (e: Exception) {
            lastError = e
            continue
        }
        val (parsedYear, parsedDays) = try {
            parseSenateXml(text)
        } catch (e: Exception) {
            lastError = e
            continue
        }
        if (parsedYear != year) continue
        senateDays.addAll(parsedDays)
        fetchedYears.add(year)
    }

    if (senateDays.isEmpty()) {
        throw lastError?.let {
            SessionCalendarBuildException(
                "Could not fetch any Senate schedule (last error: ${it.message})",
                it,
            )
        } ?: SessionCalendarBuildException(
            "Senate fetch returned no session days from any candidate year",
        )
    }
    onSenateParsed(senateDays.size, fetchedYears)

    val calendar = SessionCalendar(
        generatedAt = nowIso,
        source = SessionCalendarSource(
            house = client.houseUrl,
            senate = client.senateUrlForYear(today.year),
        ),
        chambers = linkedMapOf(
            "house" to ChamberCalendar(sessionDays = houseDays.map { it.toString() }),
            "senate" to ChamberCalendar(sessionDays = senateDays.sorted().map { it.toString() }),
        ),
    )
    return BuildSessionCalendarResult(
        calendar = calendar,
        houseDayCount = houseDays.size,
        senateDayCount = senateDays.size,
        senateYears = fetchedYears,
    )
}

const val SESSION_CALENDAR_FILE_NAME: String = "session_calendar.json"

/**
 * Hosts the published calendar at `<outputDir>/session_calendar.json`.
 * Parallels [FileBillsManifestStore].
 */
class FileSessionCalendarStore(
    private val fileSystem: FileSystem,
    private val outputDir: Path,
) {
    fun pathFor(): Path = outputDir / SESSION_CALENDAR_FILE_NAME

    fun save(calendar: SessionCalendar): Path {
        fileSystem.createDirectories(outputDir)
        val text = ManifestJson.encodeToString(SessionCalendar.serializer(), calendar) + "\n"
        fileSystem.sink(pathFor()).buffer().use { it.writeUtf8(text) }
        return pathFor()
    }

    companion object {
        fun system(outputDir: Path): FileSessionCalendarStore =
            FileSessionCalendarStore(FileSystem.SYSTEM, outputDir)
    }
}
