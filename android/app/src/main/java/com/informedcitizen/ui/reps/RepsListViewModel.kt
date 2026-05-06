package com.informedcitizen.ui.reps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.informedcitizen.data.repository.LocationPreferenceRepository
import com.informedcitizen.data.repository.MemberRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

internal fun computeCurrentCongress(today: LocalDate = LocalDate.now()): Int =
    ((today.year - 1789) / 2) + 1

@HiltViewModel
class RepsListViewModel @Inject constructor(
    private val prefs: LocationPreferenceRepository,
    private val members: MemberRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<RepsListUiState>(RepsListUiState.Loading)
    val uiState: StateFlow<RepsListUiState> = _uiState.asStateFlow()

    // Allow tests to inject a deterministic congress.
    internal var congressProvider: () -> Int = ::computeCurrentCongress

    init {
        observeLocation()
    }

    private fun observeLocation() {
        viewModelScope.launch {
            prefs.location.collectLatest { saved ->
                val state = saved.stateCode
                if (state == null) {
                    _uiState.value = RepsListUiState.NoLocation
                    return@collectLatest
                }
                _uiState.value = RepsListUiState.Loading
                _uiState.value = try {
                    val out = members.findRepsForLocation(
                        congress = congressProvider(),
                        stateCode = state,
                        district = saved.district,
                    )
                    RepsListUiState.Loaded(out.house, out.senators)
                } catch (t: Throwable) {
                    RepsListUiState.Error(t.message ?: "Could not load representatives")
                }
            }
        }
    }
}
