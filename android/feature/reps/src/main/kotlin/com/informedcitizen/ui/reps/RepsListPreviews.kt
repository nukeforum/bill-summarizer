package com.informedcitizen.ui.reps

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.informedcitizen.ui.preview.PreviewWrap
import com.informedcitizen.ui.preview.sampleRepresentative
import com.informedcitizen.ui.preview.sampleSenatorD
import com.informedcitizen.ui.preview.sampleSenatorR

@Preview(showBackground = true)
@Composable
private fun PreviewRepsListLoading() = PreviewWrap {
    RepsListContent(
        state = RepsListUiState.Loading,
        hasSeenWebsiteFallbackDialog = false,
        onMemberClick = {},
        onChangeLocation = {},
        onDeleteSavedReps = {},
        onCallPhone = {},
        onOpenUrl = {},
        onMarkWebsiteFallbackDialogSeen = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewRepsListNoLocation() = PreviewWrap {
    RepsListContent(
        state = RepsListUiState.NoLocation,
        hasSeenWebsiteFallbackDialog = false,
        onMemberClick = {},
        onChangeLocation = {},
        onDeleteSavedReps = {},
        onCallPhone = {},
        onOpenUrl = {},
        onMarkWebsiteFallbackDialogSeen = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewRepsListLoadedFull() = PreviewWrap {
    RepsListContent(
        state = RepsListUiState.Loaded(
            senators = listOf(sampleSenatorD, sampleSenatorR),
            house = listOf(sampleRepresentative),
        ),
        hasSeenWebsiteFallbackDialog = false,
        onMemberClick = {},
        onChangeLocation = {},
        onDeleteSavedReps = {},
        onCallPhone = {},
        onOpenUrl = {},
        onMarkWebsiteFallbackDialogSeen = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewRepsListLoadedEmptySenators() = PreviewWrap {
    RepsListContent(
        state = RepsListUiState.Loaded(
            senators = emptyList(),
            house = listOf(sampleRepresentative),
        ),
        hasSeenWebsiteFallbackDialog = false,
        onMemberClick = {},
        onChangeLocation = {},
        onDeleteSavedReps = {},
        onCallPhone = {},
        onOpenUrl = {},
        onMarkWebsiteFallbackDialogSeen = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewRepsListStaleSavedReps() = PreviewWrap {
    RepsListContent(
        state = RepsListUiState.StaleSavedReps,
        hasSeenWebsiteFallbackDialog = false,
        onMemberClick = {},
        onChangeLocation = {},
        onDeleteSavedReps = {},
        onCallPhone = {},
        onOpenUrl = {},
        onMarkWebsiteFallbackDialogSeen = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewRepsListError() = PreviewWrap {
    RepsListContent(
        state = RepsListUiState.Error(message = "Network unavailable."),
        hasSeenWebsiteFallbackDialog = false,
        onMemberClick = {},
        onChangeLocation = {},
        onDeleteSavedReps = {},
        onCallPhone = {},
        onOpenUrl = {},
        onMarkWebsiteFallbackDialogSeen = {},
    )
}
