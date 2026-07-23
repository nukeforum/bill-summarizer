package com.informedcitizen.ui.reps

import com.informedcitizen.domain.search.matchesSearchQuery
import com.informedcitizen.pipeline.model.Member
import com.informedcitizen.pipeline.model.MemberLegislationItem
import com.informedcitizen.pipeline.model.MemberVoteRow

data class MemberDetailUiState(
    val isLoading: Boolean = true,
    val member: Member? = null,
    val sponsored: List<MemberLegislationItem> = emptyList(),
    val cosponsored: List<MemberLegislationItem> = emptyList(),
    /** Recent recorded roll-call positions, newest first (issue #22). */
    val recentVotes: List<MemberVoteRow> = emptyList(),
    /** "Up for election · Nov 3, 2026" badge (issue #33); null hides it. */
    val ballotBadge: String? = null,
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
