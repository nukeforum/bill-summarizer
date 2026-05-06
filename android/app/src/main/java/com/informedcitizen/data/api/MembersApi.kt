package com.informedcitizen.data.api

import com.informedcitizen.data.model.MemberLegislation
import com.informedcitizen.data.model.MembersIndex
import retrofit2.http.GET
import retrofit2.http.Path

interface MembersApi {
    @GET("data/members_{congress}.json")
    suspend fun getMembersIndex(@Path("congress") congress: String): MembersIndex

    @GET("data/members/{bioguideId}_sponsored.json")
    suspend fun getSponsored(@Path("bioguideId") bioguideId: String): MemberLegislation

    @GET("data/members/{bioguideId}_cosponsored.json")
    suspend fun getCosponsored(@Path("bioguideId") bioguideId: String): MemberLegislation
}
