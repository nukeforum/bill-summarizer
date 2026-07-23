package com.informedcitizen.pipeline.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Authoritative wire contract for `docs/data/election_calendar.json`
 * (issue #23, epic #16 — "cast their vote"). Mirrors [SessionCalendar]'s
 * shape: a `generated_at` stamp, a human-readable [source] attribution
 * for the primary/runoff dates (the federal general date is statutory —
 * see `federalGeneralElectionDate`), and a flat list of dated
 * [ElectionEvent]s the app can filter by date and state.
 *
 * The list is flat rather than keyed by state so a single nationwide
 * general-election row can coexist with per-state primary rows without a
 * special-case container; consumers group by [ElectionEvent.state] as
 * needed.
 */
@Serializable
data class ElectionCalendar(
    @SerialName("generated_at") val generatedAt: String,
    val source: String,
    val elections: List<ElectionEvent>,
)

/**
 * One dated election. [state] is a 2-letter postal code (see
 * [com.informedcitizen.pipeline.stateCode]) for a per-state event, or the
 * sentinel [NATIONWIDE] for the federal general election that every state
 * shares. [date] is an ISO-8601 calendar date (`YYYY-MM-DD`) in the local
 * election jurisdiction. [electionYear] is the even-numbered federal cycle
 * year the event belongs to, carried explicitly so the app never has to
 * re-derive it from [date] (a primary in the same year still sorts under
 * its general).
 *
 * [source] is a per-row attribution override; when null the calendar-level
 * [ElectionCalendar.source] applies. Curated primary rows carry their own
 * source so the provenance of each date travels with it — correctness of
 * these dates matters more than automation (issue #23), so provenance is
 * first-class.
 *
 * [registration] is an optional per-state voter-registration deadline for
 * this event (issue #35): the *actionable* date, since registration
 * typically closes weeks before the election itself. It is null for the
 * shared [NATIONWIDE] general row (deadlines are per-state) and for any
 * state whose deadline is unverified — correctness over coverage means an
 * absent deadline is preferred to a guessed one.
 */
@Serializable
data class ElectionEvent(
    val state: String,
    val date: String,
    val type: ElectionType = ElectionType.UNKNOWN,
    @SerialName("election_year") val electionYear: Int,
    val source: String? = null,
    val registration: RegistrationDeadline? = null,
) {
    companion object {
        /** [state] sentinel for the federal general election shared by all states. */
        const val NATIONWIDE: String = "US"
    }
}

/**
 * A state's voter-registration deadlines for a given election (issue #35,
 * epic #16 — "cast their vote"). All fields are optional so the app parses
 * leniently and the pipeline can ship this data-first, and so a state can
 * carry only the deadline types it actually distinguishes.
 *
 * [online], [byMail], and [inPerson] are ISO-8601 calendar dates
 * (`YYYY-MM-DD`); many states share one deadline across methods, in which
 * case only the applicable field(s) need be set. [byMail] is the
 * postmark-by date where the state accepts a postmark (rather than
 * receipt) cutoff. [sameDay] is true when the state offers same-day /
 * election-day registration, in which case the printed deadlines are the
 * *advance* cutoffs and a voter can still register at the polls.
 *
 * [source] is the authoritative state-election-office or vote.gov URL the
 * deadline was verified against, so the app can always link out to the
 * page of record (correctness-over-coverage: provenance travels with every
 * deadline, same rule as [ElectionEvent.source]).
 */
@Serializable
data class RegistrationDeadline(
    val online: String? = null,
    @SerialName("by_mail") val byMail: String? = null,
    @SerialName("in_person") val inPerson: String? = null,
    @SerialName("same_day") val sameDay: Boolean? = null,
    val source: String? = null,
)

/**
 * Kind of election. [UNKNOWN] is the lenient-decode fallback: because
 * [ElectionEvent.type] carries a default, `coerceInputValues` maps an
 * unrecognized wire value here instead of crashing the app's manifest read
 * (same pattern as [Outcome.UNKNOWN]).
 */
@Serializable
enum class ElectionType {
    @SerialName("general") GENERAL,
    @SerialName("primary") PRIMARY,
    @SerialName("primary_runoff") PRIMARY_RUNOFF,
    @SerialName("unknown") UNKNOWN,
}
