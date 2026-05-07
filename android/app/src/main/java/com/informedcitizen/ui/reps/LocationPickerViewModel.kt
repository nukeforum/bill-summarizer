package com.informedcitizen.ui.reps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.informedcitizen.data.model.MembersIndex
import com.informedcitizen.data.repository.MemberRepository
import com.informedcitizen.data.repository.SavedRepsRepository
import com.informedcitizen.data.zipcrosswalk.ZipDistrictLookup
import com.informedcitizen.data.zipcrosswalk.ZipDistrictResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private val AT_LARGE_STATES = setOf("AK", "DE", "ND", "SD", "VT", "WY")
private val DELEGATE_JURISDICTIONS = setOf("DC", "AS", "GU", "MP", "PR", "VI")

private fun isSingleMember(stateCode: String) =
    stateCode in AT_LARGE_STATES || stateCode in DELEGATE_JURISDICTIONS

// 119th Congress House delegations. Used as a fallback when the live members
// index hasn't loaded yet (e.g. first-launch network failure) so the picker
// remains usable; the live index is preferred whenever available.
private val HOUSE_DISTRICT_COUNTS: Map<String, Int> = mapOf(
    "AL" to 7, "AZ" to 9, "AR" to 4, "CA" to 52, "CO" to 8, "CT" to 5,
    "FL" to 28, "GA" to 14, "HI" to 2, "ID" to 2, "IL" to 17, "IN" to 9,
    "IA" to 4, "KS" to 4, "KY" to 6, "LA" to 6, "ME" to 2, "MD" to 8,
    "MA" to 9, "MI" to 13, "MN" to 8, "MS" to 4, "MO" to 8, "MT" to 2,
    "NE" to 3, "NV" to 4, "NH" to 2, "NJ" to 12, "NM" to 3, "NY" to 26,
    "NC" to 14, "OH" to 15, "OK" to 5, "OR" to 6, "PA" to 17, "RI" to 2,
    "SC" to 7, "TN" to 9, "TX" to 38, "UT" to 4, "VA" to 11, "WA" to 10,
    "WV" to 2, "WI" to 8,
)

