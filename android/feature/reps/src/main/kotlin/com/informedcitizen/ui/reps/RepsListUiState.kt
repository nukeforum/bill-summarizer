package com.informedcitizen.ui.reps

import com.informedcitizen.pipeline.model.Member

sealed interface RepsListUiState {
    /** Initial state while we resolve saved reps and load members. */
    data object Loading : RepsListUiState
    /** No saved representatives yet — prompt the user to pick a location. */
    data object NoLocation : RepsListUiState
    /**
     * Successfully resolved reps from saved IDs. [ballotBadges] maps a
     * bioguide ID to its "up for election" badge text (issue #33), present
     * only for reps with a dated upcoming general on record; absent keys
     * render no badge.
     */
    data class Loaded(
        val house: List<Member>,
        val senators: List<Member>,
        val ballotBadges: Map<String, String> = emptyMap(),
    ) : RepsListUiState
    /** Saved bioguide IDs are no longer in the current Congress's index (e.g. retirement, new election). */
    data object StaleSavedReps : RepsListUiState
    /** Something went wrong loading reps. */
    data class Error(val message: String) : RepsListUiState
}
