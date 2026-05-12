package com.informedcitizen.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MembersIndex(
    val congress: Int,
    @SerialName("generated_at") val generatedAt: String,
    val members: List<Member>,
)
