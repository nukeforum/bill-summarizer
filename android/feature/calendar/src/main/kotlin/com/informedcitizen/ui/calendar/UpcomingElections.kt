package com.informedcitizen.ui.calendar

import com.informedcitizen.pipeline.model.ElectionCalendar
import com.informedcitizen.pipeline.model.ElectionEvent
import com.informedcitizen.pipeline.model.ElectionType
import com.informedcitizen.pipeline.model.Member
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

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

// -- "On your ballot" bridge (issue #33) -------------------------------------
//
// The matcher that ties a member's voting record (pillar 1) to their upcoming
// ballot appearance (pillar 3). Kept beside the #24 formatters so both
// feature:calendar and feature:reps share one definition of "on the ballot".

/**
 * The upcoming general election a [member] appears on, or null when no dated
 * ballot claim can be made. A member is "on the ballot" **iff** their
 * [Member.nextElectionYear] (issue #32) equals the [ElectionEvent.electionYear]
 * of a [ElectionType.GENERAL] event in [calendar] that falls on or after
 * [today]. The soonest such general wins, and the returned election carries the
 * calendar's own [UpcomingElection.date] — so the badge is dated from published
 * data, never fabricated from the bare year (#33 wrong-data corollary).
 *
 * Degradation is total silence: null when the member has no next-election year,
 * when [calendar] is null (nothing cached), or when no upcoming general matches
 * the year. Generals only — whether an incumbent appears on a partisan primary
 * ballot is unknowable from our data, so we never claim it.
 */
internal fun ballotElectionFor(
    member: Member,
    calendar: ElectionCalendar?,
    today: LocalDate,
): UpcomingElection? {
    val year = member.nextElectionYear ?: return null
    if (calendar == null) return null
    return calendar.elections.asSequence()
        .filter { it.type == ElectionType.GENERAL && it.electionYear == year }
        .mapNotNull { event ->
            val date = runCatching { LocalDate.parse(event.date) }.getOrNull() ?: return@mapNotNull null
            UpcomingElection(event, date, ChronoUnit.DAYS.between(today, date))
        }
        .filter { it.daysUntil >= 0 }
        .minByOrNull { it.date }
}

private val BALLOT_BADGE_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
private val BALLOT_BADGE_A11Y_FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)

/**
 * Visible badge text for a member facing an upcoming general election, e.g.
 * "Up for election · Nov 3, 2026". The date comes from [ballotElectionFor]'s
 * matched calendar entry, so it is never a date fabricated from the bare year.
 */
internal fun upForElectionBadgeText(election: UpcomingElection): String =
    "Up for election · ${BALLOT_BADGE_DATE_FORMAT.format(election.date)}"

/**
 * Screen-reader fragment appended to a member card's merged description, e.g.
 * "up for election November 3, 2026" — spelled-out month so TalkBack reads it
 * naturally as part of the card's single utterance.
 */
internal fun ballotBadgeDescription(election: UpcomingElection): String =
    "up for election ${BALLOT_BADGE_A11Y_FORMAT.format(election.date)}"
