package com.informedcitizen.ui.billslist

import com.informedcitizen.data.ai.BillTopic
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.ui.components.BillCardSummary

sealed interface BillsListUiState {
    data object Loading : BillsListUiState

    data class Success(
        val bills: List<Bill>,
        val filter: BillsListFilter,
        val isRefreshing: Boolean,
        val sessionStatusLine: String? = null,
        val aiTitlesEnabled: Boolean = false,
        val deviceCapable: Boolean = false,
        val summaries: Map<String, BillCardSummary> = emptyMap(),
        val selectedTopic: BillTopic? = null,
        val hiddenByTopicCount: Int = 0,
    ) : BillsListUiState

    data class Error(val message: String) : BillsListUiState
}
