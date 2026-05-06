package com.informedcitizen.ui.reps

import com.informedcitizen.data.model.Member

sealed interface RepsListUiState {
    /** Initial state while we resolve the saved location and load members. */
    data object Loading : RepsListUiState
    /** No saved state/district yet — prompt the user to pick a location. */
    data object NoLocation : RepsListUiState
    /** Successfully resolved reps for the saved location. */
    data class Loaded(val house: List<Member>, val senators: List<Member>) : RepsListUiState
    /** Saved district has no matching House member in the current Congress (e.g., post-redistricting). */
    data class StaleDistrict(val stateCode: String, val district: Int) : RepsListUiState
    /** Something went wrong loading reps. */
    data class Error(val message: String) : RepsListUiState
}
