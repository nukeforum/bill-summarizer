"""Parser and record-building tests for Senate roll-call vote XML.

Fixtures are real senate.gov LIS documents fetched 2026-07-21:
``senate_vote_119_1_00618.xml`` is the full detail XML for roll call 618
(passage of H.R. 5371, the FY26 continuing resolution) verbatim;
``senate_vote_menu_119_1.xml`` is the session vote menu trimmed to six
representative entries (bill passage, cloture, nominations, an en-bloc
vote). ``lis_to_bioguide.json`` is the ``id.lis -> id.bioguide`` map for
the 100 senators voting on roll call 618, extracted from the union of
legislators-current.yaml and legislators-historical.yaml — current alone is
insufficient because members who left mid-Congress (Graham/S293 here) move
to the historical file while their votes remain published.
"""
from __future__ import annotations

import json
from pathlib import Path

import pytest

from _votes import (
    bill_id_from_document,
    build_vote_ref,
    build_votes_index,
    normalize_vote_position,
    parse_lis_to_bioguide_yaml,
    parse_senate_vote,
    parse_senate_vote_menu,
    senate_vote_menu_url,
    senate_vote_source_url,
    vote_file_relpath,
    vote_id,
)

FIXTURES = Path(__file__).parent / "fixtures"


def _lis_map() -> dict[str, str]:
    return json.loads((FIXTURES / "lis_to_bioguide.json").read_text(encoding="utf-8"))


def _vote_618() -> dict:
    text = (FIXTURES / "senate_vote_119_1_00618.xml").read_text(encoding="utf-8")
    return parse_senate_vote(text, _lis_map())


# ------------------------------------------------------------ normalization

def test_position_labels_normalize_to_wire_values():
    assert normalize_vote_position("Yea") == "yea"
    assert normalize_vote_position("Aye") == "yea"
    assert normalize_vote_position("Guilty") == "yea"
    assert normalize_vote_position("Nay") == "nay"
    assert normalize_vote_position("No") == "nay"
    assert normalize_vote_position("Not Guilty") == "nay"
    assert normalize_vote_position("Present") == "present"
    assert normalize_vote_position("Present, Giving Live Pair") == "present"
    assert normalize_vote_position("Not Voting") == "not_voting"
    assert normalize_vote_position("Absent") == "not_voting"


def test_unknown_position_label_raises():
    with pytest.raises(ValueError, match="unknown vote position"):
        normalize_vote_position("Abstained")


# ------------------------------------------------------- ids, paths, urls

def test_vote_id_and_relpath_follow_kotlin_kdoc_conventions():
    assert vote_id("house", 119, 1, 17) == "house-119-1-17"
    assert vote_file_relpath(119, "house", 1, 17) == "votes/congress119/house-1-17.json"


def test_senate_urls_zero_pad_roll_number_only():
    assert senate_vote_source_url(119, 1, 618) == (
        "https://www.senate.gov/legislative/LIS/roll_call_votes/"
        "vote1191/vote_119_1_00618.xml"
    )
    assert senate_vote_menu_url(119, 1) == (
        "https://www.senate.gov/legislative/LIS/roll_call_lists/vote_menu_119_1.xml"
    )


def test_bill_id_from_document_covers_bill_types_and_non_bills():
    assert bill_id_from_document("H.R.", "5371", 119) == "hr5371-119"
    assert bill_id_from_document("S.J.Res.", "76", 119) == "sjres76-119"
    assert bill_id_from_document("S.", "42", 119) == "s42-119"
    # Nominations, treaties, and absent documents are not bills.
    assert bill_id_from_document("PN", "373", 119) is None
    assert bill_id_from_document(None, None, 119) is None
    assert bill_id_from_document("H.R.", "", 119) is None


# ------------------------------------------------------------- vote menu

