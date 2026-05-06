"""Validation entry point for docs/data/session_calendar.json.

This script does NOT auto-fetch from upstream. The session calendar is
hand-curated annually from the published sources because the upstream
pages are HTML/PDF publications, not machine APIs, and rewriting once a
year is cheaper than maintaining a scraper across format changes.

Refresh workflow:

    1. Open the published calendars:
         House:  https://www.majorityleader.gov/calendar
         Senate: https://www.senate.gov/legislative/2026_schedule.htm
       (Replace 2026 in the Senate URL with the new year.)

    2. Append the new year's session dates to each chamber's
       `session_days` array in docs/data/session_calendar.json. Keep the
       array sorted ISO-8601, no duplicates. Both chambers should be
       refreshed in the same PR.

    3. Update generated_at to the current UTC timestamp.

    4. Run this script to validate, then `pytest tests/test_session_calendar.py`.

    5. Commit and open a PR.
"""
from __future__ import annotations

import datetime as dt
import json
import sys
from pathlib import Path

CALENDAR = Path(__file__).resolve().parents[2] / "docs" / "data" / "session_calendar.json"


def main() -> int:
    if not CALENDAR.is_file():
        print(f"missing: {CALENDAR}", file=sys.stderr)
        return 1

    with CALENDAR.open() as f:
        data = json.load(f)

    problems: list[str] = []

    if not isinstance(data.get("generated_at"), str):
        problems.append("generated_at is not a string")
    else:
        try:
            dt.datetime.fromisoformat(data["generated_at"].replace("Z", "+00:00"))
        except ValueError:
            problems.append("generated_at does not parse as ISO-8601")

    chambers = data.get("chambers", {})
    for chamber in ("house", "senate"):
        days = chambers.get(chamber, {}).get("session_days")
        if not isinstance(days, list) or not days:
            problems.append(f"{chamber}: session_days missing or empty")
            continue
        try:
            parsed = [dt.date.fromisoformat(d) for d in days]
        except (TypeError, ValueError) as exc:
            problems.append(f"{chamber}: malformed date ({exc})")
            continue
        if parsed != sorted(parsed):
            problems.append(f"{chamber}: session_days not sorted")
        if len(parsed) != len(set(parsed)):
            problems.append(f"{chamber}: session_days has duplicates")

    if problems:
        for p in problems:
            print(f"ERROR: {p}", file=sys.stderr)
        return 1

    print(f"OK: {CALENDAR} is valid.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
