package com.informedcitizen.ui.billslist

import com.informedcitizen.data.ai.BillTopic
import com.informedcitizen.pipeline.model.Outcome
import com.informedcitizen.ui.components.BillCardSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillsListItemFilterTest {

    private fun summary(topic: BillTopic?) = BillCardSummary(generatedTitle = null, topic = topic)

    @Test fun `default state hides nothing`() {
        val bill = billFixture("a", outcome = Outcome.FAILED)
        assertTrue(
            billMatchesListFilters(
                bill = bill,
                filter = BillsListFilter.ALL,
                query = "",
                activeTopic = null,
                summaries = emptyMap(),
            ),
        )
    }

    @Test fun `outcome filter excludes non-matching outcome`() {
        val passed = billFixture("a", outcome = Outcome.PASSED_HOUSE)
        val failed = billFixture("b", outcome = Outcome.FAILED)
        assertTrue(passed.matchesFilter(BillsListFilter.PASSED))
        assertFalse(failed.matchesFilter(BillsListFilter.PASSED))
    }

    @Test fun `search query must match every term across some field`() {
        val bill = billFixture("a", title = "Clean Water Act", policyArea = "Environment")
        assertTrue(bill.matchesQuery("water environment"))
        assertFalse(bill.matchesQuery("water taxation"))
    }

    @Test fun `topic filter keeps only bills whose summary matches`() {
        val bill = billFixture("a")
        val summaries = mapOf("a" to summary(BillTopic.Tech))
        assertTrue(
            billMatchesListFilters(bill, BillsListFilter.ALL, "", BillTopic.Tech, summaries),
        )
        assertFalse(
            billMatchesListFilters(bill, BillsListFilter.ALL, "", BillTopic.Healthcare, summaries),
        )
    }

    @Test fun `topic filter excludes bills with no summary entry`() {
        val bill = billFixture("a")
        assertFalse(
            billMatchesListFilters(bill, BillsListFilter.ALL, "", BillTopic.Tech, emptyMap()),
        )
    }

    @Test fun `null topic ignores the summary map`() {
        val bill = billFixture("a")
        assertTrue(
            billMatchesListFilters(bill, BillsListFilter.ALL, "", null, mapOf("a" to summary(BillTopic.Tech))),
        )
    }

    @Test fun `all active clauses are ANDed`() {
        // Matches outcome and topic but not the search term.
        val bill = billFixture("a", title = "Clean Water Act", outcome = Outcome.PASSED_HOUSE)
        val summaries = mapOf("a" to summary(BillTopic.Tech))
        assertFalse(
            billMatchesListFilters(bill, BillsListFilter.PASSED, "taxation", BillTopic.Tech, summaries),
        )
        assertTrue(
            billMatchesListFilters(bill, BillsListFilter.PASSED, "water", BillTopic.Tech, summaries),
        )
    }

    private fun com.informedcitizen.pipeline.model.Bill.matchesFilter(filter: BillsListFilter) =
        billMatchesListFilters(this, filter, "", null, emptyMap())

    private fun com.informedcitizen.pipeline.model.Bill.matchesQuery(query: String) =
        billMatchesListFilters(this, BillsListFilter.ALL, query, null, emptyMap())
}
