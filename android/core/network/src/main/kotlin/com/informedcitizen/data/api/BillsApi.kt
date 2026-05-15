package com.informedcitizen.data.api

import com.informedcitizen.pipeline.model.BillsManifest
import com.informedcitizen.pipeline.model.CongressesIndex
import com.informedcitizen.pipeline.model.SessionCalendar
import retrofit2.http.GET
import retrofit2.http.Url

interface BillsApi {
    @GET("data/congresses.json")
    suspend fun getCongressesIndex(): CongressesIndex

    @GET
    suspend fun getBillsManifest(@Url url: String): BillsManifest

    @GET("data/session_calendar.json")
    suspend fun getSessionCalendar(): SessionCalendar
}
