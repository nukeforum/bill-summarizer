package com.informedcitizen.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Outcome {
    @SerialName("passed_house") PASSED_HOUSE,
    @SerialName("passed_senate") PASSED_SENATE,
    @SerialName("enacted") ENACTED,
    @SerialName("vetoed") VETOED,
    @SerialName("failed") FAILED,
}
