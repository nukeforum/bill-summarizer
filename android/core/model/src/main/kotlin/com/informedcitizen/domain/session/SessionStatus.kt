package com.informedcitizen.domain.session

import com.informedcitizen.pipeline.model.SessionCalendar
import java.time.LocalDate

enum class Chamber(val key: String) {
    HOUSE("house"),
    SENATE("senate"),
}

data class ChamberStatus(
    val chamber: Chamber,
    val inSessionToday: Boolean,
    val nextSessionDay: LocalDate?,
)

fun SessionCalendar.statusOn(date: LocalDate): List<ChamberStatus> =
    Chamber.entries.map { chamber ->
        val days = chambers[chamber.key]?.sessionDays.orEmpty()
            .map(LocalDate::parse)
        ChamberStatus(
            chamber = chamber,
            inSessionToday = date in days,
            nextSessionDay = days.firstOrNull { it > date },
        )
    }
