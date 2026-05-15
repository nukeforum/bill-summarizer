package com.informedcitizen.pipeline.fetch

/**
 * Congress.gov bill-type code → URL slug. Mirrors Python
 * `_common._BILL_TYPE_TO_SLUG`. Unknown bill types fall through to the
 * raw code, matching Python's `.get(bill_type, bill_type)`.
 */
private val BILL_TYPE_TO_SLUG: Map<String, String> = mapOf(
    "hr" to "house-bill",
    "s" to "senate-bill",
    "hjres" to "house-joint-resolution",
    "sjres" to "senate-joint-resolution",
    "hconres" to "house-concurrent-resolution",
    "sconres" to "senate-concurrent-resolution",
    "hres" to "house-resolution",
    "sres" to "senate-resolution",
)

fun congressGovUrl(congress: Int, billType: String, billNumber: String): String {
    val slug = BILL_TYPE_TO_SLUG[billType] ?: billType
    return "https://www.congress.gov/bill/${congress}th-congress/$slug/$billNumber"
}
