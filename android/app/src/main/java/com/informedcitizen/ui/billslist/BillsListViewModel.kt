package com.informedcitizen.ui.billslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.informedcitizen.data.model.Bill
import com.informedcitizen.data.repository.BillRepository
import com.informedcitizen.data.repository.SessionCalendarRepository
import com.informedcitizen.domain.session.statusOn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class BillsListViewModel @Inject constructor(
    private val billRepository: BillRepository,
    private val sessionCalendarRepository: SessionCalendarRepository,
) : ViewModel() {

    private val billsResult = MutableStateFlow<Result<List<Bill>>?>(null)
    private val filter = MutableStateFlow(BillsListFilter.ALL)
    private val isRefreshing = MutableStateFlow(false)
    private val sessionStatusLine = MutableStateFlow<String?>(null)

    val uiState: StateFlow<BillsListUiState> =
        combine(billsResult, filter, isRefreshing, sessionStatusLine) {
            result, currentFilter, refreshing, statusLine ->
            when {
                result == null -> BillsListUiState.Loading
                result.isSuccess -> BillsListUiState.Success(
                    bills = result.getOrThrow().filter(currentFilter::matches),
                    filter = currentFilter,
                    isRefreshing = refreshing,
                    sessionStatusLine = statusLine,
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
            coroutineScope {
                val billsDeferred = async {
                    billRepository.getBills(forceRefresh = forceRefresh)
                }
                val calendarDeferred = async {
                    sessionCalendarRepository.getCalendar(forceRefresh = forceRefresh)
                }
                billsResult.value = billsDeferred.await()
                val today = LocalDate.now()
                sessionStatusLine.value = calendarDeferred.await()
                    .getOrNull()
                    ?.statusOn(today)
                    ?.let { formatSessionStatusLine(it) }
            }
            isRefreshing.value = false
        }
    }
}
