"""Pure parsing functions for House and Senate session-day feeds.

Network I/O lives in build_session_calendar.py; this module is fully unit-
testable on raw text.

House feed: an iCalendar (.ics) document published by USHOR. We treat
events tagged ``Vote Day`` or ``Added Vote Day`` as session days. Other
categories (Travel Day, Pro Forma Session, Federal Holiday, Canceled
Vote Day, Non-federal Holiday) are excluded — the user-facing intent is
"today is a likely floor-activity day", which excludes pro-forma and
cancelled days.

Senate feed: an XML schedule that lists *non-legislative* periods rather
than session days. Session days are derived as ``weekdays in year``
minus ``days inside any <date> range``. The Senate occasionally meets
on weekends for cloture/filibuster votes, but the published schedule
treats such days as exceptions; for a planning indicator the weekday
rule is correct.
"""
from __future__ import annotations

import datetime as dt
import xml.etree.ElementTree as ET

HOUSE_VOTING_CATEGORIES: frozenset[str] = frozenset({"Vote Day", "Added Vote Day"})


def parse_house_ics(text: str) -> list[dt.date]:
    """Extract sorted, deduplicated House voting days from an iCalendar text."""
    days: set[dt.date] = set()
    in_event = False
    event_start: dt.date | None = None
    event_categories: set[str] = set()

    for raw_line in text.splitlines():
        line = raw_line.rstrip("\r")
        if line == "BEGIN:VEVENT":
            in_event = True
            event_start = None
            event_categories = set()
        elif line == "END:VEVENT":
            if in_event and event_start is not None and (event_categories & HOUSE_VOTING_CATEGORIES):
                days.add(event_start)
            in_event = False
        elif in_event:
            if line.startswith("DTSTART;VALUE=DATE:"):
                ymd = line.split(":", 1)[1].strip()
                if len(ymd) == 8 and ymd.isdigit():
                    event_start = dt.date(int(ymd[0:4]), int(ymd[4:6]), int(ymd[6:8]))
            elif line.startswith("CATEGORIES:"):
                values = line.split(":", 1)[1]
                event_categories.update(c.strip() for c in values.split(","))

    return sorted(days)


def parse_senate_xml(text: str) -> tuple[int, list[dt.date]]:
    """Derive Senate session days from a Senate Schedule XML document.

    Returns ``(year, session_days)``. Session days are weekdays in the
    schedule's ``<year>`` that are NOT inside any ``<date>`` range.
    """
    root = ET.fromstring(text)

    year_el = root.find("year")
    if year_el is None or not (year_el.text or "").strip():
        raise ValueError("Senate XML: missing <year>")
    year = int((year_el.text or "").strip())

    excluded: set[dt.date] = set()
    for d in root.findall(".//date"):
        begin_text = (d.findtext("beginDate") or "").strip()
        end_text = (d.findtext("endDate") or "").strip()
        if not begin_text or not end_text:
            continue
        begin = dt.date.fromisoformat(begin_text)
        end = dt.date.fromisoformat(end_text)
        if end < begin:
            raise ValueError(f"Senate XML: endDate {end} before beginDate {begin}")
        cur = begin
        while cur <= end:
            excluded.add(cur)
            cur += dt.timedelta(days=1)

    days: list[dt.date] = []
    cur = dt.date(year, 1, 1)
    end = dt.date(year, 12, 31)
    while cur <= end:
        if cur.weekday() < 5 and cur not in excluded:
            days.append(cur)
        cur += dt.timedelta(days=1)

    return year, days
