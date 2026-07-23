package com.informedcitizen.ui.calendar

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.informedcitizen.pipeline.model.ElectionCalendar
import com.informedcitizen.pipeline.model.ElectionEvent
import com.informedcitizen.pipeline.model.ElectionType
import com.informedcitizen.pipeline.model.Member
import com.informedcitizen.pipeline.model.RegistrationDeadline
import com.informedcitizen.ui.preview.PreviewWrap
import com.informedcitizen.ui.preview.sampleSessionCalendar
import java.time.LocalDate

@Preview(showBackground = true)
@Composable
private fun PreviewSessionCalendarLoading() = PreviewWrap {
    SessionCalendarContent(
        state = SessionCalendarUiState.Loading,
        innerPadding = PaddingValues(0.dp),
        onRetry = {},
        onOpenSource = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewSessionCalendarSuccess() = PreviewWrap {
    SessionCalendarContent(
        state = SessionCalendarUiState.Success(calendar = sampleSessionCalendar()),
        innerPadding = PaddingValues(0.dp),
        onRetry = {},
        today = LocalDate.of(2026, 5, 8),
        electionCalendar = sampleElectionCalendar(),
        savedReps = sampleSavedReps(),
        onOpenSource = {},
    )
}

private fun sampleSavedReps() = listOf(
    Member(
        bioguideId = "S000001",
        name = "Jane Doe",
        party = "D",
        state = "NY",
        chamber = "house",
        nextElectionYear = 2026,
    ),
)

private fun sampleElectionCalendar() = ElectionCalendar(
    generatedAt = "2026-05-05T12:00:00Z",
    source = "Federal general-election date is statutory; state primary dates are operator-curated.",
    elections = listOf(
        ElectionEvent(
            state = "NY",
            date = "2026-06-23",
            type = ElectionType.PRIMARY,
            electionYear = 2026,
            source = "https://www.elections.ny.gov/",
            registration = RegistrationDeadline(
                online = "2026-06-13",
                byMail = "2026-06-13",
                inPerson = "2026-06-13",
                source = "https://www.elections.ny.gov/",
            ),
        ),
        ElectionEvent(
            state = ElectionEvent.NATIONWIDE,
            date = "2026-11-03",
            type = ElectionType.GENERAL,
            electionYear = 2026,
        ),
    ),
)

@PreviewLightDark
@Composable
private fun PreviewSessionCalendarError() = PreviewWrap {
    SessionCalendarContent(
        state = SessionCalendarUiState.Error(message = "Couldn't reach the calendar service."),
        innerPadding = PaddingValues(0.dp),
        onRetry = {},
        onOpenSource = {},
    )
}

