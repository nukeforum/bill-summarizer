"""Fetch Senate roll-call votes and publish per-vote JSON plus a per-Congress index.

Senate only for now: senate.gov LIS XML needs no API key. House votes come from
clerk.house.gov EVS XML (also keyless; parsing already lives in _votes.py) and
land in a follow-up — the output layout below already accommodates both
chambers.

Layout under docs/data/ (matches the RollCallVote/VotesIndex wire models in
pipeline:shared):

    votes/congress119/senate-1-618.json   one file per roll call, with positions
    congress119_votes.json                index: every vote minus positions

Runs are incremental and self-resuming: a roll call already on disk is never
refetched (published roll calls are immutable), so each run costs the vote
menu(s), the two legislators YAMLs, and one detail XML per *new* vote. The
index is rebuilt from the files on disk at the end of every run, so a crashed
run heals on the next one.

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
    CHAMBER_SENATE,
    build_vote_ref,
    build_votes_index,
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


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--congress", type=int, default=None,
        help="Congress number (default: the current Congress).",
    )
    parser.add_argument(
        "--max-new", type=int, default=1000,
        help="Cap on new votes fetched per run (default 1000). A full-Congress "
             "first run is ~700 detail fetches; steady-state runs fetch a "
             "handful. The cap bounds a runaway loop, and a capped run is "
             "harmless — the next run picks up where it left off.",
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
    fetched, skipped = fetch_new_senate_votes(
        congress, menus, lis_to_bioguide, errors, args.max_new
    )

    index = rebuild_votes_index(congress)
    errors.print_summary(label="fetch_votes")
    print(
        f"OK: {fetched} new, {skipped} already on disk, {len(errors)} failed; "
        f"index now {index['vote_count']} votes"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
