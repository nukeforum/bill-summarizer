package com.informedcitizen.ui.reps

import com.informedcitizen.data.model.Member

sealed interface RepsListUiState {
    data object Loading : RepsListUiState
    data object NoLocation : RepsListUiState
    data class Loaded(val house: List<Member>, val senators: List<Member>) : RepsListUiState
    data class Error(val message: String) : RepsListUiState
}
