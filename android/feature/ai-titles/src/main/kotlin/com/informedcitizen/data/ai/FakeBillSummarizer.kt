package com.informedcitizen.data.ai

import com.informedcitizen.data.model.Bill

class FakeBillSummarizer(
    private val forcedFailures: Map<String, String> = emptyMap(),
    private val defaultTopic: BillTopic = BillTopic.Other,
) : BillSummarizer {
    override suspend fun summarize(bill: Bill): BillSummarizer.Result {
        forcedFailures[bill.id]?.let { return BillSummarizer.Result.Failure(it) }
        val short = bill.title.split(" ").take(6).joinToString(" ").trim()
        val title = "Concise: $short".take(80).trimEnd()
        return BillSummarizer.Result.Success(BillSummary(title, defaultTopic))
    }
}
