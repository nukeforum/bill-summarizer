package com.informedcitizen.pipeline.fetch

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

/**
 * Pure parsing functions for House and Senate session-day feeds.
 * Direct port of Python `_session_calendar.py`; network I/O lives in
 * [buildSessionCalendar].
 *
 * House feed: an iCalendar (.ics) document published by USHOR. Events
 * tagged `Vote Day` or `Added Vote Day` are session days. Other
 * categories (Travel Day, Pro Forma Session, Federal Holiday, Canceled
 * Vote Day, Non-federal Holiday) are excluded — the user-facing intent
 * is "today is a likely floor-activity day", which excludes pro-forma
 * and cancelled days.
 *
 * Senate feed: an XML schedule that lists *non-legislative* periods
 * rather than session days. Session days are derived as `weekdays in
 * year` minus `days inside any <date> range`. The Senate occasionally
 * meets on weekends for cloture/filibuster votes, but the published
 * schedule treats such days as exceptions; for a planning indicator
 * the weekday rule is correct.
 */
val HOUSE_VOTING_CATEGORIES: Set<String> = setOf("Vote Day", "Added Vote Day")

/**
 * Extract sorted, deduplicated House voting days from an iCalendar
 * text. Line-by-line scan of the DTSTART + CATEGORIES subset the feed
 * actually uses (no KMP ical library exists) — identical to the Python
 * parser, including its indifference to RFC 5545 line folding (folded
 * SUMMARY continuations are ignored because only DTSTART/CATEGORIES
 * prefixes are inspected).
 */
fun parseHouseIcs(text: String): List<LocalDate> {
    val days = mutableSetOf<LocalDate>()
    var inEvent = false
    var eventStart: LocalDate? = null
    var eventCategories = mutableSetOf<String>()

    for (rawLine in text.lineSequence()) {
        val line = rawLine.trimEnd('\r')
        when {
            line == "BEGIN:VEVENT" -> {
                inEvent = true
                eventStart = null
                eventCategories = mutableSetOf()
            }
            line == "END:VEVENT" -> {
                val start = eventStart
                if (inEvent && start != null && eventCategories.any { it in HOUSE_VOTING_CATEGORIES }) {
                    days.add(start)
                }
                inEvent = false
            }
            inEvent -> {
                if (line.startsWith("DTSTART;VALUE=DATE:")) {
                    val ymd = line.substringAfter(':').trim()
                    if (ymd.length == 8 && ymd.all { it.isDigit() }) {
                        eventStart = LocalDate(
                            ymd.substring(0, 4).toInt(),
                            ymd.substring(4, 6).toInt(),
                            ymd.substring(6, 8).toInt(),
                        )
                    }
                } else if (line.startsWith("CATEGORIES:")) {
                    val values = line.substringAfter(':')
                    eventCategories.addAll(values.split(',').map { it.trim() })
                }
            }
        }
    }

    return days.sorted()
}

// The Senate feed's structure is trivial (<year> plus flat <date>
// blocks holding <beginDate>/<endDate>), so the extraction is
// regex-based rather than pulling in a KMP XML library. A document
// malformed enough to defeat these patterns surfaces as the same
// "missing <year>" failure the Python ElementTree parse error feeds
// into: the builder records it and tries the next candidate year.
private val SENATE_YEAR_RE = Regex("<year>([^<]*)</year>")
private val SENATE_DATE_BLOCK_RE = Regex("<date>(.*?)</date>", RegexOption.DOT_MATCHES_ALL)
private val SENATE_BEGIN_RE = Regex("<beginDate>([^<]*)</beginDate>")
private val SENATE_END_RE = Regex("<endDate>([^<]*)</endDate>")

/**
 * Derive Senate session days from a Senate Schedule XML document.
 *
 * Returns `(year, sessionDays)`. Session days are weekdays in the
 * schedule's `<year>` that are NOT inside any `<date>` range.
 */
fun parseSenateXml(text: String): Pair<Int, List<LocalDate>> {
    val yearText = SENATE_YEAR_RE.find(text)?.groupValues?.get(1)?.trim().orEmpty()
    require(yearText.isNotEmpty()) { "Senate XML: missing <year>" }
    val year = yearText.toInt()

    val excluded = mutableSetOf<LocalDate>()
    for (block in SENATE_DATE_BLOCK_RE.findAll(text)) {
        val body = block.groupValues[1]
        val beginText = SENATE_BEGIN_RE.find(body)?.groupValues?.get(1)?.trim().orEmpty()
        val endText = SENATE_END_RE.find(body)?.groupValues?.get(1)?.trim().orEmpty()
        if (beginText.isEmpty() || endText.isEmpty()) continue
        val begin = LocalDate.parse(beginText)
        val end = LocalDate.parse(endText)
        require(end >= begin) { "Senate XML: endDate $end before beginDate $begin" }
        var cur = begin
        while (cur <= end) {
            excluded.add(cur)
            cur = cur.plus(1, DateTimeUnit.DAY)
        }
    }

    val days = mutableListOf<LocalDate>()
    var cur = LocalDate(year, 1, 1)
    val end = LocalDate(year, 12, 31)
    while (cur <= end) {
        if (cur.dayOfWeek.isoDayNumber <= 5 && cur !in excluded) {
            days.add(cur)
        }
        cur = cur.plus(1, DateTimeUnit.DAY)
    }

    return year to days
}
