package com.informedcitizen.data.work

interface BillSummarizationController {
    fun start()
    fun retry(billId: String)
    fun stopNow()
    fun clearCache()
}
