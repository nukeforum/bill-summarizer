package com.informedcitizen.ui.reps

import com.informedcitizen.data.model.Member
import com.informedcitizen.data.model.MemberLegislationItem

data class MemberDetailUiState(
    val isLoading: Boolean = true,
    val member: Member? = null,
    val sponsored: List<MemberLegislationItem> = emptyList(),
    val cosponsored: List<MemberLegislationItem> = emptyList(),
    val errorMessage: String? = null,
)
