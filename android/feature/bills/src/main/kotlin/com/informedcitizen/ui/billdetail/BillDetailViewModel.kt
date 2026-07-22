package com.informedcitizen.ui.billdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.informedcitizen.data.api.BillTextFetcher
import com.informedcitizen.data.repository.BillRepository
import com.informedcitizen.data.repository.MemberVotesRepository
import com.informedcitizen.data.repository.SavedRepsSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BillDetailViewModel @Inject constructor(
    private val billRepository: BillRepository,
    private val billTextFetcher: BillTextFetcher,
    private val savedRepsSource: SavedRepsSource,
    private val memberVotesRepository: MemberVotesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BillDetailUiState>(BillDetailUiState.Loading)
    val uiState: StateFlow<BillDetailUiState> = _uiState.asStateFlow()

    private val _fullTextState = MutableStateFlow<FullTextState>(FullTextState.Idle)
    val fullTextState: StateFlow<FullTextState> = _fullTextState.asStateFlow()

    private val _repVotes = MutableStateFlow(RepVotesUiState.Empty)
    val repVotes: StateFlow<RepVotesUiState> = _repVotes.asStateFlow()

    private var repVotesJob: Job? = null

    fun load(billId: String) {
        observeRepVotes(billId)
        viewModelScope.launch {
            _uiState.value = BillDetailUiState.Loading

            billRepository.getBillById(billId)?.let {
                _uiState.value = BillDetailUiState.Success(it, billRepository.votesCoverage.value)
                return@launch
            }

            billRepository.getBills()
                .onSuccess {
                    val bill = billRepository.getBillById(billId)
                    _uiState.value = if (bill != null) {
                        BillDetailUiState.Success(bill, billRepository.votesCoverage.value)
                    } else {
                        BillDetailUiState.Error("Bill not found")
                    }
                }
                .onFailure { throwable ->
                    _uiState.value = BillDetailUiState.Error(
                        throwable.localizedMessage ?: "Couldn't load bill"
                    )
                }
        }
    }

    /**
     * Keeps [repVotes] in sync with the manifest coverage gate and the
     * saved-reps set. Shards are fetched per saved rep (never per
     * bill) inside [MemberVotesRepository]; with coverage off or no
     * reps saved nothing is fetched at all.
     */
    private fun observeRepVotes(billId: String) {
        repVotesJob?.cancel()
        repVotesJob = viewModelScope.launch {
            billRepository.votesCoverage.collectLatest { coverage ->
                if (!coverage) {
                    _repVotes.value = RepVotesUiState.Empty
                    return@collectLatest
                }
                savedRepsSource.savedReps.collectLatest { reps ->
                    val votes = memberVotesRepository.load(reps)
                    _repVotes.value = RepVotesUiState(
                        positionsByVoteId = votes.positionsByBillId[billId]
                            .orEmpty()
                            .groupBy { it.voteId },
                        fetchFailed = votes.fetchFailed,
                    )
                }
            }
        }
    }

    fun fetchFullText() {
        val current = _uiState.value
        val bill = (current as? BillDetailUiState.Success)?.bill ?: return
        val url = bill.textUrlHtml ?: run {
            _fullTextState.value = FullTextState.Error("No HTML text URL published yet.")
            return
        }
        if (_fullTextState.value is FullTextState.Loading) return

        viewModelScope.launch {
            _fullTextState.value = FullTextState.Loading
            billTextFetcher.fetchPlainText(url)
                .onSuccess { _fullTextState.value = FullTextState.Loaded(it) }
                .onFailure {
                    _fullTextState.value = FullTextState.Error(
                        it.localizedMessage ?: "Couldn't fetch full text"
                    )
                }
        }
    }

    fun resetFullText() {
        _fullTextState.value = FullTextState.Idle
    }
}
