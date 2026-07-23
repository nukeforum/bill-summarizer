package com.informedcitizen.ui.billslist

import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Freshness threshold mirroring the pipeline's own bills gate in
 * `data-pipeline/scripts/check_freshness.py` (bills < 2 days). Kept in
 * sync by convention, not code: past this age the freshness line switches
 * to a caution state so staleness is visible rather than silent.
 */
val STALE_AFTER: Duration = Duration.ofDays(2)

/**
 * Parse a manifest `generated_at` value (ISO-8601 UTC, e.g.
 * `2026-07-22T07:55:09Z`) into an [Instant]. Returns null for a null,
 * blank, or unparseable value so the freshness line hides entirely rather
 * than ever showing "updated never" — manifests predating the field, or a
 * format drift, degrade gracefully.
 */
fun parseGeneratedAt(raw: String?): Instant? {
    if (raw.isNullOrBlank()) return null
    return try {
        Instant.parse(raw)
    } catch (_: DateTimeParseException) {
        null
    }
}

/**
 * Render the artifact's age as a relative label ("Updated N hours ago").
 * Relative rather than absolute so time zones never surface — [generatedAt]
 * is UTC and the label reads identically on any device zone, satisfying the
 * cross-timezone acceptance criterion. A [generatedAt] in the future
 * (clock skew) reads as "just now" rather than a negative age.
 */
fun formatDataFreshness(generatedAt: Instant, now: Instant): String {
    val age = Duration.between(generatedAt, now)
    val minutes = age.toMinutes()
    return when {
        minutes < 1 -> "Updated just now"
        minutes < 60 -> "Updated ${minutes.pluralize("minute")} ago"
        age.toHours() < 24 -> "Updated ${age.toHours().pluralize("hour")} ago"
        else -> "Updated ${age.toDays().pluralize("day")} ago"
    }
}

/** True once the artifact is older than the pipeline's freshness threshold. */
fun isDataStale(generatedAt: Instant, now: Instant): Boolean =
    Duration.between(generatedAt, now) >= STALE_AFTER

private fun Long.pluralize(unit: String): String =
    if (this == 1L) "1 $unit" else "$this ${unit}s"
