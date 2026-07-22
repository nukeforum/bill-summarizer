"""Pure parsing and record building for roll-call votes.

Network I/O lives in fetch_votes.py; this module is fully unit-testable on
raw text. Output dicts match the authoritative Kotlin wire models in
``pipeline:shared`` (``RollCallVote`` / ``VotesIndex`` in
``com.informedcitizen.pipeline.model``) — snake_case keys, date-only ISO
dates, ``bill_id`` null for roll calls not tied to a bill.

Senate source: senate.gov LIS XML (no API key required). The vote menu
(``vote_menu_<congress>_<session>.xml``) lists every roll call of a session;
per-vote detail XML carries per-member positions keyed by ``lis_member_id``,
which we map to bioguide ids via unitedstates/congress-legislators
(``id.lis`` → ``id.bioguide``), since bioguide is the member key used across
the rest of the published JSON. The map must union legislators-current.yaml
with legislators-historical.yaml: a member who left the Senate mid-Congress
moves to the historical file while their roll calls remain published (seen
live 2026-07-21 — Graham/S293 appears in 2025 votes but only in historical).

House source: clerk.house.gov EVS XML (no API key required), one document
per roll call at ``evs/<year>/roll<NNN>.xml``. Positions are keyed by the
``name-id`` attribute, which is already the bioguide id — no mapping step.
There is no machine-readable vote menu; roll numbers are dense and
sequential within a calendar year, so the fetch driver discovers new votes
by probing past the highest roll on disk. Speaker elections are published
in the same format but record candidate *names* as votes — those raise
``UnsupportedVoteError`` since they cannot be expressed as yea/nay
positions.
"""
from __future__ import annotations

import re
import xml.etree.ElementTree as ET
from typing import Any

import yaml

CHAMBER_HOUSE = "house"
CHAMBER_SENATE = "senate"

POSITION_YEA = "yea"
POSITION_NAY = "nay"
POSITION_PRESENT = "present"
POSITION_NOT_VOTING = "not_voting"

# Upstream label -> normalized wire value. House uses Aye/No on motions and
# Yea/Nay on passage; Senate uses Guilty/Not Guilty on impeachment articles.
_POSITION_LABELS: dict[str, str] = {
    "yea": POSITION_YEA,
    "aye": POSITION_YEA,
    "guilty": POSITION_YEA,
    "nay": POSITION_NAY,
    "no": POSITION_NAY,
    "not guilty": POSITION_NAY,
    "present": POSITION_PRESENT,
    "present, giving live pair": POSITION_PRESENT,
    "not voting": POSITION_NOT_VOTING,
    "absent": POSITION_NOT_VOTING,
}

# Matches the keys of _common._BILL_TYPE_TO_SLUG — the eight document types
# that map to a Bill.id. Anything else (PN nominations, treaty documents,
# amendments without a document) yields bill_id = None.
_BILL_TYPES = frozenset(
    {"hr", "s", "hjres", "sjres", "hconres", "sconres", "hres", "sres"}
)

_MONTHS = {
    "january": 1, "february": 2, "march": 3, "april": 4, "may": 5, "june": 6,
    "july": 7, "august": 8, "september": 9, "october": 10, "november": 11,
    "december": 12,
}

# "November 10, 2025,  08:58 PM" (Senate detail XML vote_date)
_SENATE_DATE_RE = re.compile(r"([A-Za-z]+)\s+(\d{1,2}),\s*(\d{4})")

# "16-Jan-2025" (House clerk XML action-date)
_HOUSE_DATE_RE = re.compile(r"(\d{1,2})-([A-Za-z]{3})-(\d{4})")
_MONTH_ABBREVS = {name[:3]: number for name, number in _MONTHS.items()}


class UnsupportedVoteError(Exception):
    """A well-formed roll call that cannot be expressed as yea/nay positions.

    Deliberately not a ``ValueError``: parse failures are retried on the next
    run (upstream may fix the document), whereas unsupported votes are
    permanent — the fetch driver should skip them without recording an error.
    """


