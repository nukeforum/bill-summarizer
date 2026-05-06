"""Build docs/data/session_calendar.json from upstream feeds.

Sources:
  - House: USHOR voting-days iCalendar feed
           https://votingdays.house.gov/voting-days.ics
  - Senate: published XML schedule for the current year
           https://www.senate.gov/legislative/{YEAR}_schedule.xml

Both feeds are official `.gov` publications. The Senate XML covers a
single year and is replaced each November when leadership announces the
following year's tentative schedule, so this script will need to walk
candidate years (current + previous + next) and merge what's available.

Run locally:
    python data-pipeline/scripts/build_session_calendar.py

The script writes the JSON, prints a one-line summary to stdout, and
exits non-zero on any unrecoverable error (HTTP failure, XML parse
failure, empty calendar). The GitHub Action wraps this in a workflow
that commits the file when it changes.
"""
from __future__ import annotations

import datetime as dt
import json
import sys
from pathlib import Path

import requests

from _session_calendar import parse_house_ics, parse_senate_xml

REPO_ROOT = Path(__file__).resolve().parents[2]
OUTPUT = REPO_ROOT / "docs" / "data" / "session_calendar.json"

HOUSE_URL = "https://votingdays.house.gov/voting-days.ics"
SENATE_URL_TEMPLATE = "https://www.senate.gov/legislative/{year}_schedule.xml"

USER_AGENT = "informed-citizen-pipeline (+https://github.com/nukeforum/bill-summarizer)"
TIMEOUT_SECONDS = 30


def _fetch(url: str) -> str:
    response = requests.get(
        url,
        headers={"User-Agent": USER_AGENT},
        timeout=TIMEOUT_SECONDS,
    )
    response.raise_for_status()
    response.encoding = "utf-8"
    return response.text


def _candidate_senate_years(today: dt.date) -> list[int]:
    """Years to attempt in order. Senate XMLs disappear once superseded,
    but we want to combine prior-year tail and next-year head when available
    to cover Congress sessions that straddle the calendar year boundary.
    """
    return [today.year - 1, today.year, today.year + 1]


def _fetch_senate_session_days(today: dt.date) -> list[dt.date]:
    days: set[dt.date] = set()
    last_error: Exception | None = None
    fetched_years: list[int] = []
    for year in _candidate_senate_years(today):
        url = SENATE_URL_TEMPLATE.format(year=year)
        try:
            text = _fetch(url)
        except requests.HTTPError as exc:
            # 404 is expected for years that haven't been published yet or
            # have been removed. Other errors propagate.
            if exc.response is not None and exc.response.status_code == 404:
                continue
            last_error = exc
            continue
        except requests.RequestException as exc:
            last_error = exc
            continue
        try:
            parsed_year, parsed_days = parse_senate_xml(text)
        except (ValueError, Exception) as exc:  # noqa: BLE001 — surface parse errors
            last_error = exc
            continue
        if parsed_year != year:
            # Schedule's <year> doesn't match the URL — treat as invalid.
            continue
        days.update(parsed_days)
        fetched_years.append(year)

    if not days:
        if last_error is not None:
            raise RuntimeError(
                f"Could not fetch any Senate schedule (last error: {last_error})"
            ) from last_error
        raise RuntimeError("Senate fetch returned no session days from any candidate year")

    print(f"Senate: parsed {len(days)} session days from years {fetched_years}", file=sys.stderr)
    return sorted(days)


def _fetch_house_session_days() -> list[dt.date]:
    text = _fetch(HOUSE_URL)
    days = parse_house_ics(text)
    if not days:
        raise RuntimeError("House ICS returned no Vote Day events")
    print(f"House: parsed {len(days)} voting days", file=sys.stderr)
    return days


def build(today: dt.date | None = None) -> dict:
    today = today or dt.datetime.now(dt.timezone.utc).date()
    house_days = _fetch_house_session_days()
    senate_days = _fetch_senate_session_days(today)
    return {
        "generated_at": dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "source": {
            "house": HOUSE_URL,
            "senate": SENATE_URL_TEMPLATE.format(year=today.year),
        },
        "chambers": {
            "house": {"session_days": [d.isoformat() for d in house_days]},
            "senate": {"session_days": [d.isoformat() for d in senate_days]},
        },
    }


def main() -> int:
    try:
        manifest = build()
    except Exception as exc:  # noqa: BLE001 — top-level reporter
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    house_count = len(manifest["chambers"]["house"]["session_days"])
    senate_count = len(manifest["chambers"]["senate"]["session_days"])
    print(f"OK: wrote {OUTPUT} (House: {house_count}, Senate: {senate_count})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
