package com.billsummarizer.ui.billdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) : ViewModel() {

    private val _uiState = MutableStateFlow<BillDetailUiState>(BillDetailUiState.Loading)
    val uiState: StateFlow<BillDetailUiState> = _uiState.asStateFlow()

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
}
