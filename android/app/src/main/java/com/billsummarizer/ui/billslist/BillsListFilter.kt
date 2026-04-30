package com.billsummarizer.ui.billslist

import com.billsummarizer.data.model.Bill
import com.billsummarizer.data.model.Outcome

enum class BillsListFilter(val displayName: String) {
    ALL("All"),
    PASSED("Passed"),
    FAILED("Failed"),
    ENACTED("Enacted"),
    VETOED("Vetoed");

    fun matches(bill: Bill): Boolean = when (this) {
        ALL -> true
        PASSED -> bill.outcome == Outcome.PASSED_HOUSE || bill.outcome == Outcome.PASSED_SENATE
        FAILED -> bill.outcome == Outcome.FAILED
        ENACTED -> bill.outcome == Outcome.ENACTED
        VETOED -> bill.outcome == Outcome.VETOED
    }
}
