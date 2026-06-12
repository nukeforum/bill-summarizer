package com.informedcitizen

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Root : NavKey

@Serializable data class BillDetail(val billId: String) : NavKey

@Serializable data object Settings : NavKey

@Serializable data object CongressCalendar : NavKey

@Serializable data object DataSources : NavKey

@Serializable data class MemberDetail(val bioguideId: String) : NavKey
