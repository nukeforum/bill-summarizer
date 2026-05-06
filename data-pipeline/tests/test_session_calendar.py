"""Sanity checks for docs/data/session_calendar.json."""
from __future__ import annotations

import datetime as dt
import json
from pathlib import Path

import pytest

CALENDAR = Path(__file__).resolve().parents[2] / "docs" / "data" / "session_calendar.json"


@pytest.fixture(scope="module")
def calendar():
    with CALENDAR.open() as f:
        return json.load(f)


def test_file_exists():
    assert CALENDAR.is_file(), f"missing: {CALENDAR}"


def test_top_level_shape(calendar):
    assert set(calendar) >= {"generated_at", "source", "chambers"}
    assert set(calendar["source"]) == {"house", "senate"}
    assert set(calendar["chambers"]) == {"house", "senate"}


def test_generated_at_parses(calendar):
    dt.datetime.fromisoformat(calendar["generated_at"].replace("Z", "+00:00"))


@pytest.mark.parametrize("chamber", ["house", "senate"])
def test_session_days_well_formed(calendar, chamber):
    days = calendar["chambers"][chamber]["session_days"]
    assert isinstance(days, list) and days, f"{chamber}: empty"
    parsed = [dt.date.fromisoformat(d) for d in days]
    assert parsed == sorted(parsed), f"{chamber}: not sorted"
    assert len(parsed) == len(set(parsed)), f"{chamber}: duplicates"


@pytest.mark.parametrize("chamber", ["house", "senate"])
def test_has_current_year_dates(calendar, chamber):
    """Guards against forgetting the annual refresh."""
    this_year = dt.date.today().year
    days = [dt.date.fromisoformat(d) for d in calendar["chambers"][chamber]["session_days"]]
    assert any(d.year == this_year for d in days), (
        f"{chamber}: no session days in {this_year}; calendar refresh overdue"
    )
