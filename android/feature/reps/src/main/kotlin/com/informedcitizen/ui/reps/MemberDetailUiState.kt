package com.informedcitizen.ui.reps

import com.informedcitizen.pipeline.model.Member
import com.informedcitizen.pipeline.model.MemberLegislationItem

data class MemberDetailUiState(
    val isLoading: Boolean = true,
    val member: Member? = null,
    val sponsored: List<MemberLegislationItem> = emptyList(),
    val cosponsored: List<MemberLegislationItem> = emptyList(),
    val errorMessage: String? = null,
)
