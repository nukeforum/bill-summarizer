package com.informedcitizen.data.util

/**
 * Maps a normalized bill type code (lowercase, e.g. "hr", "s", "hjres") to its
 * congress.gov URL slug. Used when constructing public-facing URLs for bills
 * we don't have a local cache entry for.
 *
 * MIRROR: This mapping is duplicated in `data-pipeline/scripts/_common.py` as
 * `_BILL_TYPE_TO_SLUG`. Keep both in sync if Congress.gov ever adds a new
 * bill type. The cross-language duplication is intentional — the Python side
 * uses it during fetching; the Kotlin side uses it for client-side fallbacks.
 */
fun billTypeToCongressGovSlug(type: String): String = when (type.lowercase()) {
    "hr" -> "house-bill"
    "s" -> "senate-bill"
    "hjres" -> "house-joint-resolution"
    "sjres" -> "senate-joint-resolution"
    "hconres" -> "house-concurrent-resolution"
    "sconres" -> "senate-concurrent-resolution"
    "hres" -> "house-resolution"
    "sres" -> "senate-resolution"
    else -> "bill"
}

fun congressGovUrlFor(type: String, number: String, congress: Int): String {
    val slug = billTypeToCongressGovSlug(type)
    return "https://www.congress.gov/bill/${congress}th-congress/$slug/$number"
}
