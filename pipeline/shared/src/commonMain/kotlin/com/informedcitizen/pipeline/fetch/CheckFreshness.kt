package com.informedcitizen.pipeline.fetch

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

/**
 * Assert published pipeline artifacts are fresh. Direct port of Python
 * `check_freshness.py` — returns a list of failure messages; empty
 * means everything is fresh. Run from a scheduled workflow so a quiet
 * failure (a workflow not running, a silent API outage, a stuck
 * backfill cursor) becomes a noisy notification rather than data
 * silently aging on the Pages site.
 *
 * Checks (each independently emits a line):
 *  - Current-Congress bills manifest `generated_at` within
 *    [BILLS_MAX_AGE_DAYS].
 *  - Members index `generated_at` within [MEMBERS_MAX_AGE_DAYS].
 *  - Session calendar's latest House and Senate session day at least
 *    [CALENDAR_MIN_LOOKAHEAD_DAYS] ahead of today (so the bills
 *    list's "session" line never reads "session has ended").
 *  - `backfill_state.json.last_run_at` advanced within
 *    [BACKFILL_MAX_AGE_DAYS] — unless the backfill queue is empty, in
 *    which case the cursor is allowed to be stale.
 */
const val BILLS_MAX_AGE_DAYS: Int = 2
const val MEMBERS_MAX_AGE_DAYS: Int = 14
const val CALENDAR_MIN_LOOKAHEAD_DAYS: Int = 30
const val BACKFILL_MAX_AGE_DAYS: Int = 3

private fun parseIsoUtc(value: String?): Instant? {
    if (value.isNullOrEmpty()) return null
    return try {
        Instant.parse(value)
    } catch (_: Exception) {
        null
    }
}

private fun loadJson(fileSystem: FileSystem, path: Path): JsonObject? {
    if (!fileSystem.exists(path)) return null
    return try {
        val text = fileSystem.source(path).buffer().use { it.readUtf8() }
        Json.parseToJsonElement(text) as? JsonObject
    } catch (_: Exception) {
        null
    }
}

fun checkFreshness(
    fileSystem: FileSystem,
    outputDir: Path,
    stateDir: Path,
    congress: Int,
    now: Instant,
): List<String> {
    val today: LocalDate = now.toLocalDateTime(TimeZone.UTC).date
    val failures = mutableListOf<String>()

    fun staleness(generatedAt: String?): Long? =
        parseIsoUtc(generatedAt)?.let { (now - it).inWholeDays }

    // 1. Current-Congress bills manifest freshness.
    val billsName = manifestFileName(congress)
    val bills = loadJson(fileSystem, outputDir / billsName)
    if (bills == null) {
        failures += "bills: $billsName missing or unreadable"
    } else {
        val generatedAt = bills.stringField("generated_at")
        val ageDays = staleness(generatedAt)
        if (ageDays == null) {
            failures += "bills: $billsName has no parseable generated_at"
        } else if (ageDays >= BILLS_MAX_AGE_DAYS) {
            failures += "bills: $billsName generated_at=$generatedAt " +
                "is older than $BILLS_MAX_AGE_DAYS days"
        }
    }

    // 2. Members index freshness.
    val membersName = membersIndexFileName(congress)
    val members = loadJson(fileSystem, outputDir / membersName)
    if (members == null) {
        failures += "members: $membersName missing or unreadable"
    } else {
        val generatedAt = members.stringField("generated_at")
        val ageDays = staleness(generatedAt)
        if (ageDays == null) {
            failures += "members: $membersName has no parseable generated_at"
        } else if (ageDays >= MEMBERS_MAX_AGE_DAYS) {
            failures += "members: $membersName generated_at=$generatedAt " +
                "is older than $MEMBERS_MAX_AGE_DAYS days"
        }
    }

    // 3. Session calendar look-ahead per chamber.
    val cal = loadJson(fileSystem, outputDir / SESSION_CALENDAR_FILE_NAME)
    if (cal == null) {
        failures += "calendar: $SESSION_CALENDAR_FILE_NAME missing or unreadable"
    } else {
        val chambers = cal["chambers"] as? JsonObject ?: JsonObject(emptyMap())
        for (chamber in listOf("house", "senate")) {
            val days = ((chambers[chamber] as? JsonObject)?.get("session_days") as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                .orEmpty()
            val todayIso = today.toString()
            val futureDays = days.filter { it >= todayIso }
            if (futureDays.isEmpty()) {
                failures += "calendar: $chamber has no session days on or after $today"
                continue
            }
            val last = try {
                LocalDate.parse(futureDays.max())
            } catch (_: Exception) {
                failures += "calendar: $chamber last future day is malformed"
                continue
            }
            if (today.daysUntil(last) < CALENDAR_MIN_LOOKAHEAD_DAYS) {
                failures += "calendar: $chamber last known session day $last is less than " +
                    "$CALENDAR_MIN_LOOKAHEAD_DAYS days out; upstream feed needs refresh"
            }
        }
    }

    // 4. Backfill cursor advancement (only if there's still work queued).
    // No state file is acceptable on a brand-new repo; not a failure.
    val state = loadJson(fileSystem, stateDir / "backfill_state.json")
    if (state != null && state["active_congress"] !is JsonNull && state["active_congress"] != null) {
        val lastRunAt = state.stringField("last_run_at")
        val ageDays = staleness(lastRunAt)
        if (ageDays == null) {
            failures += "backfill: state has no parseable last_run_at"
        } else if (ageDays >= BACKFILL_MAX_AGE_DAYS) {
            failures += "backfill: last_run_at=$lastRunAt is older than " +
                "$BACKFILL_MAX_AGE_DAYS days"
        }
    }

    return failures
}
