package com.informedcitizen.ui.calendar

import com.informedcitizen.pipeline.model.SessionCalendar

sealed interface SessionCalendarUiState {
    data object Loading : SessionCalendarUiState
    data class Error(val message: String) : SessionCalendarUiState
    data class Success(val calendar: SessionCalendar) : SessionCalendarUiState
}
