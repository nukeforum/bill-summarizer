package com.informedcitizen.ui.reps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.informedcitizen.data.repository.BillRepository
import com.informedcitizen.data.repository.MemberRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class MemberDetailViewModel @Inject constructor(
    private val members: MemberRepository,
    private val bills: BillRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemberDetailUiState())
    val uiState: StateFlow<MemberDetailUiState> = _uiState.asStateFlow()

    internal var congressProvider: () -> Int = { computeCurrentCongress() }

    private fun computeCurrentCongress(today: LocalDate = LocalDate.now()): Int =
        ((today.year - 1789) / 2) + 1

    fun load(bioguideId: String) {
        viewModelScope.launch {
            val congress = congressProvider()
            // Pre-warm the bill cache so isInLocalCache returns accurate
            // results when the user taps a sponsored/cosponsored row, even if
            // they navigated straight to MemberDetail without ever opening
            // the Bills tab. getBills() is idempotent — once cached it
            // returns immediately. Fire-and-forget on purpose; we don't want
            // to block the member render on the bills fetch.
            launch { bills.getBills() }
            try {
                val member = members.getMember(bioguideId, congress)
                val sponsored = members.getSponsored(bioguideId)
                val cosponsored = members.getCosponsored(bioguideId)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        member = member,
                        sponsored = sponsored?.bills.orEmpty(),
                        cosponsored = cosponsored?.bills.orEmpty(),
                        errorMessage = null,
                    )
                }
            } catch (t: Throwable) {
                _uiState.update { it.copy(isLoading = false, errorMessage = t.message) }
            }
        }
    }

    fun isInLocalCache(billId: String): Boolean = bills.containsBillId(billId)
}
