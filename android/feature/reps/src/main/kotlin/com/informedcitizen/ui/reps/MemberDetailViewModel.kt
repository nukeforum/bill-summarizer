package com.informedcitizen.ui.reps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.informedcitizen.data.repository.BillRepository
import com.informedcitizen.data.repository.ElectionCalendarRepository
import com.informedcitizen.data.repository.MemberRepository
import com.informedcitizen.ui.calendar.upForElectionBadge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class MemberDetailViewModel @Inject constructor(
    private val members: MemberRepository,
    private val bills: BillRepository,
    private val electionCalendar: ElectionCalendarRepository,
) : ViewModel() {

    internal var todayProvider: () -> LocalDate = { LocalDate.now() }

    private val _uiState = MutableStateFlow(MemberDetailUiState())
    val uiState: StateFlow<MemberDetailUiState> = _uiState.asStateFlow()

    internal var congressProvider: () -> Int = { computeCurrentCongress() }

    // The last bioguide loaded, so a Retry from the error state can re-run
    // the same fetch without the screen having to thread the id back in.
    private var lastBioguideId: String? = null

    private fun computeCurrentCongress(today: LocalDate = LocalDate.now()): Int =
        ((today.year - 1789) / 2) + 1

    /** Re-run the last load; wired to the error state's Retry button. */
    fun retry() {
        lastBioguideId?.let { load(it) }
    }

    fun load(bioguideId: String) {
        lastBioguideId = bioguideId
        viewModelScope.launch {
            // Reset to the spinner and clear any prior error so a Retry after
            // a failed load shows progress rather than the stale error screen.
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val congress = congressProvider()
            // Pre-warm the bill cache so isInLocalCache returns accurate
            // results when the user taps a sponsored/cosponsored row, even
            // if they navigated straight to MemberDetail without ever
            // opening the Bills tab. getBills() is idempotent — once
            // cached it returns immediately. Fire-and-forget on purpose;
            // we don't want to block the member render on the bills fetch.
            launch { bills.getBills() }
            try {
                val member = members.getMember(bioguideId, congress)
                val sponsored = members.getSponsored(bioguideId)
                val cosponsored = members.getCosponsored(bioguideId)
                val votes = members.getVotes(bioguideId)
                // Best-effort ballot badge (issue #33): a missing/failed
                // election calendar or an unmatched member yields no badge;
                // it must never fail the member page.
                val ballotBadge = member?.let {
                    val calendar = electionCalendar.getCalendar().getOrNull()
                    upForElectionBadge(it, calendar, todayProvider())
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        member = member,
                        sponsored = sponsored?.bills.orEmpty(),
                        cosponsored = cosponsored?.bills.orEmpty(),
                        recentVotes = votes?.votes?.take(RECENT_VOTES_CAP).orEmpty(),
                        ballotBadge = ballotBadge,
                        errorMessage = null,
                    )
                }
            } catch (t: Throwable) {
                // A failed fetch (typically no connectivity) must not dead-end
                // on a raw OkHttp/DNS string like `Unable to resolve host
                // "nukeforum.github.io"` (issue #101). Surface a plain,
                // actionable message; the screen pairs it with a Retry.
                _uiState.update { it.copy(isLoading = false, errorMessage = LOAD_ERROR_MESSAGE) }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun isInLocalCache(billId: String): Boolean = bills.containsBillId(billId)

    private companion object {
        // The per-member shard already runs to hundreds of rows; the
        // detail history caps at the most recent slice so the tab stays
        // scannable without a "load more" affordance (issue #22).
        const val RECENT_VOTES_CAP = 25

        // Human-readable, connection-oriented copy shown in place of a raw
        // network exception message (issue #101).
        const val LOAD_ERROR_MESSAGE =
            "Couldn't load this representative's record. Check your connection and try again."
    }
}
