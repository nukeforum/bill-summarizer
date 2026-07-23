"""Build docs/data/election_calendar.json (issue #23, epic #16).

Publishes the wire contract defined by ``pipeline:shared``'s
``ElectionCalendar`` model: the statutory federal general-election date for the
next federal cycle (shared across all states via the ``US`` sentinel) plus the
operator-curated per-state primary dates from ``data/election_primaries.json``.

The federal general date is computed, not curated, so the highest-value date is
never wrong. Primary dates are hand-verified in the curated file because a wrong
date is worse than a missing one (issue #23); malformed curated rows are dropped
with a warning rather than published.

Run locally (no API key needed — this uses no network):
    python data-pipeline/scripts/build_election_calendar.py

Writes the JSON, prints a one-line summary, and exits non-zero on any
unrecoverable error. A scheduled GitHub Action wraps this and commits the file
when it changes; check_freshness.py guards the calendar's horizon.
"""
from __future__ import annotations

import datetime as dt
import json
import sys
from pathlib import Path

from _election_calendar import build_election_calendar

REPO_ROOT = Path(__file__).resolve().parents[2]
OUTPUT = REPO_ROOT / "docs" / "data" / "election_calendar.json"
CURATED_PRIMARIES = REPO_ROOT / "data-pipeline" / "data" / "election_primaries.json"


def _load_curated_primaries(path: Path) -> list:
    """Load the curated primaries list. A missing file is tolerated (the
    calendar still publishes the statutory general row); a present-but-broken
    file is an error so the operator notices, rather than silently shipping an
    empty primary set.
    """
    if not path.is_file():
        print(f"  ! curated primaries file {path} missing; publishing general only", file=sys.stderr)
        return []
    with path.open("r", encoding="utf-8") as f:
        data = json.load(f)
    primaries = data.get("primaries")
    if not isinstance(primaries, list):
        raise ValueError(f"{path} has no 'primaries' list")
    return primaries


def build(today: dt.date | None = None) -> dict:
    today = today or dt.datetime.now(dt.timezone.utc).date()
    generated_at = dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    curated = _load_curated_primaries(CURATED_PRIMARIES)
    return build_election_calendar(today=today, generated_at=generated_at, curated_primaries=curated)


def main() -> int:
    try:
        calendar = build()
    except Exception as exc:  # noqa: BLE001 — top-level reporter
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(calendar, indent=2) + "\n", encoding="utf-8")
    total = len(calendar["elections"])
    primaries = sum(1 for e in calendar["elections"] if e["type"] != "general")
    print(f"OK: wrote {OUTPUT} ({total} events, {primaries} curated primaries)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
