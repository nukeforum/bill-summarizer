package com.informedcitizen.data.work

import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.Outcome
import java.time.LocalDate

class BillSummarizationPolicy(
    private val scope: SummarizationScope,
    private val today: LocalDate,
) {
    fun selectToEnqueue(bills: List<Bill>, cached: Set<String>): List<Bill> {
        val candidates = bills.filterNot { it.id in cached }
        return when (scope) {
            SummarizationScope.FloorActionOnly ->
                candidates.filter { it.outcome.isPassageOutcome() }

            SummarizationScope.Recent60Days ->
                candidates.filter { isWithin60Days(it.latestAction.date) }

            is SummarizationScope.Progressive ->
                candidates.sortedBy { it.introducedDate }

            SummarizationScope.All -> candidates
        }
    }

    private fun isWithin60Days(actionIsoDate: String): Boolean {
        val date = runCatching { LocalDate.parse(actionIsoDate) }.getOrNull() ?: return false
        val daysAgo = java.time.temporal.ChronoUnit.DAYS.between(date, today)
        return daysAgo in -3650..60
    }

    private fun Outcome.isPassageOutcome(): Boolean = when (this) {
        Outcome.PASSED_HOUSE,
        Outcome.PASSED_SENATE,
        Outcome.ENACTED,
        Outcome.VETOED,
        Outcome.FAILED -> true
    }
}
