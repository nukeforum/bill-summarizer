"""Validate docs/data/session_calendar.json shape on disk.

The canonical producer of this file is build_session_calendar.py, which
fetches the House iCalendar feed and the Senate XML schedule and
writes the JSON. This script is a standalone shape-checker that's
useful for spot-validation outside the build pipeline (e.g. after a
manual edit, or when debugging a CI failure).

For the full producer workflow, see build_session_calendar.py.
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
