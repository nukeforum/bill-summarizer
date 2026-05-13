package com.informedcitizen.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Member(
    @SerialName("bioguide_id") val bioguideId: String,
    val name: String,
    val party: String,
    val state: String,
    val district: Int? = null,
    val chamber: String,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("official_url") val officialUrl: String? = null,
    @SerialName("sponsored_count") val sponsoredCount: Int = 0,
    @SerialName("cosponsored_count") val cosponsoredCount: Int = 0,
    val address: String? = null,
    val phone: String? = null,
    @SerialName("contact_form") val contactForm: String? = null,
    val website: String? = null,
)
