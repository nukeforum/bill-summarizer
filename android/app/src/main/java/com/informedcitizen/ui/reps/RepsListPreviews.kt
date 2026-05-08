package com.informedcitizen.ui.reps

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.informedcitizen.ui.preview.MaterialPreviewTheme
import com.informedcitizen.ui.preview.sampleRepresentative
import com.informedcitizen.ui.preview.sampleSenatorD
import com.informedcitizen.ui.preview.sampleSenatorR

@Preview(showBackground = true)
@Composable
private fun PreviewRepsListLoading() = PreviewWrap {
    RepsListContent(
        state = RepsListUiState.Loading,
        onMemberClick = {},
        onChangeLocation = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewRepsListNoLocation() = PreviewWrap {
    RepsListContent(
        state = RepsListUiState.NoLocation,
        onMemberClick = {},
        onChangeLocation = {},
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
        onMemberClick = {},
        onChangeLocation = {},
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
        onMemberClick = {},
        onChangeLocation = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewRepsListStaleSavedReps() = PreviewWrap {
    RepsListContent(
        state = RepsListUiState.StaleSavedReps,
        onMemberClick = {},
        onChangeLocation = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewRepsListError() = PreviewWrap {
    RepsListContent(
        state = RepsListUiState.Error(message = "Network unavailable."),
        onMemberClick = {},
        onChangeLocation = {},
    )
}

@Composable
private fun PreviewWrap(content: @Composable () -> Unit) {
    MaterialPreviewTheme {
        Surface(modifier = Modifier.fillMaxSize()) { content() }
    }
}
