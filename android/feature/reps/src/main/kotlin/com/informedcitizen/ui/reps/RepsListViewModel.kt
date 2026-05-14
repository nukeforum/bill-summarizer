package com.informedcitizen.ui.reps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.informedcitizen.data.repository.MemberRepository
import com.informedcitizen.data.repository.RepsContactPreferenceRepository
import com.informedcitizen.data.repository.SavedRepsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

internal fun computeCurrentCongress(today: LocalDate = LocalDate.now()): Int =
    ((today.year - 1789) / 2) + 1

@HiltViewModel
class RepsListViewModel @Inject constructor(
    private val savedReps: SavedRepsRepository,
    private val members: MemberRepository,
    private val contactPrefs: RepsContactPreferenceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<RepsListUiState>(RepsListUiState.Loading)
    val uiState: StateFlow<RepsListUiState> = _uiState.asStateFlow()

    val hasSeenWebsiteFallbackDialog: StateFlow<Boolean> = contactPrefs
        .hasSeenWebsiteFallbackDialog
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // Allow tests to inject a deterministic congress.
    internal var congressProvider: () -> Int = ::computeCurrentCongress

    init {
        observeSavedReps()
    }

    fun deleteSavedReps() {
        viewModelScope.launch { savedReps.forget() }
    }

    fun markWebsiteFallbackDialogSeen() {
        viewModelScope.launch { contactPrefs.markWebsiteFallbackDialogSeen() }
    }

    private fun observeSavedReps() {
        viewModelScope.launch {
            savedReps.savedIds.collectLatest { ids ->
                if (ids.isEmpty()) {
                    _uiState.value = RepsListUiState.NoLocation
                    return@collectLatest
                }
                _uiState.value = RepsListUiState.Loading
                _uiState.value = try {
                    val out = members.findRepsByIds(
                        congress = congressProvider(),
                        bioguideIds = ids,
                    )
                    val foundIds = (out.house + out.senators).map { it.bioguideId }.toSet()
                    val missing = ids - foundIds
                    if (missing.isNotEmpty()) {
                        // Saved IDs that no longer appear in the index — most likely
                        // a Congress rollover (retirement, redistricting, new
                        // election cycle). Prompt the user to refresh their picks.
                        RepsListUiState.StaleSavedReps
                    } else {
                        RepsListUiState.Loaded(out.house, out.senators)
                    }
                } catch (t: Throwable) {
                    RepsListUiState.Error(t.message ?: "Could not load representatives")
                }
            }
        }
    }
}
