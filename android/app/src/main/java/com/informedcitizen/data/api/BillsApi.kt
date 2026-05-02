package com.informedcitizen.data.api

import com.informedcitizen.data.model.BillsManifest
import retrofit2.http.GET

interface BillsApi {
    @GET("data/bills.json")
    suspend fun getBills(): BillsManifest
}
