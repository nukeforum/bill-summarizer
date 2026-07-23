package com.informedcitizen.data.byok

import com.informedcitizen.pipeline.fetch.LIST_PAGES_MAX
import com.informedcitizen.pipeline.fetch.RECENT_DAYS

/**
 * The BYOK bill-coverage contract (backlog #43).
 *
 * Post-#39 a single Congress holds ~10,000+ bills once pre-floor
 * lifecycle statuses (introduced / in committee / reported) stop being
 * dropped. Direct on-device enrichment of that whole set — three
 * Congress.gov GETs per bill (detail / summaries / text, see
 * [com.informedcitizen.pipeline.fetch.buildBillRecord]) — cannot
 * complete inside one key's 5,000 requests/hour budget. So BYOK
 * deliberately does NOT mirror the full broadened set on the device.
 * Its contract is:
 *
 *  - **Bounded direct fetch.** BYOK enriches only bills whose latest
 *    action falls inside the [BYOK_BILL_WINDOW_DAYS]-day recency window
 *    (the same cutoff the shared `fetchBills` already applies). That is
 *    the freshness-critical slice a user's own key actually buys them.
 *  - **Breadth from the published shards.** Coverage beyond the window
 *    comes from the project-published sharded manifests (#40) over the
 *    app's normal read path — no key required.
 *  - **Per-run cap + incremental resume.** The window fetch is capped
 *    per run at [byokMaxBillsPerRun] and resumes across daily ticks (the
 *    same pattern the BYOK votes step already uses), so even a first
 *    sync of the broadened set never exceeds one key's hourly budget.
 *
 * The UI states this bound ([BYOK_BILLS_COVERAGE_STATEMENT]) so a BYOK
 * user is never shown a partial record as if it were the complete one.
 *
 * This file is the pure, testable core of that contract. Wiring the
 * per-run cap into [ByokFetchOrchestrator.fetchBills] (via a shared
 * `maxNew` parameter + a fetch cursor, mirroring the votes step) is a
 * separate slice.
 */

/** Congress.gov's per-key request budget: 5,000 requests per hour. */
const val CONGRESS_HOURLY_REQUEST_BUDGET: Int = 5_000

/**
 * Congress.gov GETs issued per enriched bill: bill detail, CRS
 * summaries, and text-format URLs — the three sequential calls in
 * [com.informedcitizen.pipeline.fetch.buildBillRecord].
 */
const val REQUESTS_PER_BILL: Int = 3

/**
 * The recency window (in days) BYOK direct-fetches. Mirrors the shared
 * `fetchBills` cutoff ([RECENT_DAYS]) so the bound the UI states matches
 * the data the orchestrator actually pulls.
 */
const val BYOK_BILL_WINDOW_DAYS: Int = RECENT_DAYS

/**
 * The number of Congress.gov requests one bounded sync run issues: the
 * paginated list walk ([listPages] requests) plus [REQUESTS_PER_BILL]
 * enrichment GETs for each bill actually fetched.
 */
fun byokBillSyncRequestEstimate(
    billsEnriched: Int,
    listPages: Int = LIST_PAGES_MAX,
): Int {
    require(billsEnriched >= 0) { "billsEnriched must be >= 0, was $billsEnriched" }
    require(listPages >= 0) { "listPages must be >= 0, was $listPages" }
    return listPages + billsEnriched * REQUESTS_PER_BILL
}

/**
 * The most bills BYOK may enrich in one run while staying within
 * [budget], reserving [listPages] requests for the list walk. Never
 * negative (a budget too small to cover even the list walk yields 0).
 * This is the per-run cap the bounded-fetch slice will hand to the
 * shared fetcher.
 */
fun byokMaxBillsPerRun(
    budget: Int = CONGRESS_HOURLY_REQUEST_BUDGET,
    listPages: Int = LIST_PAGES_MAX,
): Int {
    val forEnrichment = budget - listPages
    if (forEnrichment <= 0) return 0
    return forEnrichment / REQUESTS_PER_BILL
}

/**
 * True when a bounded sync run enriching [billsEnriched] bills fits
 * inside one key's hourly [budget].
 */
fun byokSyncFitsBudget(
    billsEnriched: Int,
    budget: Int = CONGRESS_HOURLY_REQUEST_BUDGET,
): Boolean = byokBillSyncRequestEstimate(billsEnriched) <= budget

/**
 * The user-facing coverage statement for the Data sources screen, so a
 * BYOK user knows their direct fetch is bounded to the recency window
 * and that older bills come from the app's published data.
 */
const val BYOK_BILLS_COVERAGE_STATEMENT: String =
    "Your key fetches bills active in the last $BYOK_BILL_WINDOW_DAYS days directly. " +
        "Older bills come from the app's published data, so your record stays complete."
