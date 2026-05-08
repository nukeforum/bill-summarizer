package com.informedcitizen.ui.calendar

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.informedcitizen.ui.preview.MaterialPreviewTheme
import com.informedcitizen.ui.preview.sampleSessionCalendar

@Preview(showBackground = true)
@Composable
private fun PreviewSessionCalendarLoading() = PreviewWrap {
    SessionCalendarContent(
        state = SessionCalendarUiState.Loading,
        innerPadding = PaddingValues(0.dp),
        onRetry = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewSessionCalendarSuccess() = PreviewWrap {
    SessionCalendarContent(
        state = SessionCalendarUiState.Success(calendar = sampleSessionCalendar()),
        innerPadding = PaddingValues(0.dp),
        onRetry = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewSessionCalendarError() = PreviewWrap {
    SessionCalendarContent(
        state = SessionCalendarUiState.Error(message = "Couldn't reach the calendar service."),
        innerPadding = PaddingValues(0.dp),
        onRetry = {},
    )
}

@Composable
private fun PreviewWrap(content: @Composable () -> Unit) {
    MaterialPreviewTheme {
        Surface(modifier = Modifier.fillMaxSize()) { content() }
    }
}
