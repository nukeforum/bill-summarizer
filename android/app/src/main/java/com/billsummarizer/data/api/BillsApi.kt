package com.billsummarizer.data.api

import com.billsummarizer.data.model.BillsManifest
import retrofit2.http.GET

interface BillsApi {
    @GET("data/bills.json")
    suspend fun getBills(): BillsManifest
}
