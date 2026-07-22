"""Fetch roll-call votes (both chambers) and publish per-vote JSON plus a per-Congress index.

Both sources are keyless: Senate votes come from senate.gov LIS XML (menu +
detail documents), House votes from clerk.house.gov EVS XML. The House
publishes no machine-readable vote menu, so new House rolls are discovered by
probing sequentially past the rolls already on disk until the first 404 (roll
numbers are dense per calendar-year session).

Layout under docs/data/ (matches the RollCallVote/VotesIndex wire models in
pipeline:shared):

    votes/congress119/senate-1-618.json   one file per roll call, with positions
    votes/congress119/house-1-17.json
    congress119_votes.json                index: every vote minus positions

Runs are incremental and self-resuming: a roll call already on disk is never
refetched (published roll calls are immutable), so each run costs the Senate
vote menu(s), the two legislators YAMLs, one detail XML per *new* vote, and a
handful of House probe fetches. The index is rebuilt from the files on disk at
the end of every run, so a crashed run heals on the next one.

Run locally (no key required):
    python data-pipeline/scripts/fetch_votes.py
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

import requests

import _common
from _common import ErrorCollector, LEGISLATORS_CURRENT_YAML_URL, current_congress, now_iso
from _votes import (
    CHAMBER_HOUSE,
    CHAMBER_SENATE,
    UnsupportedVoteError,
    build_vote_ref,
    build_votes_index,
    house_vote_source_url,
    parse_house_vote,
    parse_lis_to_bioguide_yaml,
    parse_senate_vote,
    parse_senate_vote_menu,
    senate_vote_menu_url,
    senate_vote_source_url,
    vote_file_relpath,
)

LEGISLATORS_HISTORICAL_YAML_URL = (
    "https://raw.githubusercontent.com/"
    "unitedstates/congress-legislators/main/legislators-historical.yaml"
)

# Both sessions are always attempted; the menu for a session that hasn't
# started yet 404s and is skipped (same pattern as the Senate schedule walk
# in build_session_calendar.py).
SENATE_SESSIONS = (1, 2)

# House sessions are probed the same way: a session that hasn't started 404s
# on its first roll and costs a single fetch.
HOUSE_SESSIONS = (1, 2)

# A clerk.house.gov outage or format drift looks like a run of consecutive
# parse failures; a single bad roll has parseable successors that reset the
# counter. The cap keeps a 200-with-error-page response from probing forever.
MAX_CONSECUTIVE_HOUSE_ERRORS = 3

TIMEOUT_SECONDS = 30


def _fetch(url: str) -> str:
    response = requests.get(
        url,
        headers={"User-Agent": _common.USER_AGENT},
        timeout=TIMEOUT_SECONDS,
    )
    response.raise_for_status()
    response.encoding = "utf-8"
    return response.text


# ---------- IO (paths resolve against _common.OUTPUT_DIR at call time) -----


def votes_dir(congress: int) -> Path:
    return _common.OUTPUT_DIR / "votes" / f"congress{congress}"


def vote_path(congress: int, chamber: str, session: int, roll_number: int) -> Path:
    return _common.OUTPUT_DIR / vote_file_relpath(congress, chamber, session, roll_number)


def votes_index_path(congress: int) -> Path:
    return _common.OUTPUT_DIR / f"congress{congress}_votes.json"


def rebuild_votes_index(congress: int) -> dict[str, Any]:
    """Rebuild congress{N}_votes.json from every vote file on disk."""
    refs = []
    directory = votes_dir(congress)
    if directory.exists():
        for path in sorted(directory.glob("*.json")):
            with path.open("r", encoding="utf-8") as f:
                refs.append(build_vote_ref(json.load(f)))
    payload = build_votes_index(congress, refs, now_iso())
    _common._write_json(votes_index_path(congress), payload)
    return payload


# ---------- fetching --------------------------------------------------------


def build_lis_to_bioguide() -> dict[str, str]:
    """Union of the current and historical legislators YAMLs.

    Historical is required, not a nicety: a senator who leaves mid-Congress
    moves to legislators-historical.yaml while their roll calls stay
    published. Current entries win on the (unobserved) chance of a conflict.
    """
    historical = parse_lis_to_bioguide_yaml(_fetch(LEGISLATORS_HISTORICAL_YAML_URL))
    current = parse_lis_to_bioguide_yaml(_fetch(LEGISLATORS_CURRENT_YAML_URL))
    return {**historical, **current}


def fetch_senate_menus(congress: int) -> list[dict[str, Any]]:
    """Fetch and parse the vote menu for each published session.

    A 404 means the session hasn't started (or its menu isn't up yet) and is
    skipped; any other HTTP failure propagates. Raises if no session menu is
    available at all — that means the congress number is wrong or senate.gov
    changed its layout, either way nothing useful can be published.
    """
    menus: list[dict[str, Any]] = []
    for session in SENATE_SESSIONS:
        url = senate_vote_menu_url(congress, session)
        try:
            text = _fetch(url)
        except requests.HTTPError as exc:
            if exc.response is not None and exc.response.status_code == 404:
                continue
            raise
        menu = parse_senate_vote_menu(text)
        if menu["congress"] != congress or menu["session"] != session:
            raise RuntimeError(
                f"vote menu at {url} identifies itself as congress "
                f"{menu['congress']} session {menu['session']}"
            )
        menus.append(menu)
    if not menus:
        raise RuntimeError(f"no Senate vote menu available for Congress {congress}")
    return menus


def fetch_new_senate_votes(
    congress: int,
    menus: list[dict[str, Any]],
    lis_to_bioguide: dict[str, str],
    errors: ErrorCollector,
    max_new: int,
) -> tuple[int, int]:
    """Fetch, parse, and save every menu vote not already on disk.

    Returns (fetched, skipped). A vote that fails to fetch or parse is
    recorded in ``errors`` and left off disk, so the next run retries it.
    """
    fetched = 0
    skipped = 0
    for menu in menus:
        session = menu["session"]
        for roll_number in menu["vote_numbers"]:
            path = vote_path(congress, CHAMBER_SENATE, session, roll_number)
            if path.exists():
                skipped += 1
                continue
            if fetched >= max_new:
                print(f"Reached --max-new {max_new}; remaining votes deferred to next run")
                return fetched, skipped
            url = senate_vote_source_url(congress, session, roll_number)
            try:
                vote = parse_senate_vote(_fetch(url), lis_to_bioguide)
                if vote["session"] != session or vote["roll_number"] != roll_number:
                    raise ValueError(
                        f"detail XML identifies itself as session {vote['session']} "
                        f"roll {vote['roll_number']}"
                    )
            except Exception as exc:  # noqa: BLE001 — per-vote isolation
                errors.record("senate_vote", f"{congress}-{session}-{roll_number}", exc, url=url)
                continue
            _common._write_json(path, vote)
            fetched += 1
            print(f"  + {vote['id']}: {vote['question']} — {vote['result']}")
    return fetched, skipped


def fetch_new_house_votes(
    congress: int,
    errors: ErrorCollector,
    max_new: int,
) -> tuple[int, int, int]:
    """Probe clerk.house.gov for new House roll calls and save them.

    The House publishes no vote menu, but roll numbers are dense and
    sequential per session, so each session is walked from roll 1: rolls
    already on disk are skipped for free, missing ones are fetched, and the
    first 404 is the end of the published roll calls. Candidate-ballot votes
    (Speaker elections) cannot be expressed as yea/nay positions; they are
    skipped — and re-probed every run, a couple of cheap fetches per Congress
    — rather than recorded, keeping file existence as the only resume cursor.

    Returns (fetched, skipped, unsupported).
    """
    fetched = 0
    skipped = 0
    unsupported = 0
    for session in HOUSE_SESSIONS:
        consecutive_errors = 0
        roll_number = 0
        while True:
            roll_number += 1
            path = vote_path(congress, CHAMBER_HOUSE, session, roll_number)
            if path.exists():
                skipped += 1
                continue
            if fetched >= max_new:
                print(f"Reached --max-new {max_new}; remaining votes deferred to next run")
                return fetched, skipped, unsupported
            url = house_vote_source_url(congress, session, roll_number)
            vote_key = f"{congress}-{session}-{roll_number}"
            try:
                text = _fetch(url)
            except requests.HTTPError as exc:
                if exc.response is not None and exc.response.status_code == 404:
                    break  # end of the published roll calls for this session
                errors.record("house_vote", vote_key, exc, url=url)
                break  # outage vs. end is indistinguishable; retry next run
            except requests.RequestException as exc:
                errors.record("house_vote", vote_key, exc, url=url)
                break
            try:
                vote = parse_house_vote(text)
            except UnsupportedVoteError as exc:
                unsupported += 1
                consecutive_errors = 0
                print(f"  - house-{vote_key} skipped: {exc}")
                continue
            except Exception as exc:  # noqa: BLE001 — per-vote isolation
                errors.record("house_vote", vote_key, exc, url=url)
                consecutive_errors += 1
                if consecutive_errors >= MAX_CONSECUTIVE_HOUSE_ERRORS:
                    print(
                        f"  ! {consecutive_errors} consecutive House parse failures; "
                        f"stopping session {session} probe"
                    )
                    break
                continue
            if (
                vote["congress"] != congress
                or vote["session"] != session
                or vote["roll_number"] != roll_number
            ):
                errors.record(
                    "house_vote",
                    vote_key,
                    ValueError(f"detail XML identifies itself as {vote['id']}"),
                    url=url,
                )
                break  # the year->session mapping is off; nothing later can be right
            consecutive_errors = 0
            _common._write_json(path, vote)
            fetched += 1
            print(f"  + {vote['id']}: {vote['question']} — {vote['result']}")
    return fetched, skipped, unsupported


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--congress", type=int, default=None,
        help="Congress number (default: the current Congress).",
    )
    parser.add_argument(
        "--max-new", type=int, default=1000,
        help="Cap on new votes fetched per run, shared across both chambers "
             "(default 1000). A full-Congress first backfill is ~1500+ detail "
             "fetches; steady-state runs fetch a handful. The cap bounds a "
             "runaway loop, and a capped run is harmless — the next run picks "
             "up where it left off.",
    )
    args = parser.parse_args(argv)
    congress = args.congress if args.congress is not None else current_congress()

    try:
        menus = fetch_senate_menus(congress)
        lis_to_bioguide = build_lis_to_bioguide()
    except Exception as exc:  # noqa: BLE001 — top-level reporter
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    sessions = [m["session"] for m in menus]
    total_listed = sum(len(m["vote_numbers"]) for m in menus)
    print(
        f"Senate menu for Congress {congress}: {total_listed} roll calls "
        f"across session(s) {sessions}"
    )

    errors = ErrorCollector()
    senate_fetched, senate_skipped = fetch_new_senate_votes(
        congress, menus, lis_to_bioguide, errors, args.max_new
    )

    house_budget = args.max_new - senate_fetched
    if house_budget > 0:
        print(f"House probe for Congress {congress} (clerk.house.gov EVS):")
        house_fetched, house_skipped, unsupported = fetch_new_house_votes(
            congress, errors, house_budget
        )
    else:
        print("House probe deferred to next run: --max-new budget exhausted")
        house_fetched = house_skipped = unsupported = 0

    index = rebuild_votes_index(congress)
    errors.print_summary(label="fetch_votes")
    print(
        f"OK: {senate_fetched + house_fetched} new "
        f"({senate_fetched} Senate, {house_fetched} House), "
        f"{senate_skipped + house_skipped} already on disk, "
        f"{unsupported} unsupported, {len(errors)} failed; "
        f"index now {index['vote_count']} votes"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
