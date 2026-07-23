package com.informedcitizen.ui.billslist

import com.informedcitizen.data.ai.BillTopic
import com.informedcitizen.domain.search.matchesSearchQuery
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.ui.components.BillCardSummary

/**
 * The per-bill predicate that narrows the recency-ordered list by the three
 * filters that can't be pushed into the SQL paging query (issue #41):
 *
 * 1. the outcome chips ([BillsListFilter]) — there is no extracted `outcome`
 *    column, and the SQL `status` column carries the *lifecycle* status
 *    (introduced/in_committee/reported), not the passage outcome;
 * 2. the free-text search box — [Bill.matchesSearchQuery] is multi-field,
 *    multi-term substring matching, which SQLite FTS can't reproduce without
 *    changing the match semantics (FTS is word/prefix-based, not `contains`);
 * 3. the on-device AI topic classification — [activeTopic] resolves against the
 *    summary cache, which is not part of the manifest/DB row.
 *
 * `policyArea` is deliberately *absent* here: it is an extracted column and
 * already filters in SQL ([selectBillsPaged]). This function is the seam the
 * paging switch will apply over the SQL-paged `Flow<PagingData<Bill>>` via
 * `PagingData.filter`, so the three list-only filters are defined and tested
 * once, independent of whether the list is materialised in memory or paged.
 *
 * All clauses are ANDed: a bill is visible only when it clears every active
 * filter. A [BillsListFilter.ALL] filter, a blank [query], and a null
 * [activeTopic] each pass everything, so the default state hides nothing.
 */
internal fun billMatchesListFilters(
    bill: Bill,
    filter: BillsListFilter,
    query: String,
    activeTopic: BillTopic?,
    summaries: Map<String, BillCardSummary>,
): Boolean =
    filter.matches(bill) &&
        bill.matchesSearchQuery(query) &&
        (activeTopic == null || summaries[bill.id]?.topic == activeTopic)
