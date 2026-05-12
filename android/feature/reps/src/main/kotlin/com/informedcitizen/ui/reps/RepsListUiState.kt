package com.informedcitizen.ui.reps

import com.informedcitizen.data.model.Member

sealed interface RepsListUiState {
    /** Initial state while we resolve saved reps and load members. */
    data object Loading : RepsListUiState
    /** No saved representatives yet — prompt the user to pick a location. */
    data object NoLocation : RepsListUiState
    /** Successfully resolved reps from saved IDs. */
    data class Loaded(val house: List<Member>, val senators: List<Member>) : RepsListUiState
    /** Saved bioguide IDs are no longer in the current Congress's index (e.g. retirement, new election). */
    data object StaleSavedReps : RepsListUiState
    /** Something went wrong loading reps. */
    data class Error(val message: String) : RepsListUiState
}
