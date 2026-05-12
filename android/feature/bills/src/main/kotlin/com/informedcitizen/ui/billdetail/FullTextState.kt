package com.informedcitizen.ui.billdetail

sealed interface FullTextState {
    data object Idle : FullTextState
    data object Loading : FullTextState
    data class Loaded(val text: String) : FullTextState
    data class Error(val message: String) : FullTextState
}
