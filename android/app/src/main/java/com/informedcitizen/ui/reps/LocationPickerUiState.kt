package com.informedcitizen.ui.reps

sealed interface DistrictHint {
    data object None : DistrictHint
    data class Single(val district: Int) : DistrictHint
    data class Multiple(val districts: List<Int>) : DistrictHint
    data object Miss : DistrictHint
}

data class LocationPickerUiState(
    val selectedState: String? = null,
    val districtsForState: List<Int> = emptyList(),
    val isAtLargeOrDelegate: Boolean = false,
    val zipInput: String = "",
    val districtHint: DistrictHint = DistrictHint.None,
    val canSave: Boolean = false,
)
