package com.billsummarizer.ui.billdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.billsummarizer.data.api.BillTextFetcher
import com.billsummarizer.data.repository.BillRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BillDetailViewModel @Inject constructor(
    private val billRepository: BillRepository,
    private val billTextFetcher: BillTextFetcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BillDetailUiState>(BillDetailUiState.Loading)
    val uiState: StateFlow<BillDetailUiState> = _uiState.asStateFlow()

    private val _fullTextState = MutableStateFlow<FullTextState>(FullTextState.Idle)
    val fullTextState: StateFlow<FullTextState> = _fullTextState.asStateFlow()

    fun load(billId: String) {
        viewModelScope.launch {
            _uiState.value = BillDetailUiState.Loading

            billRepository.getBillById(billId)?.let {
                _uiState.value = BillDetailUiState.Success(it)
                return@launch
            }

            billRepository.getBills()
                .onSuccess {
                    val bill = billRepository.getBillById(billId)
                    _uiState.value = if (bill != null) {
                        BillDetailUiState.Success(bill)
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
