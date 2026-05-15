package com.informedcitizen.pipeline.fetch

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

/**
 * UTC, seconds-precision ISO-8601 with trailing `Z`. Matches Python
 * `_common.now_iso()`:
 *  - `datetime.now(timezone.utc).replace(microsecond=0)`
 *  - `.isoformat().replace("+00:00", "Z")`
 *
 * `now` is parameterized for tests; production callers pass nothing
 * and the default `Clock.System.now()` applies.
 */
fun nowIso(now: Instant = Clock.System.now()): String {
    // Truncate to seconds so output never includes fractional digits,
    // matching Python's `.replace(microsecond=0)`.
    val seconds = Instant.fromEpochSeconds(now.epochSeconds)
    return seconds.toString()
}

/**
 * Parse a value Congress.gov returns as either a full ISO-8601 instant
 * (`"2026-04-20T13:45:00Z"`) or a bare ISO date (`"2026-04-20"`).
 * Bare-date values are anchored at UTC midnight (Python: same).
 * Returns `null` on null/empty/unparseable input, matching Python's
 * `parse_iso_date` (which swallows ValueError).
 */
fun parseIsoInstant(value: String?): Instant? {
    if (value.isNullOrEmpty()) return null
    return try {
        if ('T' in value) {
            Instant.parse(value)
        } else {
            LocalDate.parse(value).atStartOfDayIn(TimeZone.UTC)
        }
    } catch (_: Throwable) {
        null
    }
}
