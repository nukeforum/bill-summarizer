package com.billsummarizer

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object BillsList : NavKey

@Serializable data class BillDetail(val billId: String) : NavKey

@Serializable data object Settings : NavKey
