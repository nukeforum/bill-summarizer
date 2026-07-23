package com.informedcitizen.ui.billslist

import com.informedcitizen.data.ai.BillTopic
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.ui.components.BillCardSummary
import java.time.Instant

sealed interface BillsListUiState {
    data object Loading : BillsListUiState

    data class Success(
        val bills: List<Bill>,
        val filter: BillsListFilter,
        val isRefreshing: Boolean,
        val sessionStatusLine: String? = null,
        /** Manifest `generated_at`; null hides the freshness line entirely. */
        val dataGeneratedAt: Instant? = null,
        val aiTitlesEnabled: Boolean = false,
        val deviceCapable: Boolean = false,
        val summaries: Map<String, BillCardSummary> = emptyMap(),
        val selectedTopic: BillTopic? = null,
        val hiddenByTopicCount: Int = 0,
        val searchQuery: String = "",
        val availablePolicyAreas: List<String> = emptyList(),
        val selectedPolicyArea: String? = null,
        /**
         * The finer-grained legislative subjects (#10/#28) present across the
         * loaded bills, sorted; empty (facet hidden) until the feed carries
         * subject terms. Distinct from [availablePolicyAreas]: `policyArea` is a
         * single coarse value, `subjects` are multi-value topical tags.
         */
        val availableSubjects: List<String> = emptyList(),
        val selectedSubject: String? = null,
        val selectedStatus: BillStatusFilter = BillStatusFilter.ALL,
        /**
         * Whether any loaded bill carries a pre-floor lifecycle status (#39/#42).
         * Drives whether the status filter row renders — dormant-invisible until
         * pre-floor bills are published, so it never shows dead chips on today's
         * floor-outcome-only feed (mirrors [availablePolicyAreas] degradation).
         */
        val statusFilterAvailable: Boolean = false,
    ) : BillsListUiState

    data class Error(val message: String) : BillsListUiState
}
