package com.informedcitizen.data.work

import com.informedcitizen.pipeline.model.Action
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.Outcome
import com.informedcitizen.pipeline.model.Sponsor
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BillSummarizationPolicyTest {

    private val today = LocalDate.of(2026, 5, 8)

    @Test fun `FloorActionOnly keeps every bill in the manifest (all current outcomes are passage outcomes)`() {
        val policy = BillSummarizationPolicy(SummarizationScope.FloorActionOnly, today)
        val picks = policy.selectToEnqueue(
            bills = listOf(
                bill("a", outcome = Outcome.PASSED_HOUSE),
                bill("b", outcome = Outcome.PASSED_SENATE),
                bill("c", outcome = Outcome.ENACTED),
                bill("d", outcome = Outcome.FAILED),
                bill("e", outcome = Outcome.VETOED),
            ),
            cached = emptySet(),
        )
        assertEquals(setOf("a", "b", "c", "d", "e"), picks.map { it.id }.toSet())
    }

    @Test fun `Recent60Days keeps bills whose latestAction is within 60 days inclusive`() {
        val policy = BillSummarizationPolicy(SummarizationScope.Recent60Days, today)
        val picks = policy.selectToEnqueue(
            bills = listOf(
                bill("today", actionDate = today),
                bill("60d", actionDate = today.minusDays(60)),
                bill("61d", actionDate = today.minusDays(61)),
                bill("future", actionDate = today.plusDays(5)),
            ),
            cached = emptySet(),
        )
        assertEquals(setOf("today", "60d", "future"), picks.map { it.id }.toSet())
    }

    @Test fun `Progressive returns all uncached bills oldest-first`() {
        val policy = BillSummarizationPolicy(SummarizationScope.Progressive(50), today)
        val picks = policy.selectToEnqueue(
            bills = listOf(
                bill("new", introducedDate = LocalDate.of(2026, 4, 1)),
                bill("old", introducedDate = LocalDate.of(2025, 1, 1)),
                bill("mid", introducedDate = LocalDate.of(2025, 12, 1)),
            ),
            cached = emptySet(),
        )
        assertEquals(listOf("old", "mid", "new"), picks.map { it.id })
    }

    @Test fun `cached bills are excluded`() {
        val policy = BillSummarizationPolicy(SummarizationScope.All, today)
        val picks = policy.selectToEnqueue(
            bills = listOf(bill("a"), bill("b"), bill("c")),
            cached = setOf("b"),
        )
        assertEquals(setOf("a", "c"), picks.map { it.id }.toSet())
    }

    private fun bill(
        id: String,
        outcome: Outcome = Outcome.PASSED_HOUSE,
        actionDate: LocalDate = LocalDate.of(2026, 5, 1),
        introducedDate: LocalDate = LocalDate.of(2026, 1, 1),
    ): Bill = Bill(
        id = id,
        congress = 119,
        type = "hr",
        number = "1",
        title = "Title for $id",
        shortTitle = null,
        sponsor = Sponsor(name = "Sponsor", party = "D", state = "CA"),
        introducedDate = introducedDate.toString(),
        latestAction = Action(date = actionDate.toString(), text = "Action"),
        outcome = outcome,
        summaryCrs = null,
        textUrlHtml = null,
        textUrlXml = null,
        textUrlPdf = null,
        congressGovUrl = "https://congress.gov/$id",
    )
}
