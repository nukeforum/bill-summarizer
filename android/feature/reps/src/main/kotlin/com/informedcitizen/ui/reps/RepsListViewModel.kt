package com.informedcitizen.ui.reps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.informedcitizen.data.repository.ElectionCalendarRepository
import com.informedcitizen.data.repository.MemberRepository
import com.informedcitizen.data.repository.SavedRepsRepository
import com.informedcitizen.pipeline.model.ElectionCalendar
import com.informedcitizen.pipeline.model.Member
import com.informedcitizen.ui.calendar.upForElectionBadge
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
    private val savedReps: SavedRepsRepository,
    private val members: MemberRepository,
    private val electionCalendar: ElectionCalendarRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<RepsListUiState>(RepsListUiState.Loading)
    val uiState: StateFlow<RepsListUiState> = _uiState.asStateFlow()

    internal var congressProvider: () -> Int = ::computeCurrentCongress
    internal var todayProvider: () -> LocalDate = { LocalDate.now() }

    init { observeSavedReps() }

    fun deleteSavedReps() {
        viewModelScope.launch { savedReps.forget() }
    }

    /**
     * The "up for election" badge (issue #33) for each member that has a
     * dated upcoming general on record. The election calendar is best-effort:
     * a failed/absent fetch yields a null calendar, so every rep simply gets
     * no badge — the reps list must never fail because the calendar is
     * unavailable. Keyed by bioguide ID; members with no match are omitted.
     */
    private suspend fun ballotBadges(reps: List<Member>): Map<String, String> {
        val calendar: ElectionCalendar? = electionCalendar.getCalendar().getOrNull()
        if (calendar == null) return emptyMap()
        val today = todayProvider()
        return reps.mapNotNull { m ->
            upForElectionBadge(m, calendar, today)?.let { m.bioguideId to it }
        }.toMap()
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
                        RepsListUiState.StaleSavedReps
                    } else {
                        RepsListUiState.Loaded(
                            house = out.house,
                            senators = out.senators,
                            ballotBadges = ballotBadges(out.house + out.senators),
                        )
                    }
                } catch (t: Throwable) {
                    RepsListUiState.Error(t.message ?: "Could not load representatives")
                }
            }
        }
    }
}
