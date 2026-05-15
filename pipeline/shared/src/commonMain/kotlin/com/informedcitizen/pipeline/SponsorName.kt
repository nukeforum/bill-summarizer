package com.informedcitizen.pipeline

private val PARTY_STATE_SUFFIX = Regex("""\s*\[[A-Z]+-[A-Z]{2}(?:-\d+)?]\s*$""")

fun cleanSponsorName(fullName: String): String =
    PARTY_STATE_SUFFIX.replace(fullName, "").trim()