@HiltViewModel
class LocationPickerViewModel @Inject constructor(
    private val savedReps: SavedRepsRepository,
    private val zipLookup: ZipDistrictLookup,
    private val members: MemberRepository,
) : ViewModel() {

    // Allow tests to inject a deterministic congress.
    internal var congressProvider: () -> Int = ::computeCurrentCongress

    private val _uiState = MutableStateFlow(LocationPickerUiState())
    val uiState: StateFlow<LocationPickerUiState> = _uiState.asStateFlow()

    // One-shot channel for navigation events. The screen pops back on Saved.
    // Conflated so a fast re-navigation doesn't deliver stale events.
    private val _events = Channel<LocationPickerEvent>(Channel.CONFLATED)
    val events: Flow<LocationPickerEvent> = _events.receiveAsFlow()

    private var pendingDistrict: Int? = null
    private var loadedIndex: MembersIndex? = null

    init {
        // Probe the crosswalk asset on construction. If it's missing (e.g. the
        // HUD CSV hasn't been bundled into the build yet) the UI hides the
        // ZIP textbox entirely instead of silently failing every lookup.
        viewModelScope.launch {
            val available = zipLookup.isAvailable()
            _uiState.update { it.copy(isZipLookupAvailable = available) }
        }
        // Pre-load the members index so district button counts reflect the
        // current redistricting cycle. If the load fails (no network on first
        // launch, etc.) we silently fall back to the hardcoded counts above.
        viewModelScope.launch {
            loadedIndex = members.getIndex(congressProvider())
            // If a state was selected before the index finished loading,
            // refresh its district list so the user sees the live counts.
            val current = _uiState.value
            val sc = current.selectedState
            if (sc != null && !current.isAtLargeOrDelegate) {
                _uiState.update { it.copy(districtsForState = districtsForState(sc)) }
            }
        }
    }

    private fun districtsForState(stateCode: String): List<Int> {
        if (isSingleMember(stateCode)) return emptyList()
        val fromIndex = loadedIndex?.let { idx ->
            idx.members
                .asSequence()
                .filter { it.state == stateCode && it.chamber == "house" }
                .mapNotNull { it.district }
                .filter { it > 0 }
                .toSortedSet()
                .toList()
        }
        if (!fromIndex.isNullOrEmpty()) return fromIndex
        val count = HOUSE_DISTRICT_COUNTS[stateCode] ?: return emptyList()
        return (1..count).toList()
    }

    fun selectState(stateCode: String) {
        val sc = stateCode.uppercase()
        val districts = districtsForState(sc)
        val atLarge = isSingleMember(sc)
        pendingDistrict = if (atLarge) 0 else null
        _uiState.update {
            it.copy(
                selectedState = sc,
                districtsForState = districts,
                isAtLargeOrDelegate = atLarge,
                districtHint = DistrictHint.None,
                canSave = atLarge,
            )
        }
    }

    fun selectDistrict(district: Int) {
        pendingDistrict = district
        _uiState.update { it.copy(canSave = it.selectedState != null) }
    }

    fun selectMode(mode: LocationPickerMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun onZipChanged(zip: String) {
        _uiState.update { it.copy(zipInput = zip) }
    }

    fun lookupZip() {
        val zip = _uiState.value.zipInput.trim()
        if (zip.isEmpty()) return
        viewModelScope.launch {
            when (val result = zipLookup.lookup(zip)) {
                is ZipDistrictResult.Single -> {
                    pendingDistrict = result.district
                    _uiState.update {
                        it.copy(
                            selectedState = result.state,
                            districtsForState = districtsForState(result.state),
                            isAtLargeOrDelegate = isSingleMember(result.state),
                            districtHint = DistrictHint.Single(result.district),
                            canSave = true,
                        )
                    }
                }
                is ZipDistrictResult.Multiple -> {
                    pendingDistrict = null
                    // The user needs to disambiguate, which only the district
                    // grid can do — flip to Pick so the narrowed grid is in view.
                    _uiState.update {
                        it.copy(
                            selectedState = result.state,
                            districtsForState = result.districts,
                            isAtLargeOrDelegate = false,
                            districtHint = DistrictHint.Multiple(result.districts),
                            canSave = false,
                            mode = LocationPickerMode.Pick,
                        )
                    }
                }
                ZipDistrictResult.Miss -> _uiState.update { it.copy(districtHint = DistrictHint.Miss) }
            }
        }
    }

    fun save() {
        val state = _uiState.value.selectedState ?: return
        // pendingDistrict is 0 for at-large/delegate, so it's safe to pass through.
        // For non-at-large states the UI gates save behind canSave (= district picked),
        // so a null here means at-large; pass null and let the repo filter return all.
        val district = pendingDistrict
        viewModelScope.launch {
            val resolved = runCatching {
                members.findRepsForLocation(
                    congress = congressProvider(),
                    stateCode = state,
                    district = district,
                )
            }.getOrNull()
            val ids = buildSet {
                resolved?.house?.forEach { add(it.bioguideId) }
                resolved?.senators?.forEach { add(it.bioguideId) }
            }
            // No representatives resolved means the index is unavailable or the
            // chosen state/district has no members on file. Don't persist an
            // empty save — surface a hint so the user knows to retry.
            if (ids.isEmpty()) {
                _uiState.update { it.copy(districtHint = DistrictHint.SaveFailed) }
                return@launch
            }
            savedReps.set(ids)
            _events.trySend(LocationPickerEvent.Saved)
        }
    }

    private fun computeCurrentCongress(today: LocalDate = LocalDate.now()): Int =
        ((today.year - 1789) / 2) + 1
}
