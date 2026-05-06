"""Parser tests for House .ics and Senate .xml feeds."""
from __future__ import annotations

import datetime as dt
from pathlib import Path

import pytest

from _session_calendar import parse_house_ics, parse_senate_xml

FIXTURES = Path(__file__).parent / "fixtures"


# ---------------------------------------------------------------- house ICS

def test_house_ics_returns_sorted_unique_dates():
    text = (FIXTURES / "house_voting_days.ics").read_text(encoding="utf-8")
    days = parse_house_ics(text)
    assert days, "expected at least one Vote Day in fixture"
    assert days == sorted(days)
    assert len(days) == len(set(days))


def test_house_ics_excludes_non_voting_categories():
    """Pro Forma, Travel Day, and Holiday events must not appear."""
    text = (FIXTURES / "house_voting_days.ics").read_text(encoding="utf-8")
    days = parse_house_ics(text)
    # Feb 17, 2026 is a Pro Forma Session in the fixture — must be excluded.
    assert dt.date(2026, 2, 17) not in days
    # Memorial Day 2026 (May 25) is a federal holiday — must be excluded.
    assert dt.date(2026, 5, 25) not in days
    # Sep 11, 2026 is a Rosh Hashanah informational event — must be excluded.
    assert dt.date(2026, 9, 11) not in days


def test_house_ics_includes_known_vote_day():
    text = (FIXTURES / "house_voting_days.ics").read_text(encoding="utf-8")
    days = parse_house_ics(text)
    # Dec 14, 2026 is in the fixture as a Vote Day.
    assert dt.date(2026, 12, 14) in days


def test_house_ics_includes_added_vote_day():
    """Added Vote Day events count toward voting days."""
    ics = """\
BEGIN:VCALENDAR
VERSION:2.0
BEGIN:VEVENT
UID:test
DTSTART;VALUE=DATE:20260601
SUMMARY:🏛️ Vote Day
CATEGORIES:Added Vote Day
END:VEVENT
END:VCALENDAR
"""
    assert parse_house_ics(ics) == [dt.date(2026, 6, 1)]


def test_house_ics_excludes_canceled_vote_day():
    ics = """\
BEGIN:VCALENDAR
BEGIN:VEVENT
DTSTART;VALUE=DATE:20260602
CATEGORIES:Canceled Vote Day
END:VEVENT
END:VCALENDAR
"""
    assert parse_house_ics(ics) == []


def test_house_ics_handles_crlf_line_endings():
    ics = (
        "BEGIN:VCALENDAR\r\n"
        "BEGIN:VEVENT\r\n"
        "DTSTART;VALUE=DATE:20260603\r\n"
        "CATEGORIES:Vote Day\r\n"
        "END:VEVENT\r\n"
        "END:VCALENDAR\r\n"
    )
    assert parse_house_ics(ics) == [dt.date(2026, 6, 3)]


def test_house_ics_empty_returns_empty_list():
    assert parse_house_ics("") == []


# ---------------------------------------------------------------- senate XML

def test_senate_xml_returns_year_and_sorted_unique_dates():
    text = (FIXTURES / "senate_schedule_2026.xml").read_text(encoding="utf-8")
    year, days = parse_senate_xml(text)
    assert year == 2026
    assert days, "expected at least one session day in fixture"
    assert days == sorted(days)
    assert len(days) == len(set(days))


def test_senate_xml_excludes_recess_periods():
    text = (FIXTURES / "senate_schedule_2026.xml").read_text(encoding="utf-8")
    _, days = parse_senate_xml(text)
    # Jan 19-23, 2026 is a State Work Period — excluded.
    for d in (dt.date(2026, 1, 19), dt.date(2026, 1, 20), dt.date(2026, 1, 23)):
        assert d not in days
    # Aug 10 - Sep 11, 2026 is the long August recess — excluded.
    assert dt.date(2026, 8, 24) not in days


def test_senate_xml_excludes_weekends():
    text = (FIXTURES / "senate_schedule_2026.xml").read_text(encoding="utf-8")
    _, days = parse_senate_xml(text)
    for d in days:
        assert d.weekday() < 5, f"{d} is a weekend"


def test_senate_xml_includes_a_known_session_day():
    """May 11, 2026 is a Monday outside any recess in the fixture."""
    text = (FIXTURES / "senate_schedule_2026.xml").read_text(encoding="utf-8")
    _, days = parse_senate_xml(text)
    assert dt.date(2026, 5, 11) in days


def test_senate_xml_single_day_recess_inclusive():
    """A <date> with begin==end excludes that single day."""
    xml = """<?xml version="1.0"?><schedule>
        <year>2026</year>
        <dates>
            <date>
                <beginDate>2026-09-21</beginDate>
                <endDate>2026-09-21</endDate>
                <action/>
                <note/>
            </date>
        </dates>
    </schedule>"""
    _, days = parse_senate_xml(xml)
    assert dt.date(2026, 9, 21) not in days
    assert dt.date(2026, 9, 22) in days  # next weekday is in session


def test_senate_xml_missing_year_raises():
    xml = """<?xml version="1.0"?><schedule><dates/></schedule>"""
    with pytest.raises(ValueError, match="<year>"):
        parse_senate_xml(xml)


def test_senate_xml_no_recesses_yields_all_weekdays():
    xml = """<?xml version="1.0"?><schedule>
        <year>2026</year>
        <dates/>
    </schedule>"""
    year, days = parse_senate_xml(xml)
    assert year == 2026
    # 2026 has 261 weekdays.
    expected_weekdays = sum(
        1
        for n in range(365)
        if (dt.date(2026, 1, 1) + dt.timedelta(days=n)).weekday() < 5
    )
    assert len(days) == expected_weekdays
