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
 * filter. A [BillsListFilter.ALL] filter, a blank [query], a null [activeTopic],
 * and a null [selectedSubject] each pass everything, so the default state hides
 * nothing.
 *
 * [selectedSubject] is the finer-grained legislative-subject facet (issue #10,
 * data from #28). Like the outcome/search/topic clauses it stays a post-SQL
 * predicate — `Bill.subjects` is a multi-value list with no extracted column, so
 * it can't be pushed into the paging query the way `policyArea` is.
 */
internal fun billMatchesListFilters(
    bill: Bill,
    filter: BillsListFilter,
    query: String,
    activeTopic: BillTopic?,
    summaries: Map<String, BillCardSummary>,
    selectedSubject: String? = null,
): Boolean =
    filter.matches(bill) &&
        bill.matchesSearchQuery(query) &&
        (activeTopic == null || summaries[bill.id]?.topic == activeTopic) &&
        (selectedSubject == null || bill.hasSubject(selectedSubject))

/**
 * Case-insensitive membership test against the bill's legislative subjects
 * (issue #10/#28). A subject picked from [distinctSubjects] always originates
 * from a bill's own list, but matching case-insensitively keeps the facet robust
 * to any casing drift in the published data.
 */
internal fun Bill.hasSubject(subject: String): Boolean =
    subjects.any { it.equals(subject, ignoreCase = true) }

/**
 * The distinct legislative subjects across [bills], sorted for a stable filter
 * facet (issue #10). Mirrors the `availablePolicyAreas` derivation: bills with
 * no subjects simply contribute nothing, so the facet is empty (and hidden)
 * until the #28 subject terms are populated in the feed.
 */
internal fun distinctSubjects(bills: List<Bill>): List<String> =
    bills.flatMap { it.subjects }
        .distinct()
        .sorted()
