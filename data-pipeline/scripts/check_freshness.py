"""Assert published pipeline artifacts are fresh; exit 1 if anything is stale.

Run from a scheduled workflow so a quiet failure (a workflow not running, a
silent API outage, a stuck backfill cursor) becomes a noisy GitHub Actions
notification rather than data silently aging on the Pages site.

Checks (each independently emits a line):

* Current-Congress bills manifest ``generated_at`` is within
  ``BILLS_MAX_AGE_DAYS``.
* Members index ``generated_at`` is within ``MEMBERS_MAX_AGE_DAYS``.
* Votes index ``generated_at`` is within ``VOTES_MAX_AGE_DAYS`` (the index is
  rebuilt on every ``fetch_votes.py`` run, so a stale timestamp means the
  update-votes workflow has stopped running).
* Session calendar's latest House and Senate session day is at least
  ``CALENDAR_MIN_LOOKAHEAD_DAYS`` ahead of today (so the bills list's
  "session" line never reads "session has ended" for users).
* Election calendar names at least one election on or after today, so its
  federal-general horizon never lapses (issue #23). Tolerated if absent.
* No upcoming election carries a registration deadline that has already
  passed (issue #35): a published deadline dated before today while its
  election is still in the future is stale by definition — the app would
  count down to an election the voter can no longer register for.
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
from _election_calendar import _REGISTRATION_DATE_FIELDS

BILLS_MAX_AGE_DAYS = 2
MEMBERS_MAX_AGE_DAYS = 14
VOTES_MAX_AGE_DAYS = 2
CALENDAR_MIN_LOOKAHEAD_DAYS = 30
BACKFILL_MAX_AGE_DAYS = 3
# The election calendar must always name a real upcoming election. Its horizon
# is far out (the next federal general is up to ~2 years away), so unlike the
# session calendar this guards that the horizon hasn't lapsed entirely, not a
# rolling look-ahead window: fail once every published event is in the past.
ELECTION_CALENDAR_MIN_LOOKAHEAD_DAYS = 1


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

    # 3. Votes index freshness.
    votes_path = OUTPUT_DIR / f"congress{congress}_votes.json"
    votes = _load_json(votes_path)
    if votes is None:
        failures.append(f"votes: {votes_path.name} missing or unreadable")
    else:
        ts = _parse_iso_utc(votes.get("generated_at"))
        if ts is None:
            failures.append(f"votes: {votes_path.name} has no parseable generated_at")
        elif (now - ts).days >= VOTES_MAX_AGE_DAYS:
            failures.append(
                f"votes: {votes_path.name} generated_at={votes['generated_at']} "
                f"is older than {VOTES_MAX_AGE_DAYS} days"
            )

    # 4. Session calendar look-ahead per chamber.
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

    # 4b. Election calendar horizon: at least one election on or after today.
    election_path = OUTPUT_DIR / "election_calendar.json"
    election = _load_json(election_path)
    if election is None:
        # The election calendar is a newer artifact (issue #23); absence is
        # tolerated until its workflow is live, rather than failing every run.
        pass
    else:
        events = election.get("elections") or []
        future_dates = [
            e["date"]
            for e in events
            if isinstance(e, dict) and isinstance(e.get("date"), str) and e["date"] >= today.isoformat()
        ]
        if not future_dates:
            failures.append(
                f"election: {election_path.name} has no election on or after {today}; "
                "the federal general horizon needs advancing"
            )
        else:
            try:
                last = date.fromisoformat(max(future_dates))
            except ValueError:
                failures.append(f"election: {election_path.name} latest future date is malformed")
            else:
                if (last - today).days < ELECTION_CALENDAR_MIN_LOOKAHEAD_DAYS:
                    failures.append(
                        f"election: {election_path.name} horizon {last} is less than "
                        f"{ELECTION_CALENDAR_MIN_LOOKAHEAD_DAYS} days out"
                    )

        # 4c. Registration-deadline lookahead (issue #35). For every election
        # still upcoming (date on or after today), any published method deadline
        # dated before today is stale — the voter can no longer act on it, yet
        # the app would still count down to that election. Fail so the operator
        # advances or removes the deadline. same_day carries no date and is exempt.
        for event in events:
            if not isinstance(event, dict):
                continue
            event_date = event.get("date")
            if not isinstance(event_date, str) or event_date < today.isoformat():
                continue
            reg = event.get("registration")
            if not isinstance(reg, dict):
                continue
            passed = []
            for field in _REGISTRATION_DATE_FIELDS:
                value = reg.get(field)
                if isinstance(value, str) and value < today.isoformat():
                    passed.append(f"{field}={value}")
            if passed:
                state = event.get("state", "?")
                failures.append(
                    f"election: {state} {event_date} election has passed registration "
                    f"deadline(s) {', '.join(passed)} before today {today}; "
                    "the curated deadline is stale and must be advanced or removed"
                )

    # 5. Backfill cursor advancement (only if there's still work queued).
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