def normalize_vote_position(raw: str) -> str:
    """Map an upstream vote label to one of the four wire values.

    Raises ``ValueError`` on an unrecognized label so callers surface new
    upstream vocabulary instead of silently miscounting.
    """
    position = _POSITION_LABELS.get(raw.strip().lower())
    if position is None:
        raise ValueError(f"unknown vote position: {raw!r}")
    return position


def vote_id(chamber: str, congress: int, session: int, roll_number: int) -> str:
    """``<chamber>-<congress>-<session>-<roll>``, e.g. ``senate-119-1-618``."""
    return f"{chamber}-{congress}-{session}-{roll_number}"


def vote_file_relpath(congress: int, chamber: str, session: int, roll_number: int) -> str:
    """Vote-file location relative to docs/data/, e.g. ``votes/congress119/house-1-17.json``."""
    return f"votes/congress{congress}/{chamber}-{session}-{roll_number}.json"


def senate_vote_source_url(congress: int, session: int, roll_number: int) -> str:
    return (
        "https://www.senate.gov/legislative/LIS/roll_call_votes/"
        f"vote{congress}{session}/vote_{congress}_{session}_{roll_number:05d}.xml"
    )


def senate_vote_menu_url(congress: int, session: int) -> str:
    return (
        "https://www.senate.gov/legislative/LIS/roll_call_lists/"
        f"vote_menu_{congress}_{session}.xml"
    )


def _parse_senate_date(raw: str) -> str:
    """``"November 10, 2025,  08:58 PM"`` -> ``"2025-11-10"``.

    Month names are mapped explicitly rather than via strptime %B so the
    result does not depend on the process locale.
    """
    m = _SENATE_DATE_RE.search(raw)
    if not m:
        raise ValueError(f"unparseable Senate vote date: {raw!r}")
    month = _MONTHS.get(m.group(1).lower())
    if month is None:
        raise ValueError(f"unparseable Senate vote date: {raw!r}")
    return f"{int(m.group(3)):04d}-{month:02d}-{int(m.group(2)):02d}"


def bill_id_from_document(doc_type: str | None, doc_number: str | None, congress: int) -> str | None:
    """Derive a ``Bill.id`` (``hr5371-119``) from a Senate XML document block.

    Returns None for non-bill documents (nominations, treaties) — the
    corresponding roll call is published with ``bill_id: null``.
    """
    if not doc_type or not doc_number:
        return None
    bill_type = doc_type.replace(".", "").replace(" ", "").lower()
    number = doc_number.strip()
    if bill_type not in _BILL_TYPES or not number.isdigit():
        return None
    return f"{bill_type}{number}-{congress}"


def parse_senate_vote_menu(text: str) -> dict[str, Any]:
    """Parse a Senate vote-menu XML into ``{congress, session, vote_numbers}``.

    ``vote_numbers`` is sorted ascending. The menu carries tallies and
    questions too, but the fetch driver only needs to know which roll calls
    exist; everything else comes from the per-vote detail XML.
    """
    root = ET.fromstring(text)
    numbers = sorted(
        int(el.text.strip())
        for el in root.iter("vote_number")
        if el.text and el.text.strip().isdigit()
    )
    return {
        "congress": int(root.findtext("congress").strip()),
        "session": int(root.findtext("session").strip()),
        "vote_numbers": numbers,
    }


