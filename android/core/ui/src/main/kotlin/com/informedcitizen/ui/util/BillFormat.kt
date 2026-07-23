package com.informedcitizen.ui.util

import com.informedcitizen.pipeline.model.LifecycleStatus
import com.informedcitizen.pipeline.model.Outcome
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val displayDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

fun formatBillRef(type: String, number: String): String = when (type.lowercase()) {
    "hr" -> "H.R. $number"
    "s" -> "S. $number"
    "hjres" -> "H.J.Res. $number"
    "sjres" -> "S.J.Res. $number"
    "hconres" -> "H.Con.Res. $number"
    "sconres" -> "S.Con.Res. $number"
    "hres" -> "H.Res. $number"
    "sres" -> "S.Res. $number"
    else -> "${type.uppercase()} $number"
}

fun formatDate(iso: String): String = try {
    LocalDate.parse(iso).format(displayDateFormatter)
} catch (_: DateTimeParseException) {
    iso
}

fun Outcome.displayName(): String = when (this) {
    Outcome.PASSED_HOUSE -> "Passed House"
    Outcome.PASSED_SENATE -> "Passed Senate"
    Outcome.ENACTED -> "Enacted"
    Outcome.VETOED -> "Vetoed"
    Outcome.FAILED -> "Failed"
    Outcome.UNKNOWN -> "Unknown"
}

fun LifecycleStatus.displayName(): String = when (this) {
    LifecycleStatus.INTRODUCED -> "Introduced"
    LifecycleStatus.IN_COMMITTEE -> "In committee"
    LifecycleStatus.REPORTED -> "Reported"
    LifecycleStatus.UNKNOWN -> "Unknown"
}
