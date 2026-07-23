package com.informedcitizen.data.api

import com.informedcitizen.pipeline.model.BillShardIndex
import com.informedcitizen.pipeline.model.BillsManifest
import com.informedcitizen.pipeline.model.CongressesIndex
import com.informedcitizen.pipeline.model.ElectionCalendar
import com.informedcitizen.pipeline.model.SessionCalendar
import retrofit2.http.GET
import retrofit2.http.Url

interface BillsApi {
    @GET("data/congresses.json")
    suspend fun getCongressesIndex(): CongressesIndex

    @GET
    suspend fun getBillsManifest(@Url url: String): BillsManifest

    /**
     * Fetch the per-Congress sharded bills index (issue #40) at the
     * [com.informedcitizen.pipeline.model.CongressEntry.shardIndexPath]
     * URL. Each shard the index lists is itself a [BillsManifest], fetched
     * via [getBillsManifest] — no separate shard endpoint is needed.
     */
    @GET
    suspend fun getBillShardIndex(@Url url: String): BillShardIndex

    @GET("data/session_calendar.json")
    suspend fun getSessionCalendar(): SessionCalendar

    @GET("data/election_calendar.json")
    suspend fun getElectionCalendar(): ElectionCalendar
}
