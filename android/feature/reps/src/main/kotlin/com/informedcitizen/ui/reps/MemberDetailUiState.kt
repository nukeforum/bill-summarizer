package com.informedcitizen.ui.reps

import com.informedcitizen.domain.search.matchesSearchQuery
import com.informedcitizen.pipeline.model.Member
import com.informedcitizen.pipeline.model.MemberLegislationItem

data class MemberDetailUiState(
    val isLoading: Boolean = true,
    val member: Member? = null,
    val sponsored: List<MemberLegislationItem> = emptyList(),
    val cosponsored: List<MemberLegislationItem> = emptyList(),
    val errorMessage: String? = null,
    val searchQuery: String = "",
) {
    val filteredSponsored: List<MemberLegislationItem>
        get() = filterByQuery(sponsored)

    val filteredCosponsored: List<MemberLegislationItem>
        get() = filterByQuery(cosponsored)

    private fun filterByQuery(items: List<MemberLegislationItem>): List<MemberLegislationItem> =
        if (searchQuery.isBlank()) items else items.filter { it.matchesSearchQuery(searchQuery) }
}
