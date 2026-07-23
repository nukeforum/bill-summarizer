package com.informedcitizen.pipeline

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

/**
 * The statutory U.S. federal general-election date for a given even year:
 * the Tuesday after the first Monday in November (2 U.S.C. §7, 3 U.S.C.
 * §1). This is deterministic and needs no curated source — the pipeline
 * computes it rather than trusting a data file, so the highest-value date
 * on the calendar is never wrong.
 *
 * Federal general elections fall only in even years; passing an odd [year]
 * still returns that year's Tuesday-after-first-Monday, so callers that
 * care about the federal cycle should gate on `year % 2 == 0` themselves
 * (see [nextFederalGeneralElectionYear]).
 */
fun federalGeneralElectionDate(year: Int): LocalDate {
    val november1 = LocalDate(year, 11, 1)
    // Days from Nov 1 forward to the first Monday (0 if Nov 1 is itself Monday).
    val daysToFirstMonday = (DayOfWeek.MONDAY.isoDayNumber - november1.dayOfWeek.isoDayNumber + 7) % 7
    // Election day is the very next day: the Tuesday after that first Monday.
    return november1.plus(DatePeriod(days = daysToFirstMonday + 1))
}

/**
 * The even-numbered year of the next federal general election on or after
 * [from]. If [from] falls in an even year but past that year's election
 * day, the following even year is returned. Used to seed the election
 * calendar's horizon so it always looks ahead to a real upcoming date.
 */
fun nextFederalGeneralElectionYear(from: LocalDate): Int {
    var year = if (from.year % 2 == 0) from.year else from.year + 1
    if (from > federalGeneralElectionDate(year)) year += 2
    return year
}
