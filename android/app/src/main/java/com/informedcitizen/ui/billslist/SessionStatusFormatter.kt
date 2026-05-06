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

    return when {
        house.inSessionToday && senate.inSessionToday ->
            "House and Senate in session today"

        house.inSessionToday && senate.nextSessionDay != null ->
            "House in session — Senate returns ${senate.nextSessionDay.format(DOW_MON_DAY)}"

        senate.inSessionToday && house.nextSessionDay != null ->
            "Senate in session — House returns ${house.nextSessionDay.format(DOW_MON_DAY)}"

        !house.inSessionToday && !senate.inSessionToday &&
            house.nextSessionDay != null && senate.nextSessionDay != null ->
            if (house.nextSessionDay == senate.nextSessionDay) {
                "Both chambers on recess — they return ${house.nextSessionDay.format(DOW_MON_DAY)}"
            } else {
                "Both chambers on recess — House returns ${house.nextSessionDay.format(MON_DAY)}, " +
                    "Senate ${senate.nextSessionDay.format(MON_DAY)}"
            }

        else -> null
    }
}
