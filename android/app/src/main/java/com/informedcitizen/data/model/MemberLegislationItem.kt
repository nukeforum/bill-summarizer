package com.informedcitizen.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MemberLegislationItem(
    val id: String,
    val type: String,
    val number: String,
    val congress: Int,
    val title: String,
    @SerialName("introduced_date") val introducedDate: String,
    @SerialName("latest_action") val latestAction: Action,
    @SerialName("policy_area") val policyArea: String? = null,
)
