package com.informedcitizen.ui.reps

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.informedcitizen.ui.preview.PreviewWrap

@PreviewLightDark
@Composable
private fun PreviewLocationPickerPickEmpty() = PreviewWrap {
    LocationPickerContent(
        state = LocationPickerUiState(),
        onSelectState = {},
        onSelectMode = {},
        onSelectDistrict = {},
        onZipChanged = {},
        onLookupZip = {},
        onOpenHouseGov = {},
        onSave = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewLocationPickerPickWithDistricts() = PreviewWrap {
    LocationPickerContent(
        state = LocationPickerUiState(
            selectedState = "WA",
            districtsForState = (1..10).toList(),
        ),
        onSelectState = {},
        onSelectMode = {},
        onSelectDistrict = {},
        onZipChanged = {},
        onLookupZip = {},
        onOpenHouseGov = {},
        onSave = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewLocationPickerPickAtLarge() = PreviewWrap {
    LocationPickerContent(
        state = LocationPickerUiState(
            selectedState = "VT",
            isAtLargeOrDelegate = true,
            canSave = true,
        ),
        onSelectState = {},
        onSelectMode = {},
        onSelectDistrict = {},
        onZipChanged = {},
        onLookupZip = {},
        onOpenHouseGov = {},
        onSave = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewLocationPickerLookupEmpty() = PreviewWrap {
    LocationPickerContent(
        state = LocationPickerUiState(
            mode = LocationPickerMode.Lookup,
            selectedState = "WA",
        ),
        onSelectState = {},
        onSelectMode = {},
        onSelectDistrict = {},
        onZipChanged = {},
        onLookupZip = {},
        onOpenHouseGov = {},
        onSave = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewLocationPickerLookupSingleHint() = PreviewWrap {
    LocationPickerContent(
        state = LocationPickerUiState(
            mode = LocationPickerMode.Lookup,
            selectedState = "WA",
            zipInput = "98101",
            districtHint = DistrictHint.Single(district = 7),
            canSave = true,
        ),
        onSelectState = {},
        onSelectMode = {},
        onSelectDistrict = {},
        onZipChanged = {},
        onLookupZip = {},
        onOpenHouseGov = {},
        onSave = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewLocationPickerLookupMiss() = PreviewWrap {
    LocationPickerContent(
        state = LocationPickerUiState(
            mode = LocationPickerMode.Lookup,
            selectedState = "WA",
            zipInput = "00000",
            districtHint = DistrictHint.Miss,
        ),
        onSelectState = {},
        onSelectMode = {},
        onSelectDistrict = {},
        onZipChanged = {},
        onLookupZip = {},
        onOpenHouseGov = {},
        onSave = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewLocationPickerSaveFailed() = PreviewWrap {
    LocationPickerContent(
        state = LocationPickerUiState(
            selectedState = "WA",
            districtsForState = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
            districtHint = DistrictHint.SaveFailed,
            canSave = true,
        ),
        onSelectState = {},
        onSelectMode = {},
        onSelectDistrict = {},
        onZipChanged = {},
        onLookupZip = {},
        onOpenHouseGov = {},
        onSave = {},
    )
}

