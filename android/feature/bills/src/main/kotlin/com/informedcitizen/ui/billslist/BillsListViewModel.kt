package com.informedcitizen.ui.billslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import com.informedcitizen.data.ai.AiCapability
import com.informedcitizen.data.ai.BillTopic
import com.informedcitizen.data.cache.BillSummaryCache
import com.informedcitizen.data.cache.BillSummaryEntry
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.data.repository.AiTitlesPreferenceRepository
import com.informedcitizen.data.repository.BillRepository
import com.informedcitizen.data.repository.SessionCalendarRepository
import com.informedcitizen.data.work.BillSummarizationController
import com.informedcitizen.domain.search.matchesSearchQuery
import com.informedcitizen.domain.search.searchRelevance
import com.informedcitizen.domain.session.statusOn
import com.informedcitizen.ui.components.BillCardSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
    private val selectedPolicyArea = MutableStateFlow<String?>(null)
    private val selectedSubject = MutableStateFlow<String?>(null)
    private val selectedStatus = MutableStateFlow(BillStatusFilter.ALL)
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

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
            _searchQuery,
            selectedPolicyArea,
            billRepository.generatedAt,
            selectedStatus,
            selectedSubject,
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
        val query = values[8] as String
        val policyArea = values[9] as String?
        val generatedAt = values[10] as String?
        val status = values[11] as BillStatusFilter
        val subject = values[12] as String?

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
                query = query,
                policyArea = policyArea,
                generatedAt = generatedAt,
                status = status,
                subject = subject,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BillsListUiState.Loading)

    // --- Paging 3 stream (issue #41, epic #38) -------------------------------
    // The bills list is migrating from the in-memory `billsResult`/`buildSuccess`
    // list above to a shard-paged `Flow<PagingData<Bill>>`. This is the
    // ViewModel side of that switch; the UI consumes it in a following slice.

    /**
     * The SQL-paged source. The two SQL-bound filters — [selectedStatus] (#42
     * lifecycle status) and [selectedPolicyArea] (#10) — drive
     * [BillRepository.pagedBills], so the Pager is rebuilt via [flatMapLatest]
     * *only* when either of those changes. [cachedIn] snapshots the loaded pages
     * into [viewModelScope] so the three list-only filters applied downstream
     * (outcome chips, free-text search, AI topic) never restart the Pager — a
     * keystroke or topic toggle re-filters cached pages rather than re-hitting
     * the network/DB.
     *
     * The outcome chips ([BillsListFilter]) stay a downstream predicate, not a
     * SQL param: they filter `Bill.outcome`, a different axis from the #39
     * lifecycle `status` column (see [BillStatusFilter]).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val pagedSource: Flow<PagingData<Bill>> =
        combine(selectedStatus, selectedPolicyArea) { status, policyArea ->
            status to policyArea
        }
            .flatMapLatest { (status, policyArea) ->
                billRepository.pagedBills(status = status.sqlStatus, policyArea = policyArea)
            }
            .cachedIn(viewModelScope)

    /**
     * The two post-SQL "chip" filters folded into one flow so the [pagedBills]
     * combine stays within kotlinx's 5-arg typed overload: the outcome
     * [BillsListFilter] and the #10 legislative-subject facet.
     */
    private val postSqlChips: Flow<Pair<BillsListFilter, String?>> =
        combine(filter, selectedSubject) { currentFilter, subject -> currentFilter to subject }

    /** The AI topic to filter by, or null when AI titles are disabled. */
    private val activeTopicFilter: Flow<BillTopic?> =
        combine(selectedTopic, aiPrefs.enabled) { topic, aiEnabled ->
            if (aiEnabled) topic else null
        }

    /** The summary rows the topic predicate resolves against (empty when AI off). */
    private val visibleSummariesFlow: Flow<Map<String, BillCardSummary>> =
        combine(cache.observeAll(), aiPrefs.enabled) { rows, aiEnabled ->
            if (aiEnabled) {
                rows.mapValues { (_, entry) ->
                    BillCardSummary(
                        generatedTitle = entry.summary?.generatedTitle,
                        topic = entry.summary?.topic,
                    )
                }
            } else {
                emptyMap()
            }
        }

    /**
     * The bills list as a paged, filtered stream. The three list-only filters
     * are applied over the cached pages via [billMatchesListFilters] — the same
     * seam `buildSuccess` uses — so paged and in-memory filtering stay identical
     * during the migration.
     */
    val pagedBills: Flow<PagingData<Bill>> = combine(
        pagedSource,
        postSqlChips,
        _searchQuery,
        activeTopicFilter,
        visibleSummariesFlow,
    ) { data, (currentFilter, subject), query, activeTopic, summaries ->
        data.filter { billMatchesListFilters(it, currentFilter, query, activeTopic, summaries, subject) }
    }

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

    fun selectPolicyArea(policyArea: String?) {
        selectedPolicyArea.value = policyArea
    }

    fun selectSubject(subject: String?) {
        selectedSubject.value = subject
    }

    fun setStatusFilter(status: BillStatusFilter) {
        selectedStatus.value = status
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
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
        query: String,
        policyArea: String?,
        generatedAt: String?,
        status: BillStatusFilter,
        subject: String?,
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

        val availablePolicyAreas = allBills
            .mapNotNull { it.policyArea }
            .distinct()
            .sorted()
        val availableSubjects = distinctSubjects(allBills)
        // A stale selection (e.g. the subject vanished on refresh) deselects
        // rather than pinning the list to an invisible empty filter.
        val activePolicyArea = policyArea?.takeIf { it in availablePolicyAreas }
        val activeSubject = subject?.takeIf { it in availableSubjects }
        val activeTopic = if (aiEnabled) topic else null
        // policyArea filters here (it becomes a SQL predicate under #41 paging);
        // the remaining four narrow via the shared billMatchesListFilters seam.
        val policyFiltered = allBills
            .filter { activePolicyArea == null || it.policyArea == activePolicyArea }
        val matchedBills = policyFiltered.filter {
            billMatchesListFilters(it, currentFilter, query, activeTopic, visibleSummaries, activeSubject)
        }
        // With a free-text query active, rank the matches by relevance so a bill
        // genuinely about the query outranks one that only mentions the words in
        // a long summary; sortedByDescending is stable, so ties keep the
        // latest-action date order the list is otherwise sorted by.
        val filteredBills = if (query.isBlank()) {
            matchedBills
        } else {
            matchedBills.sortedByDescending { it.searchRelevance(query) }
        }
        // Bills passing every filter except the topic are counted as hidden so
        // the "N bills hidden — not yet summarized" nudge stays honest.
        val hidden = if (activeTopic != null) {
            policyFiltered.count {
                currentFilter.matches(it) && it.matchesSearchQuery(query) &&
                    (activeSubject == null || it.hasSubject(activeSubject))
            } - filteredBills.size
        } else {
            0
        }

        return BillsListUiState.Success(
            bills = filteredBills,
            filter = currentFilter,
            isRefreshing = refreshing,
            sessionStatusLine = statusLine,
            dataGeneratedAt = parseGeneratedAt(generatedAt),
            aiTitlesEnabled = aiEnabled,
            deviceCapable = capable,
            summaries = visibleSummaries,
            selectedTopic = activeTopic,
            hiddenByTopicCount = hidden,
            searchQuery = query,
            availablePolicyAreas = availablePolicyAreas,
            selectedPolicyArea = activePolicyArea,
            availableSubjects = availableSubjects,
            selectedSubject = activeSubject,
            selectedStatus = status,
            // The lifecycle-status filter only makes sense once the broadened
            // (#39) pre-floor bills are published; until then every bill has a
            // null status, so the row stays hidden rather than showing dead chips.
            statusFilterAvailable = allBills.any { it.lifecycleStatus != null },
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
