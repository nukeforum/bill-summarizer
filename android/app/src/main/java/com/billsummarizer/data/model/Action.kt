package com.billsummarizer.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Action(
    val date: String,
    val text: String,
)
