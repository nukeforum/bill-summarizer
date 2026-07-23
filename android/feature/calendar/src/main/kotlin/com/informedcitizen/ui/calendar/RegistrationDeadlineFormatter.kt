package com.informedcitizen.ui.calendar

import com.informedcitizen.pipeline.model.RegistrationDeadline
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Pure, JVM-only formatter for issue #36's registration-deadline block on the
 * #24 upcoming-elections surface. It turns the wire [RegistrationDeadline]
 * (#35) into a display state (open / urgent / closed) with the exact copy and
 * boundary rules the design mandates, so the Compose `RegistrationDeadlineBlock`
 * stays a thin renderer and the boundary logic unit-tests without Android —
 * same precedent as `DataFreshnessFormatter` and the #24/#33 formatters here.
 *
 * **Date math is civil.** Deadlines are the *last valid day* to register (per
 * #35), parsed as a [LocalDate] and compared against a caller-supplied civil
 * [LocalDate] `today` (the device zone at the call site). No `Instant`/UTC
 * conversion is introduced: a UTC parse would shift the day boundary for US
 * time zones and mis-flip "closes today" → "closed".
 *
 * **Omitted data stays silent.** [registrationDeadlineDisplay] returns null
 * whenever the data publishes no parseable deadline date, so the surface shows
 * no registration UI at all rather than a guessed or computed "closed" state
 * (the #35 wrong-data corollary). `same_day` alone never manufactures a block.
 */

/** How a voter may register, with the copy fragments the display enumerates. */
enum class RegistrationMethod(
    /** Lowercase fragment for sentence enumeration, e.g. "by mail". */
    val phrase: String,
    /** Sentence-case label for the expanded per-type detail line, e.g. "By mail". */
    val detailLabel: String,
) {
    ONLINE("online", "Online"),
    BY_MAIL("by mail", "By mail"),
    IN_PERSON("in person", "In person"),
}

/** One published registration method with its parsed deadline and countdown. */
data class RegistrationMethodDeadline(
    val method: RegistrationMethod,
    val date: LocalDate,
    /** Days from `today` to [date]; negative once the deadline has passed. */
    val daysUntil: Long,
)

/**
 * The display state of a state's registration deadline for one election. Only
 * ever constructed by [registrationDeadlineDisplay], which returns null when
 * there is nothing published to show.
 */
sealed interface RegistrationDeadlineDisplay {
    /** True when the data flags election-day (same-day) registration at the polls. */
    val sameDay: Boolean

    /**
     * At least one published method is still open. [headline] is the *earliest
     * upcoming* deadline (what the visible countdown reads), [methods] lists
     * every published method date-sorted for the expandable detail, and
     * [closedMethods] are the published methods whose deadline has already
     * passed while others remain open (e.g. online closed, in-person still open).
     */
    data class Open(
        val headline: RegistrationMethodDeadline,
        val methods: List<RegistrationMethodDeadline>,
        val closedMethods: List<RegistrationMethod>,
        override val sameDay: Boolean,
    ) : RegistrationDeadlineDisplay {
        /** Urgent styling threshold (≤ 7 days), matching #27's caution state. */
        val urgent: Boolean get() = headline.daysUntil <= URGENT_WITHIN_DAYS
    }

    /** Every published method's deadline has passed. */
    data class Closed(
        val closedMethods: List<RegistrationMethod>,
        override val sameDay: Boolean,
    ) : RegistrationDeadlineDisplay

    companion object {
        /** Registration closing within this many days renders as urgent. */
        const val URGENT_WITHIN_DAYS: Long = 7
    }
}

private val REGISTRATION_SHORT_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val REGISTRATION_A11Y_DATE = DateTimeFormatter.ofPattern("MMMM d", Locale.US)

/**
 * Compute the display state for a [deadline] relative to [today], or null when
 * nothing should render. Null is returned when [deadline] is null or when no
 * method carries a parseable ISO date — an unparseable/blank date is dropped so
 * one bad curated field can't blank the block, and a `same_day`-only or
 * source-only block (no dates) shows nothing (never a bare "closed" claim).
 */
fun registrationDeadlineDisplay(
    deadline: RegistrationDeadline?,
    today: LocalDate,
): RegistrationDeadlineDisplay? {
    if (deadline == null) return null
    val published = buildList {
        parseDeadline(RegistrationMethod.ONLINE, deadline.online, today)?.let(::add)
        parseDeadline(RegistrationMethod.BY_MAIL, deadline.byMail, today)?.let(::add)
        parseDeadline(RegistrationMethod.IN_PERSON, deadline.inPerson, today)?.let(::add)
    }
    if (published.isEmpty()) return null
    val sameDay = deadline.sameDay == true
    val open = published.filter { it.daysUntil >= 0 }
    return if (open.isEmpty()) {
        RegistrationDeadlineDisplay.Closed(published.map { it.method }, sameDay)
    } else {
        RegistrationDeadlineDisplay.Open(
            headline = open.minWith(compareBy({ it.date }, { it.method.ordinal })),
            methods = published,
            closedMethods = published.filter { it.daysUntil < 0 }.map { it.method },
            sameDay = sameDay,
        )
    }
}

/** Parse one method's ISO date into a countdown row, or null when absent/unparseable. */
private fun parseDeadline(
    method: RegistrationMethod,
    isoDate: String?,
    today: LocalDate,
): RegistrationMethodDeadline? {
    val trimmed = isoDate?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val date = runCatching { LocalDate.parse(trimmed) }.getOrNull() ?: return null
    return RegistrationMethodDeadline(method, date, ChronoUnit.DAYS.between(today, date))
}

/**
 * The visible headline for an [RegistrationDeadlineDisplay.Open] state, e.g.
 * "Registration closes in 12 days (Apr 6)" — or "Registration closes today
 * (Apr 6)" on the deadline day, which counts as open all day and only flips to
 * closed the following day.
 */
fun registrationOpenHeadline(open: RegistrationDeadlineDisplay.Open): String {
    val days = open.headline.daysUntil
    val phrase = when (days) {
        0L -> "Registration closes today"
        1L -> "Registration closes in 1 day"
        else -> "Registration closes in $days days"
    }
    return "$phrase (${REGISTRATION_SHORT_DATE.format(open.headline.date)})"
}

/**
 * The closed-state sentence enumerating exactly the published methods that have
 * passed, e.g. "Online and by mail registration has closed for this election."
 */
fun registrationClosedText(closed: RegistrationDeadlineDisplay.Closed): String =
    "${capitalize(joinMethods(closed.closedMethods))} registration has closed for this election."

/**
 * A note shown in the open state when *some* published methods have already
 * closed while others remain open (e.g. "Online registration has closed."), or
 * null when nothing has closed yet. The still-open method keeps counting down
 * via [registrationOpenHeadline].
 */
fun registrationPartialClosedText(open: RegistrationDeadlineDisplay.Open): String? =
    if (open.closedMethods.isEmpty()) {
        null
    } else {
        "${capitalize(joinMethods(open.closedMethods))} registration has closed."
    }

/**
 * The same-day-registration reassurance line, shown **only** when the data's
 * flag is true — never inferred. Consumers gate on [RegistrationDeadlineDisplay.sameDay].
 */
const val SAME_DAY_REGISTRATION_NOTE: String =
    "You can still register in person at the polls (same-day registration)."

/** One expanded-detail line for a published method, e.g. "By mail: Apr 6". */
fun registrationDetailLine(method: RegistrationMethodDeadline): String =
    "${method.method.detailLabel}: ${REGISTRATION_SHORT_DATE.format(method.date)}"

/**
 * Merged one-node screen-reader description for the whole block, so TalkBack
 * reads a single coherent utterance. Urgent open states prepend "Warning: " so
 * urgency is conveyed without relying on color (mirrors #27's data-freshness line).
 */
fun registrationDeadlineDescription(display: RegistrationDeadlineDisplay): String = when (display) {
    is RegistrationDeadlineDisplay.Open -> {
        val days = display.headline.daysUntil
        val lead = when (days) {
            0L -> "Registration closes today"
            1L -> "Registration closes in 1 day"
            else -> "Registration closes in $days days"
        }
        val methods = joinMethods(display.methods.map { it.method })
        val body = "$lead, ${REGISTRATION_A11Y_DATE.format(display.headline.date)}, $methods."
        if (display.urgent) "Warning: $body" else body
    }
    is RegistrationDeadlineDisplay.Closed -> {
        val closed = registrationClosedText(display)
        if (display.sameDay) "$closed $SAME_DAY_REGISTRATION_NOTE" else closed
    }
}

/** Oxford-comma enumeration of method [phrase]s: "online, by mail, and in person". */
private fun joinMethods(methods: List<RegistrationMethod>): String {
    val phrases = methods.map { it.phrase }
    return when (phrases.size) {
        0 -> ""
        1 -> phrases[0]
        2 -> "${phrases[0]} and ${phrases[1]}"
        else -> "${phrases.dropLast(1).joinToString(", ")}, and ${phrases.last()}"
    }
}

private fun capitalize(text: String): String =
    text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
