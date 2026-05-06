package com.informedcitizen.data.api

import com.informedcitizen.data.model.BillsManifest
import com.informedcitizen.data.model.SessionCalendar
import retrofit2.http.GET

interface BillsApi {
    @GET("data/bills.json")
    suspend fun getBills(): BillsManifest

    @GET("data/session_calendar.json")
    suspend fun getSessionCalendar(): SessionCalendar
}
