"""Sanity checks for the published docs/data/election_calendar.json (issue #23)."""
from __future__ import annotations

import datetime as dt
import json
from pathlib import Path

import pytest

CALENDAR = Path(__file__).resolve().parents[2] / "docs" / "data" / "election_calendar.json"
_VALID_TYPES = {"general", "primary", "primary_runoff", "unknown"}


@pytest.fixture(scope="module")
def calendar():
    with CALENDAR.open() as f:
        return json.load(f)


def test_file_exists():
    assert CALENDAR.is_file(), f"missing: {CALENDAR}"


def test_top_level_shape(calendar):
    assert set(calendar) == {"generated_at", "source", "elections"}
    dt.datetime.fromisoformat(calendar["generated_at"].replace("Z", "+00:00"))
    assert isinstance(calendar["source"], str) and calendar["source"]


def test_events_well_formed_and_sorted(calendar):
    events = calendar["elections"]
    assert isinstance(events, list) and events
    for e in events:
        assert set(e) >= {"state", "date", "type", "election_year"}
        assert e["type"] in _VALID_TYPES
        dt.date.fromisoformat(e["date"])
        assert isinstance(e["election_year"], int) and e["election_year"] % 2 == 0
    ordering = [(e["date"], e["state"]) for e in events]
    assert ordering == sorted(ordering), "events not sorted by (date, state)"


def test_has_exactly_one_nationwide_general(calendar):
    general = [e for e in calendar["elections"] if e["type"] == "general"]
    assert len(general) == 1
    assert general[0]["state"] == "US"


def test_horizon_is_in_the_future(calendar):
    """Guards against forgetting the biennial refresh — the general row must
    still name an upcoming election."""
    today = dt.date.today().isoformat()
    assert any(e["date"] >= today for e in calendar["elections"]), (
        "no election on or after today; calendar horizon has lapsed"
    )
