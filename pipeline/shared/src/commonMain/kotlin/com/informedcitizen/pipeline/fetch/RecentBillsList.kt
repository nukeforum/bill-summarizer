package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.http.CongressClient
import com.informedcitizen.pipeline.state.LIST_PAGE_LIMIT
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Default page-cap when iterating the recent-bills list. Mirrors Python `LIST_PAGES_MAX`. */
const val LIST_PAGES_MAX: Int = 8

/**
 * Paginated walk of `/bill/{congress}` sorted by `updateDate desc`,
 * filtered to actions on or after [cutoff]. Stops when a page returns
 * fewer than [LIST_PAGE_LIMIT] items (or zero). Returns the
 * concatenated bill summaries from every page consumed.
 *
 * Mirrors Python `fetch_bills.list_recent_bills`.
 */
suspend fun listRecentBills(
    client: CongressClient,
    congress: Int,
    cutoff: Instant,
    maxPages: Int = LIST_PAGES_MAX,
): List<JsonObject> {
    val fromDt = Instant.fromEpochSeconds(cutoff.epochSeconds).toString()
    val collected = mutableListOf<JsonObject>()
    for (page in 0 until maxPages) {
        val offset = page * LIST_PAGE_LIMIT
        val body = client.get(
            path = "/bill/$congress",
            params = mapOf(
                "limit" to LIST_PAGE_LIMIT.toString(),
                "offset" to offset.toString(),
                "sort" to "updateDate desc",
                "fromDateTime" to fromDt,
            ),
        )
        val bills = (body["bills"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        if (bills.isEmpty()) return collected
        collected += bills
        if (bills.size < LIST_PAGE_LIMIT) return collected
    }
    return collected
}
