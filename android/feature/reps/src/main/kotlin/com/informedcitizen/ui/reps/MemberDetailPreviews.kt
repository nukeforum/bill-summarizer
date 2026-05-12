package com.informedcitizen.ui.reps

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.informedcitizen.ui.preview.PreviewWrap
import com.informedcitizen.ui.preview.sampleLegislation
import com.informedcitizen.ui.preview.sampleSenatorD

@Preview(showBackground = true)
@Composable
private fun PreviewMemberDetailLoading() = PreviewWrap {
    MemberDetailContent(
        state = MemberDetailUiState(isLoading = true),
        innerPadding = PaddingValues(0.dp),
        onLegislationClick = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewMemberDetailLoaded() = PreviewWrap {
    MemberDetailContent(
        state = MemberDetailUiState(
            isLoading = false,
            member = sampleSenatorD,
            sponsored = sampleLegislation,
            cosponsored = sampleLegislation,
        ),
        innerPadding = PaddingValues(0.dp),
        onLegislationClick = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewMemberDetailLoadedEmpty() = PreviewWrap {
    MemberDetailContent(
        state = MemberDetailUiState(
            isLoading = false,
            member = sampleSenatorD,
            sponsored = emptyList(),
            cosponsored = emptyList(),
        ),
        innerPadding = PaddingValues(0.dp),
        onLegislationClick = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewMemberDetailError() = PreviewWrap {
    MemberDetailContent(
        state = MemberDetailUiState(
            isLoading = false,
            member = null,
            errorMessage = "Couldn't load this representative.",
        ),
        innerPadding = PaddingValues(0.dp),
        onLegislationClick = {},
    )
}

