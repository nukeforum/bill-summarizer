package com.informedcitizen.pipeline.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Outcome {
    @SerialName("passed_house") PASSED_HOUSE,
    @SerialName("passed_senate") PASSED_SENATE,
    @SerialName("enacted") ENACTED,
    @SerialName("vetoed") VETOED,
    @SerialName("failed") FAILED,

    /**
     * Forward-compat fallback for any outcome string a future pipeline
     * publishes that this app generation doesn't recognise. It is the
     * default for [Bill.outcome], so the lenient wire config's
     * `coerceInputValues` maps an unknown value here instead of crashing
     * deserialization. The pipeline never emits `"unknown"`; classifier
     * misses drop the bill rather than publishing this value.
     */
    @SerialName("unknown") UNKNOWN,
}