def test_menu_parses_congress_session_and_sorted_vote_numbers():
    text = (FIXTURES / "senate_vote_menu_119_1.xml").read_text(encoding="utf-8")
    menu = parse_senate_vote_menu(text)
    assert menu["congress"] == 119
    assert menu["session"] == 1
    assert menu["vote_numbers"] == sorted(menu["vote_numbers"])
    # 618 is the H.R. 5371 passage vote; 655 is an en-bloc nomination vote
    # whose <vote_number> sits alongside nested <en_bloc> matter blocks.
    assert 618 in menu["vote_numbers"]
    assert 655 in menu["vote_numbers"]
    assert len(menu["vote_numbers"]) == 6


# ------------------------------------------------------------ vote detail

def test_senate_vote_618_parses_to_wire_shape():
    vote = _vote_618()
    assert vote["id"] == "senate-119-1-618"
    assert vote["congress"] == 119
    assert vote["chamber"] == "senate"
    assert vote["session"] == 1
    assert vote["roll_number"] == 618
    assert vote["date"] == "2025-11-10"
    assert vote["question"] == "On Passage of the Bill"
    assert vote["result"] == "Bill Passed"
    assert vote["bill_id"] == "hr5371-119"
    assert vote["source_url"] == senate_vote_source_url(119, 1, 618)


def test_senate_vote_618_totals_match_positions_and_official_tally():
    vote = _vote_618()
    # Official tally: Bill Passed (60-40), all 100 senators voting.
    assert vote["totals"] == {"yea": 60, "nay": 40, "present": 0, "not_voting": 0}
    assert len(vote["positions"]) == 100
    counted = {"yea": 0, "nay": 0, "present": 0, "not_voting": 0}
    for p in vote["positions"]:
        counted[p["position"]] += 1
    assert counted == vote["totals"]


def test_senate_vote_618_positions_are_bioguide_keyed_and_sorted():
    vote = _vote_618()
    ids = [p["bioguide_id"] for p in vote["positions"]]
    assert ids == sorted(ids)
    assert len(set(ids)) == 100
    # Alsobrooks (D-MD), lis S428 -> bioguide A000382, voted Nay.
    alsobrooks = next(p for p in vote["positions"] if p["bioguide_id"] == "A000382")
    assert alsobrooks == {
        "bioguide_id": "A000382",
        "position": "nay",
        "party": "D",
        "state": "MD",
    }


def test_unmapped_lis_member_id_raises():
    text = (FIXTURES / "senate_vote_119_1_00618.xml").read_text(encoding="utf-8")
    mapping = _lis_map()
    mapping.pop("S428")
    with pytest.raises(ValueError, match="S428"):
        parse_senate_vote(text, mapping)


# ---------------------------------------------------------------- yaml map

def test_parse_lis_to_bioguide_yaml():
    text = (
        "- id:\n"
        "    bioguide: A000382\n"
        "    lis: S428\n"
        "- id:\n"
        "    bioguide: B001234\n"  # a representative: no lis id
        "- not-a-dict\n"
    )
    assert parse_lis_to_bioguide_yaml(text) == {"S428": "A000382"}


# -------------------------------------------------------------------- index

def test_vote_ref_drops_positions_and_carries_path():
    ref = build_vote_ref(_vote_618())
    assert "positions" not in ref
    assert "congress" not in ref
    assert ref["path"] == "votes/congress119/senate-1-618.json"
    assert ref["bill_id"] == "hr5371-119"
    assert ref["totals"]["yea"] == 60


def test_votes_index_is_newest_first_with_count():
    older = dict(build_vote_ref(_vote_618()), date="2025-01-03", roll_number=2)
    newer = build_vote_ref(_vote_618())
    index = build_votes_index(119, [older, newer], generated_at="2026-07-21T00:00:00Z")
    assert index["generated_at"] == "2026-07-21T00:00:00Z"
    assert index["congress"] == 119
    assert index["vote_count"] == 2
    assert [v["date"] for v in index["votes"]] == ["2025-11-10", "2025-01-03"]
