package com.informedcitizen.ui.billslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.informedcitizen.data.ai.AiCapability
import com.informedcitizen.data.ai.BillTopic
import com.informedcitizen.data.cache.BillSummaryCache
import com.informedcitizen.data.cache.BillSummaryEntry
import com.informedcitizen.data.model.Bill
import com.informedcitizen.data.repository.AiTitlesPreferenceRepository
import com.informedcitizen.data.repository.BillRepository
import com.informedcitizen.data.repository.SessionCalendarRepository
import com.informedcitizen.data.work.BillSummarizationController
import com.informedcitizen.domain.session.statusOn
import com.informedcitizen.ui.components.BillCardSummary
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
    private val cache: BillSummaryCache,
    private val aiPrefs: AiTitlesPreferenceRepository,
    private val aiCapability: AiCapability,
    private val controller: BillSummarizationController,
) : ViewModel() {

    private val billsResult = MutableStateFlow<Result<List<Bill>>?>(null)
    private val filter = MutableStateFlow(BillsListFilter.ALL)
    private val isRefreshing = MutableStateFlow(false)
    private val sessionStatusLine = MutableStateFlow<String?>(null)
    private val selectedTopic = MutableStateFlow<BillTopic?>(null)

    val uiState: StateFlow<BillsListUiState> = combine(
        listOf(
            billsResult,
            filter,
            isRefreshing,
            sessionStatusLine,
            cache.observeAll(),
            aiPrefs.enabled,
            aiCapability.status,
            selectedTopic,
        ),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val result = values[0] as Result<List<Bill>>?
        val currentFilter = values[1] as BillsListFilter
        val refreshing = values[2] as Boolean
        val statusLine = values[3] as String?
        @Suppress("UNCHECKED_CAST")
        val cacheRows = values[4] as Map<String, BillSummaryEntry>
        val aiEnabled = values[5] as Boolean
        val capStatus = values[6] as AiCapability.Status
        val topic = values[7] as BillTopic?

        when {
            result == null -> BillsListUiState.Loading
            result.isFailure -> BillsListUiState.Error(
                message = result.exceptionOrNull()?.localizedMessage ?: "Couldn't load bills",
            )
            else -> buildSuccess(
                allBills = result.getOrThrow(),
                currentFilter = currentFilter,
                refreshing = refreshing,
                statusLine = statusLine,
                cacheRows = cacheRows,
                aiEnabled = aiEnabled,
                capStatus = capStatus,
                topic = topic,
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

    fun selectTopic(topic: BillTopic?) {
        selectedTopic.value = topic
    }

    fun resummarize(billId: String) {
        controller.retry(billId)
    }

    private fun buildSuccess(
        allBills: List<Bill>,
        currentFilter: BillsListFilter,
        refreshing: Boolean,
        statusLine: String?,
        cacheRows: Map<String, BillSummaryEntry>,
        aiEnabled: Boolean,
        capStatus: AiCapability.Status,
        topic: BillTopic?,
    ): BillsListUiState.Success {
        val capable = capStatus == AiCapability.Status.Available ||
            capStatus is AiCapability.Status.ModelDownloading

        val visibleSummaries: Map<String, BillCardSummary> = if (aiEnabled) {
            cacheRows.mapValues { (_, entry) ->
                BillCardSummary(
                    generatedTitle = entry.summary?.generatedTitle,
                    topic = entry.summary?.topic,
                )
            }
        } else {
            emptyMap()
        }

        val baseList = allBills.filter(currentFilter::matches)
        val activeTopic = if (aiEnabled) topic else null
        val (filteredBills, hidden) = if (activeTopic != null) {
            val passing = baseList.filter { visibleSummaries[it.id]?.topic == activeTopic }
            passing to (baseList.size - passing.size)
        } else {
            baseList to 0
        }

        return BillsListUiState.Success(
            bills = filteredBills,
            filter = currentFilter,
            isRefreshing = refreshing,
            sessionStatusLine = statusLine,
            aiTitlesEnabled = aiEnabled,
            deviceCapable = capable,
            summaries = visibleSummaries,
            selectedTopic = activeTopic,
            hiddenByTopicCount = hidden,
        )
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
