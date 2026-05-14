"""Assert published pipeline artifacts are fresh; exit 1 if anything is stale.

Run from a scheduled workflow so a quiet failure (a workflow not running, a
silent API outage, a stuck backfill cursor) becomes a noisy GitHub Actions
notification rather than data silently aging on the Pages site.

Checks (each independently emits a line):

* Current-Congress bills manifest ``generated_at`` is within
  ``BILLS_MAX_AGE_DAYS``.
* Members index ``generated_at`` is within ``MEMBERS_MAX_AGE_DAYS``.
* Session calendar's latest House and Senate session day is at least
  ``CALENDAR_MIN_LOOKAHEAD_DAYS`` ahead of today (so the bills list's
  "session" line never reads "session has ended" for users).
* ``backfill_state.json.last_run_at`` advanced within
  ``BACKFILL_MAX_AGE_DAYS`` — unless the backfill queue is empty, in which
  case the cursor is allowed to be stale.
"""

from __future__ import annotations

import json
import sys
from datetime import date, datetime, timezone
from pathlib import Path

from _common import (
    OUTPUT_DIR,
    STATE_DIR,
    current_congress,
    manifest_path_for,
    members_index_path,
)

BILLS_MAX_AGE_DAYS = 2
MEMBERS_MAX_AGE_DAYS = 14
CALENDAR_MIN_LOOKAHEAD_DAYS = 30
BACKFILL_MAX_AGE_DAYS = 3


def _parse_iso_utc(value: str | None) -> datetime | None:
    if not isinstance(value, str) or not value:
        return None
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None


def _load_json(path: Path) -> dict | None:
    if not path.is_file():
        return None
    try:
        with path.open("r", encoding="utf-8") as f:
            return json.load(f)
    except (OSError, json.JSONDecodeError):
        return None


def check(now: datetime | None = None) -> list[str]:
    """Return a list of failure messages; empty list means everything is fresh."""
    now = now or datetime.now(timezone.utc)
    today = now.date()
    failures: list[str] = []

    # 1. Current-Congress bills manifest freshness.
    congress = current_congress(now)
    bills_path = manifest_path_for(congress)
    bills = _load_json(bills_path)
    if bills is None:
        failures.append(f"bills: {bills_path.name} missing or unreadable")
    else:
        ts = _parse_iso_utc(bills.get("generated_at"))
        if ts is None:
            failures.append(f"bills: {bills_path.name} has no parseable generated_at")
        elif (now - ts).days >= BILLS_MAX_AGE_DAYS:
            failures.append(
                f"bills: {bills_path.name} generated_at={bills['generated_at']} "
                f"is older than {BILLS_MAX_AGE_DAYS} days"
            )

    # 2. Members index freshness.
    members_path = members_index_path(congress)
    members = _load_json(members_path)
    if members is None:
        failures.append(f"members: {members_path.name} missing or unreadable")
    else:
        ts = _parse_iso_utc(members.get("generated_at"))
        if ts is None:
            failures.append(f"members: {members_path.name} has no parseable generated_at")
        elif (now - ts).days >= MEMBERS_MAX_AGE_DAYS:
            failures.append(
                f"members: {members_path.name} generated_at={members['generated_at']} "
                f"is older than {MEMBERS_MAX_AGE_DAYS} days"
            )

    # 3. Session calendar look-ahead per chamber.
    cal_path = OUTPUT_DIR / "session_calendar.json"
    cal = _load_json(cal_path)
    if cal is None:
        failures.append(f"calendar: {cal_path.name} missing or unreadable")
    else:
        chambers = cal.get("chambers") or {}
        for chamber in ("house", "senate"):
            days = (chambers.get(chamber) or {}).get("session_days") or []
            future_days = [d for d in days if isinstance(d, str) and d >= today.isoformat()]
            if not future_days:
                failures.append(
                    f"calendar: {chamber} has no session days on or after {today}"
                )
                continue
            try:
                last = date.fromisoformat(max(future_days))
            except ValueError:
                failures.append(f"calendar: {chamber} last future day is malformed")
                continue
            if (last - today).days < CALENDAR_MIN_LOOKAHEAD_DAYS:
                failures.append(
                    f"calendar: {chamber} last known session day {last} is less than "
                    f"{CALENDAR_MIN_LOOKAHEAD_DAYS} days out; upstream feed needs refresh"
                )

    # 4. Backfill cursor advancement (only if there's still work queued).
    state_path = STATE_DIR / "backfill_state.json"
    state = _load_json(state_path)
    if state is None:
        # No state file is acceptable on a brand-new repo; not a failure here.
        pass
    elif state.get("active_congress") is not None:
        ts = _parse_iso_utc(state.get("last_run_at"))
        if ts is None:
            failures.append("backfill: state has no parseable last_run_at")
        elif (now - ts).days >= BACKFILL_MAX_AGE_DAYS:
            failures.append(
                f"backfill: last_run_at={state['last_run_at']} is older than "
                f"{BACKFILL_MAX_AGE_DAYS} days"
            )

    return failures


def main() -> int:
    failures = check()
    if failures:
        print("Pipeline freshness check FAILED:", file=sys.stderr)
        for line in failures:
            print(f"  - {line}", file=sys.stderr)
        return 1
    print("Pipeline freshness check OK.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
