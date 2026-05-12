package com.informedcitizen.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.informedcitizen.data.repository.SessionCalendarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionCalendarViewModel @Inject constructor(
    private val repository: SessionCalendarRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SessionCalendarUiState>(SessionCalendarUiState.Loading)
    val uiState: StateFlow<SessionCalendarUiState> = _uiState.asStateFlow()

    init {
        load(forceRefresh = false)
    }

    fun retry() = load(forceRefresh = true)

    private fun load(forceRefresh: Boolean) {
        _uiState.value = SessionCalendarUiState.Loading
        viewModelScope.launch {
            repository.getCalendar(forceRefresh).fold(
                onSuccess = { _uiState.value = SessionCalendarUiState.Success(it) },
                onFailure = {
                    _uiState.value = SessionCalendarUiState.Error(
                        it.message ?: "Could not load Congress calendar"
                    )
                },
            )
        }
    }
}
