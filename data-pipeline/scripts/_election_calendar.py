"""Election-calendar builder helpers (issue #23, epic #16 — "cast their vote").

Pure, testable logic behind ``build_election_calendar.py``. Emits the wire
shape of ``pipeline:shared``'s ``ElectionCalendar`` model
(``com.informedcitizen.pipeline.model.ElectionCalendar``):

    {"generated_at": ..., "source": ..., "elections": [ElectionEvent, ...]}

An ``ElectionEvent`` is ``{state, date, type, election_year, source?}``. The
one federal general-election row uses the ``NATIONWIDE`` sentinel state ``"US"``
and a statutory date (never curated — see ``federal_general_election_date``).
Per-state primary rows come from the operator-curated
``data/election_primaries.json`` and are validated here; a row that fails
validation is dropped with a warning rather than published, because a missing
date is safer than a wrong one (issue #23).
"""
from __future__ import annotations

import datetime as dt
import sys
from typing import Any, Iterable

from _common import _STATE_NAME_TO_CODE

# Sentinel state for the federal general election shared by every state.
# Mirrors ElectionEvent.NATIONWIDE in pipeline:shared.
NATIONWIDE = "US"

# Wire values of ElectionType in pipeline:shared. "general" is emitted only for
# the statutory nationwide row; curated rows must be one of the primary kinds.
_GENERAL = "general"
_CURATED_TYPES = frozenset({"primary", "primary_runoff"})

# Valid 2-letter postal codes: the states/territories _common already maps.
_VALID_STATE_CODES = frozenset(_STATE_NAME_TO_CODE.values())

_DEFAULT_SOURCE = (
    "Federal general-election date is statutory (2 U.S.C. §7, 3 U.S.C. §1); "
    "state primary dates are operator-curated in data/election_primaries.json."
)


def federal_general_election_date(year: int) -> dt.date:
    """The statutory U.S. federal general-election date: the Tuesday after the
    first Monday in November (2 U.S.C. §7, 3 U.S.C. §1). Deterministic — the
    highest-value date on the calendar is computed, never curated, so it is
    never wrong. Mirrors ``ElectionDates.federalGeneralElectionDate`` in KMP.
    """
    november1 = dt.date(year, 11, 1)
    # Days forward from Nov 1 to the first Monday (0 if Nov 1 is Monday).
    days_to_first_monday = (0 - november1.weekday()) % 7
    return november1 + dt.timedelta(days=days_to_first_monday + 1)


def next_federal_general_election_year(from_date: dt.date) -> int:
    """The even year of the next federal general election on or after
    ``from_date``. If ``from_date`` is in an even year but past that year's
    election day, the following even year is returned. Mirrors
    ``ElectionDates.nextFederalGeneralElectionYear``.
    """
    year = from_date.year if from_date.year % 2 == 0 else from_date.year + 1
    if from_date > federal_general_election_date(year):
        year += 2
    return year


def _validate_primary(row: Any, min_year: int) -> dict | None:
    """Coerce one curated row to a wire ``ElectionEvent`` dict, or return
    ``None`` (with a stderr warning) if it fails validation. Rows for cycles
    strictly before ``min_year`` are dropped silently as stale, not warned.
    """
    if not isinstance(row, dict):
        print(f"  ! skipping non-object primary row: {row!r}", file=sys.stderr)
        return None

    state = row.get("state")
    date = row.get("date")
    etype = row.get("type")
    year = row.get("election_year")

    if state not in _VALID_STATE_CODES:
        print(f"  ! skipping primary with unknown state {state!r}", file=sys.stderr)
        return None
    if etype not in _CURATED_TYPES:
        print(f"  ! skipping {state} primary with bad type {etype!r}", file=sys.stderr)
        return None
    if not isinstance(year, int) or year % 2 != 0:
        print(f"  ! skipping {state} primary with non-even election_year {year!r}", file=sys.stderr)
        return None
    if not isinstance(date, str):
        print(f"  ! skipping {state} primary with non-string date {date!r}", file=sys.stderr)
        return None
    try:
        parsed = dt.date.fromisoformat(date)
    except ValueError:
        print(f"  ! skipping {state} primary with unparseable date {date!r}", file=sys.stderr)
        return None
    if parsed.year != year:
        print(
            f"  ! skipping {state} primary: date {date} not in election_year {year}",
            file=sys.stderr,
        )
        return None
    if year < min_year:
        # Stale cycle — a past primary, not an error worth surfacing.
        return None

    event = {
        "state": state,
        "date": date,
        "type": etype,
        "election_year": year,
    }
    source = row.get("source")
    if isinstance(source, str) and source:
        event["source"] = source
    return event


def build_election_calendar(
    today: dt.date,
    generated_at: str,
    curated_primaries: Iterable[Any] | None = None,
    source: str = _DEFAULT_SOURCE,
) -> dict:
    """Build the ``election_calendar.json`` payload.

    Always includes the statutory nationwide general-election row for the next
    federal cycle (``next_federal_general_election_year(today)``). Merges any
    valid curated primary rows for that cycle or later. Events are sorted by
    ``(date, state)`` so the app can render them chronologically without
    re-sorting.
    """
    cycle_year = next_federal_general_election_year(today)
    general_date = federal_general_election_date(cycle_year)

    elections: list[dict] = [
        {
            "state": NATIONWIDE,
            "date": general_date.isoformat(),
            "type": _GENERAL,
            "election_year": cycle_year,
        }
    ]

    for row in curated_primaries or []:
        event = _validate_primary(row, min_year=cycle_year)
        if event is not None:
            elections.append(event)

    elections.sort(key=lambda e: (e["date"], e["state"]))

    return {
        "generated_at": generated_at,
        "source": source,
        "elections": elections,
    }
