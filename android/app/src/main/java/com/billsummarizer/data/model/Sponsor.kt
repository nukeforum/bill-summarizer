package com.billsummarizer.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Sponsor(
    val name: String,
    val party: String,
    val state: String,
)
