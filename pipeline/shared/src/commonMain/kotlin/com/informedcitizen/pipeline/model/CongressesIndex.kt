package com.informedcitizen.pipeline.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CongressesIndex(
    @SerialName("current_congress") val currentCongress: Int,
    val congresses: List<CongressEntry>,
)

@Serializable
data class CongressEntry(
    val congress: Int,
    @SerialName("manifest_path") val manifestPath: String,
    @SerialName("is_current") val isCurrent: Boolean = false,
)
