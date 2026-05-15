package com.informedcitizen.pipeline.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MemberLegislation(
    @SerialName("bioguide_id") val bioguideId: String,
    val congress: Int,
    val kind: String,
    @SerialName("generated_at") val generatedAt: String,
    val bills: List<MemberLegislationItem>,
)
