package com.informedcitizen.ui.reps

sealed interface DistrictHint {
    data object None : DistrictHint
    data class Single(val district: Int) : DistrictHint
    data class Multiple(val districts: List<Int>) : DistrictHint
    data object Miss : DistrictHint
    /** Save tapped but the index couldn't resolve any reps (offline / empty state). */
    data object SaveFailed : DistrictHint
}

sealed interface LocationPickerEvent {
    data object Saved : LocationPickerEvent
}

data class LocationPickerUiState(
    val selectedState: String? = null,
    val districtsForState: List<Int> = emptyList(),
    val isAtLargeOrDelegate: Boolean = false,
    val zipInput: String = "",
    val districtHint: DistrictHint = DistrictHint.None,
    val canSave: Boolean = false,
    /**
     * True when the bundled ZIP -> congressional-district crosswalk asset is
     * loadable. Defaults to true so existing tests and pre-init UI render the
     * ZIP entry by default; the VM probes [ZipDistrictLookup.isAvailable] on
     * construction and flips this off if the asset is missing, in which case
     * the UI hides the ZIP textbox entirely.
     */
    val isZipLookupAvailable: Boolean = true,
)
