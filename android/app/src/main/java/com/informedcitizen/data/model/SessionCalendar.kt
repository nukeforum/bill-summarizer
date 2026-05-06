package com.informedcitizen.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionCalendar(
    @SerialName("generated_at") val generatedAt: String,
    val source: SessionCalendarSource,
    val chambers: Map<String, ChamberCalendar>,
)

@Serializable
data class SessionCalendarSource(
    val house: String,
    val senate: String,
)

@Serializable
data class ChamberCalendar(
    @SerialName("session_days") val sessionDays: List<String>,
)
