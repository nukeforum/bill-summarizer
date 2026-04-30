package com.billsummarizer.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BillsManifest(
    @SerialName("generated_at") val generatedAt: String,
    val congress: Int,
    val bills: List<Bill>,
)