def parse_senate_vote(text: str, lis_to_bioguide: dict[str, str]) -> dict[str, Any]:
    """Parse a Senate roll-call detail XML into a RollCallVote-shaped dict.

    Totals are derived from the parsed positions rather than the XML
    ``<count>`` block so the published tallies always agree with the
    published positions. Raises ``ValueError`` on an unknown vote label or
    an ``lis_member_id`` missing from ``lis_to_bioguide`` (a senator not yet
    in congress-legislators, or a departed one when the caller forgot the
    historical file) — the fetch driver skips that vote and records the
    error rather than publishing incomplete positions.
    """
    root = ET.fromstring(text)
    congress = int(root.findtext("congress").strip())
    session = int(root.findtext("session").strip())
    roll_number = int(root.findtext("vote_number").strip())

    positions: list[dict[str, Any]] = []
    totals = {
        POSITION_YEA: 0,
        POSITION_NAY: 0,
        POSITION_PRESENT: 0,
        POSITION_NOT_VOTING: 0,
    }
    for member in root.iter("member"):
        lis_id = (member.findtext("lis_member_id") or "").strip()
        bioguide = lis_to_bioguide.get(lis_id)
        if not bioguide:
            raise ValueError(f"no bioguide mapping for lis_member_id {lis_id!r}")
        position = normalize_vote_position(member.findtext("vote_cast") or "")
        totals[position] += 1
        positions.append(
            {
                "bioguide_id": bioguide,
                "position": position,
                "party": (member.findtext("party") or "").strip() or None,
                "state": (member.findtext("state") or "").strip() or None,
            }
        )
    positions.sort(key=lambda p: p["bioguide_id"])

    document = root.find("document")
    bill_id = (
        bill_id_from_document(
            document.findtext("document_type"),
            document.findtext("document_number"),
            congress,
        )
        if document is not None
        else None
    )

    return {
        "id": vote_id(CHAMBER_SENATE, congress, session, roll_number),
        "congress": congress,
        "chamber": CHAMBER_SENATE,
        "session": session,
        "roll_number": roll_number,
        "date": _parse_senate_date(root.findtext("vote_date") or ""),
        "question": (root.findtext("question") or "").strip(),
        "result": (root.findtext("vote_result") or "").strip(),
        "bill_id": bill_id,
        "totals": {
            "yea": totals[POSITION_YEA],
            "nay": totals[POSITION_NAY],
            "present": totals[POSITION_PRESENT],
            "not_voting": totals[POSITION_NOT_VOTING],
        },
        "positions": positions,
        "source_url": senate_vote_source_url(congress, session, roll_number),
    }


def house_session_year(congress: int, session: int) -> int:
    """Calendar year of a House session (session 1 = the Congress's first year).

    Clerk EVS URLs are year-keyed while our ids are congress/session-keyed.
    """
    return 1789 + (congress - 1) * 2 + (session - 1)


def house_vote_source_url(congress: int, session: int, roll_number: int) -> str:
    year = house_session_year(congress, session)
    return f"https://clerk.house.gov/evs/{year}/roll{roll_number:03d}.xml"


def bill_id_from_legis_num(legis_num: str | None, congress: int) -> str | None:
    """Derive a ``Bill.id`` from a House clerk ``legis-num`` (``"H R 30"``).

    Returns None for non-bill business (``QUORUM``, ``MOTION``, blank —
    Speaker elections omit the element entirely).
    """
    tokens = (legis_num or "").split()
    if len(tokens) < 2:
        return None
    return bill_id_from_document(" ".join(tokens[:-1]), tokens[-1], congress)


def _parse_house_date(raw: str) -> str:
    """``"16-Jan-2025"`` -> ``"2025-01-16"``, locale-independent."""
    m = _HOUSE_DATE_RE.search(raw)
    if not m:
        raise ValueError(f"unparseable House vote date: {raw!r}")
    month = _MONTH_ABBREVS.get(m.group(2).lower())
    if month is None:
        raise ValueError(f"unparseable House vote date: {raw!r}")
    return f"{int(m.group(3)):04d}-{month:02d}-{int(m.group(1)):02d}"


def _parse_house_session(raw: str) -> int:
    """``"1st"`` / ``"2nd"`` -> 1 / 2."""
    m = re.match(r"\s*(\d+)", raw or "")
    if not m:
        raise ValueError(f"unparseable House session: {raw!r}")
    return int(m.group(1))


