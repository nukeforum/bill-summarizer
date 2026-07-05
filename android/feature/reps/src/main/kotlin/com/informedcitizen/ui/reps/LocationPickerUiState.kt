package com.informedcitizen.ui.reps

sealed interface DistrictHint {
    data object None : DistrictHint
    data class Single(val district: Int) : DistrictHint
    data class Multiple(val districts: List<Int>) : DistrictHint
    data object Miss : DistrictHint
    /** Save tapped but the index couldn't resolve any reps (offline / empty state). */
    data object SaveFailed : DistrictHint
}

enum class LocationPickerMode { Pick, Lookup }

sealed interface LocationPickerEvent {
    data object Saved : LocationPickerEvent
}

data class LocationPickerUiState(
    val selectedState: String? = null,
    /**
     * The district that will be saved — either auto-found via ZIP lookup or
     * manually picked from the grid (a manual pick always wins over a prior
     * auto-find). Null until one is chosen; 0 for at-large/delegate states.
     */
    val selectedDistrict: Int? = null,
    val districtsForState: List<Int> = emptyList(),
    val isAtLargeOrDelegate: Boolean = false,
    val zipInput: String = "",
    val districtHint: DistrictHint = DistrictHint.None,
    val canSave: Boolean = false,
    /**
     * True while a single-district ZIP match is awaiting the user's confirmation.
     * A single match pre-fills [selectedState]/[selectedDistrict] and pops a
     * confirmation dialog so it's obvious an action is needed, rather than
     * saving silently or leaving the user unsure they must press Save.
     */
    val awaitingZipConfirm: Boolean = false,
    val mode: LocationPickerMode = LocationPickerMode.Pick,
    /**
     * True when the bundled ZIP -> congressional-district crosswalk asset is
     * loadable. Defaults to true so existing tests and pre-init UI render the
     * ZIP entry by default; the VM probes [ZipDistrictLookup.isAvailable] on
     * construction and flips this off if the asset is missing, in which case
     * the UI hides the ZIP textbox entirely.
     */
    val isZipLookupAvailable: Boolean = true,
)
