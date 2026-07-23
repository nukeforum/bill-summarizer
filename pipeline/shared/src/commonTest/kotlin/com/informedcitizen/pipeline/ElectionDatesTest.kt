package com.informedcitizen.pipeline

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ElectionDatesTest {
    @Test fun computes_statutory_general_election_dates() {
        // Tuesday after the first Monday in November — verified against the
        // real federal calendar.
        assertEquals(LocalDate(2024, 11, 5), federalGeneralElectionDate(2024))
        assertEquals(LocalDate(2026, 11, 3), federalGeneralElectionDate(2026))
        assertEquals(LocalDate(2028, 11, 7), federalGeneralElectionDate(2028))
        assertEquals(LocalDate(2030, 11, 5), federalGeneralElectionDate(2030))
    }

    @Test fun handles_month_starting_on_monday() {
        // Nov 1 2027 is a Monday, so the first Monday is Nov 1 and election
        // day is Nov 2 — the earliest the date can land.
        assertEquals(LocalDate(2027, 11, 2), federalGeneralElectionDate(2027))
    }

    @Test fun handles_month_starting_on_tuesday() {
        // Nov 1 2022 is a Tuesday; the first Monday is Nov 7, so election day
        // is Nov 8 — the latest the date can land.
        assertEquals(LocalDate(2022, 11, 8), federalGeneralElectionDate(2022))
    }

    @Test fun next_year_is_current_even_year_before_its_election() {
        assertEquals(2026, nextFederalGeneralElectionYear(LocalDate(2026, 7, 22)))
        assertEquals(2026, nextFederalGeneralElectionYear(LocalDate(2026, 11, 3)))
    }

    @Test fun next_year_rolls_forward_after_election_day() {
        assertEquals(2028, nextFederalGeneralElectionYear(LocalDate(2026, 11, 4)))
        assertEquals(2028, nextFederalGeneralElectionYear(LocalDate(2027, 1, 1)))
    }
}