def parse_house_vote(text: str) -> dict[str, Any]:
    """Parse a House clerk EVS XML into a RollCallVote-shaped dict.

    Same policies as :func:`parse_senate_vote`: totals are derived from the
    parsed positions (not the ``vote-totals`` block) so published tallies
    always agree with published positions, and an unknown vote label raises
    ``ValueError`` so the driver records the vote instead of miscounting.
    Candidate-ballot votes (Speaker elections, identified by their
    ``totals-by-candidate`` blocks) raise ``UnsupportedVoteError``.
    """
    root = ET.fromstring(text)
    meta = root.find("vote-metadata")
    if meta is None:
        raise ValueError("missing vote-metadata block")
    congress = int(meta.findtext("congress").strip())
    session = _parse_house_session(meta.findtext("session") or "")
    roll_number = int(meta.findtext("rollcall-num").strip())

    if meta.find(".//totals-by-candidate") is not None:
        raise UnsupportedVoteError(
            f"candidate-ballot vote (roll {roll_number}): positions are "
            "candidate names, not yea/nay"
        )

    positions: list[dict[str, Any]] = []
    totals = {
        POSITION_YEA: 0,
        POSITION_NAY: 0,
        POSITION_PRESENT: 0,
        POSITION_NOT_VOTING: 0,
    }
    for recorded in root.iter("recorded-vote"):
        legislator = recorded.find("legislator")
        bioguide = (legislator.get("name-id") or "").strip() if legislator is not None else ""
        if not bioguide:
            raise ValueError(f"recorded-vote without a name-id (roll {roll_number})")
        position = normalize_vote_position(recorded.findtext("vote") or "")
        totals[position] += 1
        positions.append(
            {
                "bioguide_id": bioguide,
                "position": position,
                "party": (legislator.get("party") or "").strip() or None,
                "state": (legislator.get("state") or "").strip() or None,
            }
        )
    positions.sort(key=lambda p: p["bioguide_id"])

    return {
        "id": vote_id(CHAMBER_HOUSE, congress, session, roll_number),
        "congress": congress,
        "chamber": CHAMBER_HOUSE,
        "session": session,
        "roll_number": roll_number,
        "date": _parse_house_date(meta.findtext("action-date") or ""),
        "question": (meta.findtext("vote-question") or "").strip(),
        "result": (meta.findtext("vote-result") or "").strip(),
        "bill_id": bill_id_from_legis_num(meta.findtext("legis-num"), congress),
        "totals": {
            "yea": totals[POSITION_YEA],
            "nay": totals[POSITION_NAY],
            "present": totals[POSITION_PRESENT],
            "not_voting": totals[POSITION_NOT_VOTING],
        },
        "positions": positions,
        "source_url": house_vote_source_url(congress, session, roll_number),
    }


def parse_lis_to_bioguide_yaml(text: str) -> dict[str, str]:
    """Extract ``{lis_id: bioguide_id}`` from legislators-current.yaml.

    Only senators carry an ``id.lis`` key, so the result covers exactly the
    members that appear in Senate roll-call XML.
    """
    data = yaml.safe_load(text) or []
    out: dict[str, str] = {}
    for entry in data:
        if not isinstance(entry, dict):
            continue
        ids = entry.get("id") or {}
        lis, bioguide = ids.get("lis"), ids.get("bioguide")
        if lis and bioguide:
            out[lis] = bioguide
    return out


def build_vote_ref(vote: dict[str, Any]) -> dict[str, Any]:
    """A VotesIndex row: the vote minus positions, plus its file path."""
    return {
        "id": vote["id"],
        "chamber": vote["chamber"],
        "session": vote["session"],
        "roll_number": vote["roll_number"],
        "date": vote["date"],
        "question": vote["question"],
        "result": vote["result"],
        "bill_id": vote["bill_id"],
        "totals": vote["totals"],
        "path": vote_file_relpath(
            vote["congress"], vote["chamber"], vote["session"], vote["roll_number"]
        ),
    }


def build_votes_index(
    congress: int, refs: list[dict[str, Any]], generated_at: str
) -> dict[str, Any]:
    """Assemble the per-Congress ``congress<N>_votes.json`` payload.

    Newest first (matching the bills manifests); ties broken by roll number
    then chamber for a deterministic file.
    """
    ordered = sorted(
        refs,
        key=lambda r: (r["date"], r["roll_number"], r["chamber"]),
        reverse=True,
    )
    return {
        "generated_at": generated_at,
        "congress": congress,
        "vote_count": len(ordered),
        "votes": ordered,
    }
