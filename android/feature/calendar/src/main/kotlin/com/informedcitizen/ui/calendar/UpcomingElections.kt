package com.informedcitizen.ui.calendar

import com.informedcitizen.pipeline.model.ElectionCalendar
import com.informedcitizen.pipeline.model.ElectionEvent
import com.informedcitizen.pipeline.model.ElectionType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * One upcoming election with its computed days-remaining countdown, ready
 * for issue #24's upcoming-elections surface. Pure/JVM-only so it unit
 * tests without Android — same precedent as [upcomingBlocks] (session
 * days) and `DataFreshnessFormatter` (bills list).
 */
data class UpcomingElection(
    val event: ElectionEvent,
    val date: LocalDate,
    val daysUntil: Long,
) {
    /** True for the shared federal general row ([ElectionEvent.NATIONWIDE]). */
    val isNationwide: Boolean get() = event.state == ElectionEvent.NATIONWIDE

    /** "Today" / "Tomorrow" / "in N days" countdown for [date] relative to today. */
    val countdownText: String
        get() = when (daysUntil) {
            0L -> "Today"
            1L -> "Tomorrow"
            else -> "in $daysUntil days"
        }
}

/**
 * Human label for an [ElectionType]. [ElectionType.UNKNOWN] — the
 * lenient-decode fallback for a future wire value — reads as a generic
 * "Election" rather than leaking the enum name.
 */
internal fun electionTypeLabel(type: ElectionType): String = when (type) {
    ElectionType.GENERAL -> "General election"
    ElectionType.PRIMARY -> "Primary"
    ElectionType.PRIMARY_RUNOFF -> "Primary runoff"
    ElectionType.UNKNOWN -> "Election"
}

/**
 * Title line for one election row. The shared federal general
 * ([UpcomingElection.isNationwide]) reads as just its type label; a
 * per-state event is prefixed with its postal code (e.g. "TX Primary")
 * so the row is self-describing when several states appear together.
 */
internal fun electionRowLabel(election: UpcomingElection): String {
    val type = electionTypeLabel(election.event.type)
    return if (election.isNationwide) type else "${election.event.state} $type"
}

/**
 * Merged screen-reader description for an election row: label, formatted
 * date, and countdown as one utterance so TalkBack doesn't read three
 * disconnected fragments.
 */
internal fun electionRowDescription(election: UpcomingElection, formattedDate: String): String =
    "${electionRowLabel(election)}, $formattedDate, ${election.countdownText}"

/**
 * Upcoming elections relevant to [stateCode] (a 2-letter postal code, or
 * null / blank for a national-only view). Keeps events on or after [today]
 * that are either nationwide (the shared federal general) or match
 * [stateCode] (case-insensitive), parses their ISO dates — skipping any
 * unparseable row so one bad curated date can't blank the whole surface —
 * and returns them soonest-first capped at [limit].
 *
 * With no known state only the nationwide general appears, satisfying
 * issue #24's "fall back to a national view when no state is known".
 */
internal fun upcomingElections(
    calendar: ElectionCalendar,
    today: LocalDate,
    stateCode: String?,
    limit: Int = 5,
): List<UpcomingElection> {
    val normalizedState = stateCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
    return calendar.elections.asSequence()
        .filter {
            it.state == ElectionEvent.NATIONWIDE ||
                (normalizedState != null && it.state.uppercase() == normalizedState)
        }
        .mapNotNull { event ->
            val date = runCatching { LocalDate.parse(event.date) }.getOrNull() ?: return@mapNotNull null
            UpcomingElection(event, date, ChronoUnit.DAYS.between(today, date))
        }
        .filter { it.daysUntil >= 0 }
        .sortedWith(compareBy({ it.date }, { it.event.state }))
        .take(limit)
        .toList()
}

/**
 * The single soonest upcoming election for [stateCode], or null when the
 * calendar holds nothing on or after [today] — the value the compact
 * "next election in N days" line and the section header read.
 */
internal fun nextElection(
    calendar: ElectionCalendar,
    today: LocalDate,
    stateCode: String?,
): UpcomingElection? = upcomingElections(calendar, today, stateCode, limit = 1).firstOrNull()
