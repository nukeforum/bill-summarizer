package com.informedcitizen.data.ai

import com.informedcitizen.data.model.Bill

interface BillSummarizer {
    suspend fun summarize(bill: Bill): Result

    sealed interface Result {
        data class Success(val summary: BillSummary) : Result
        data class Failure(val errorKind: String) : Result
    }
}
