"""Unit tests for the election-calendar builder (issue #23)."""
from __future__ import annotations

import datetime as dt

import pytest

from _election_calendar import (
    NATIONWIDE,
    build_election_calendar,
    federal_general_election_date,
    next_federal_general_election_year,
)

GEN_AT = "2026-07-22T00:00:00Z"


# --- statutory federal general date (mirrors ElectionDates.kt) -------------

@pytest.mark.parametrize(
    "year,expected",
    [
        (2026, dt.date(2026, 11, 3)),   # Nov 1 is a Sunday
        (2028, dt.date(2028, 11, 7)),   # Nov 1 is a Wednesday
        (2024, dt.date(2024, 11, 5)),
        (2016, dt.date(2016, 11, 8)),   # latest possible (Nov 1 is Tuesday)
        (2032, dt.date(2032, 11, 2)),   # earliest possible (Nov 1 is Monday)
    ],
)
def test_general_election_date_is_statutory(year, expected):
    assert federal_general_election_date(year) == expected


def test_general_date_is_always_a_tuesday():
    for year in range(2026, 2100, 2):
        assert federal_general_election_date(year).weekday() == 1  # Tuesday


def test_next_cycle_year_rolls_past_election_day():
    # Even year, before its election day → that year.
    assert next_federal_general_election_year(dt.date(2026, 1, 1)) == 2026
    # Even year, on election day → still that year.
    assert next_federal_general_election_year(dt.date(2026, 11, 3)) == 2026
    # Even year, past election day → next even year.
    assert next_federal_general_election_year(dt.date(2026, 11, 4)) == 2028
    # Odd year → next even year.
    assert next_federal_general_election_year(dt.date(2027, 6, 1)) == 2028


# --- calendar assembly -----------------------------------------------------

def test_general_row_always_present_and_statutory():
    cal = build_election_calendar(dt.date(2026, 7, 22), GEN_AT, curated_primaries=[])
    general = [e for e in cal["elections"] if e["type"] == "general"]
    assert len(general) == 1
    row = general[0]
    assert row["state"] == NATIONWIDE == "US"
    assert row["date"] == "2026-11-03"
    assert row["election_year"] == 2026
    assert "source" not in row  # falls back to calendar-level source


def test_top_level_shape_matches_wire_contract():
    cal = build_election_calendar(dt.date(2026, 7, 22), GEN_AT, curated_primaries=[])
    assert set(cal) == {"generated_at", "source", "elections"}
    assert cal["generated_at"] == GEN_AT
    assert isinstance(cal["source"], str) and cal["source"]


def test_valid_curated_primary_passes_through_with_source():
    primaries = [
        {"state": "TX", "date": "2026-03-03", "type": "primary",
         "election_year": 2026, "source": "Texas Election Code §41.007"},
    ]
    cal = build_election_calendar(dt.date(2026, 7, 22), GEN_AT, curated_primaries=primaries)
    tx = [e for e in cal["elections"] if e["state"] == "TX"]
    assert len(tx) == 1
    assert tx[0] == {
        "state": "TX", "date": "2026-03-03", "type": "primary",
        "election_year": 2026, "source": "Texas Election Code §41.007",
    }


def test_events_sorted_by_date_then_state():
    primaries = [
        {"state": "PA", "date": "2026-05-19", "type": "primary", "election_year": 2026},
        {"state": "OR", "date": "2026-05-19", "type": "primary", "election_year": 2026},
        {"state": "TX", "date": "2026-03-03", "type": "primary", "election_year": 2026},
    ]
    cal = build_election_calendar(dt.date(2026, 7, 22), GEN_AT, curated_primaries=primaries)
    ordering = [(e["date"], e["state"]) for e in cal["elections"]]
    assert ordering == sorted(ordering)
    # Same-date rows tie-break by state code (OR before PA).
    assert ordering == [
        ("2026-03-03", "TX"),
        ("2026-05-19", "OR"),
        ("2026-05-19", "PA"),
        ("2026-11-03", "US"),
    ]


@pytest.mark.parametrize(
    "bad",
    [
        {"state": "ZZ", "date": "2026-03-03", "type": "primary", "election_year": 2026},   # bad state
        {"state": "TX", "date": "not-a-date", "type": "primary", "election_year": 2026},   # bad date
        {"state": "TX", "date": "2026-03-03", "type": "general", "election_year": 2026},    # bad type
        {"state": "TX", "date": "2026-03-03", "type": "primary", "election_year": 2027},    # odd year
        {"state": "TX", "date": "2025-03-03", "type": "primary", "election_year": 2025},    # odd + stale
        {"state": "TX", "date": "2027-03-03", "type": "primary", "election_year": 2026},    # date/year mismatch
        "not-an-object",
    ],
)
def test_malformed_curated_row_is_dropped_not_published(bad):
    cal = build_election_calendar(dt.date(2026, 7, 22), GEN_AT, curated_primaries=[bad])
    # Only the statutory general row survives.
    assert [e["type"] for e in cal["elections"]] == ["general"]


def test_stale_cycle_primary_is_dropped():
    # A 2024 primary is past; the 2026 cycle is current.
    primaries = [
        {"state": "TX", "date": "2024-03-05", "type": "primary", "election_year": 2024},
        {"state": "OH", "date": "2026-05-05", "type": "primary", "election_year": 2026},
    ]
    cal = build_election_calendar(dt.date(2026, 7, 22), GEN_AT, curated_primaries=primaries)
    states = {e["state"] for e in cal["elections"]}
    assert states == {"US", "OH"}


def test_none_curated_publishes_general_only():
    cal = build_election_calendar(dt.date(2026, 7, 22), GEN_AT, curated_primaries=None)
    assert len(cal["elections"]) == 1
    assert cal["elections"][0]["type"] == "general"
