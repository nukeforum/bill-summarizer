package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.http.CongressClient
import com.informedcitizen.pipeline.state.LIST_PAGE_LIMIT
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Fetch a single bill-list page from `/bill/{congress}` at the given
 * offset. Used by the backfill orchestrator which advances its own
 * cursor across runs; distinct from [listRecentBills] which paginates
 * eagerly with a `fromDateTime` cutoff. Mirrors Python
 * `backfill_bills.list_congress_page`.
 */
suspend fun listCongressPage(
    client: CongressClient,
    congress: Int,
    offset: Int,
    sort: String = "updateDate asc",
): List<JsonObject> {
    val body = client.get(
        path = "/bill/$congress",
        params = mapOf(
            "limit" to LIST_PAGE_LIMIT.toString(),
            "offset" to offset.toString(),
            "sort" to sort,
        ),
    )
    return (body["bills"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
}
