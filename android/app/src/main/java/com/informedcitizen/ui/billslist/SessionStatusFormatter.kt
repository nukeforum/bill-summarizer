package com.informedcitizen.ui.billslist

import com.informedcitizen.domain.session.Chamber
import com.informedcitizen.domain.session.ChamberStatus
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DOW_MON_DAY: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
private val MON_DAY: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d", Locale.US)

fun formatSessionStatusLine(statuses: List<ChamberStatus>): String? {
    val house = statuses.first { it.chamber == Chamber.HOUSE }
    val senate = statuses.first { it.chamber == Chamber.SENATE }

    val houseNext = house.nextSessionDay
    val senateNext = senate.nextSessionDay

    return when {
        house.inSessionToday && senate.inSessionToday ->
            "House and Senate in session today"

        house.inSessionToday && senateNext != null ->
            "House in session — Senate returns ${senateNext.format(DOW_MON_DAY)}"

        senate.inSessionToday && houseNext != null ->
            "Senate in session — House returns ${houseNext.format(DOW_MON_DAY)}"

        !house.inSessionToday && !senate.inSessionToday &&
            houseNext != null && senateNext != null ->
            if (houseNext == senateNext) {
                "Both chambers on recess — they return ${houseNext.format(DOW_MON_DAY)}"
            } else {
                "Both chambers on recess — House returns ${houseNext.format(MON_DAY)}, " +
                    "Senate ${senateNext.format(MON_DAY)}"
            }

        else -> null
    }
}
