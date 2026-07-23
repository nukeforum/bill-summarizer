package com.informedcitizen.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.informedcitizen.data.repository.ElectionCalendarRepository
import com.informedcitizen.data.repository.SessionCalendarRepository
import com.informedcitizen.pipeline.model.ElectionCalendar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionCalendarViewModel @Inject constructor(
    private val repository: SessionCalendarRepository,
    private val electionRepository: ElectionCalendarRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SessionCalendarUiState>(SessionCalendarUiState.Loading)
    val uiState: StateFlow<SessionCalendarUiState> = _uiState.asStateFlow()

    /**
     * The election calendar for the upcoming-elections section (issue #24),
     * loaded independently of the session calendar: a failed or missing
     * election fetch leaves this null (the section hides) without knocking
     * the session-calendar screen into its error state.
     */
    private val _electionCalendar = MutableStateFlow<ElectionCalendar?>(null)
    val electionCalendar: StateFlow<ElectionCalendar?> = _electionCalendar.asStateFlow()

    init {
        load(forceRefresh = false)
        loadElections(forceRefresh = false)
    }

    fun retry() {
        load(forceRefresh = true)
        loadElections(forceRefresh = true)
    }

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

    private fun loadElections(forceRefresh: Boolean) {
        viewModelScope.launch {
            // Best-effort: the repository already records a non-fatal on
            // failure, so on failure we simply leave the section hidden.
            electionRepository.getCalendar(forceRefresh).onSuccess {
                _electionCalendar.value = it
            }
        }
    }
}
