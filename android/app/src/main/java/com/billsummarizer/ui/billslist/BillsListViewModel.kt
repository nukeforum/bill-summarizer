package com.billsummarizer.ui.billslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.billsummarizer.data.model.Bill
import com.billsummarizer.data.repository.BillRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BillsListViewModel @Inject constructor(
    private val billRepository: BillRepository,
) : ViewModel() {

    private val billsResult = MutableStateFlow<Result<List<Bill>>?>(null)
    private val filter = MutableStateFlow(BillsListFilter.ALL)
    private val isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<BillsListUiState> =
        combine(billsResult, filter, isRefreshing) { result, currentFilter, refreshing ->
            when {
                result == null -> BillsListUiState.Loading
                result.isSuccess -> BillsListUiState.Success(
                    bills = result.getOrThrow().filter(currentFilter::matches),
                    filter = currentFilter,
                    isRefreshing = refreshing,
                )
                else -> BillsListUiState.Error(
                    message = result.exceptionOrNull()?.localizedMessage
                        ?: "Couldn't load bills",
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BillsListUiState.Loading)

    init {
        load(forceRefresh = false)
    }

    fun refresh() = load(forceRefresh = true)

    fun setFilter(newFilter: BillsListFilter) {
        filter.value = newFilter
    }

    private fun load(forceRefresh: Boolean) {
        viewModelScope.launch {
            isRefreshing.value = forceRefresh
            billsResult.value = billRepository.getBills(forceRefresh = forceRefresh)
            isRefreshing.value = false
        }
    }
}
