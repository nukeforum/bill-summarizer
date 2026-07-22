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

House fixtures are real clerk.house.gov EVS documents fetched 2026-07-22:
``house_vote_119_1_roll017.xml`` is the full XML for 2025 roll 17 (passage
of H.R. 30) verbatim; ``house_vote_119_1_roll001_quorum.xml`` is the
opening-day quorum call trimmed to four representative positions;
``house_vote_119_1_roll002_speaker.xml`` is the Speaker election trimmed to
four candidate-name votes (metadata, including the ``totals-by-candidate``
blocks, kept verbatim).
"""
from __future__ import annotations

import json
from pathlib import Path

import pytest

from _votes import (
    UnsupportedVoteError,
    attach_vote_refs,
    bill_id_from_document,
    bill_id_from_legis_num,
    build_party_split,
    build_vote_ref,
    build_votes_index,
    strip_vote_refs,
    house_session_year,
    house_vote_source_url,
    normalize_vote_position,
    parse_house_vote,
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


# ------------------------------------------------------------- house votes

def _house_vote_17() -> dict:
    text = (FIXTURES / "house_vote_119_1_roll017.xml").read_text(encoding="utf-8")
    return parse_house_vote(text)


def test_house_urls_are_year_keyed_with_three_digit_rolls():
    assert house_session_year(119, 1) == 2025
    assert house_session_year(119, 2) == 2026
    assert house_vote_source_url(119, 1, 17) == "https://clerk.house.gov/evs/2025/roll017.xml"
    # Rolls past 999 (seen in busy years) are not truncated by the padding.
    assert house_vote_source_url(119, 2, 1002) == "https://clerk.house.gov/evs/2026/roll1002.xml"


def test_bill_id_from_legis_num_covers_bill_types_and_non_bills():
    assert bill_id_from_legis_num("H R 30", 119) == "hr30-119"
    assert bill_id_from_legis_num("H RES 5", 119) == "hres5-119"
    assert bill_id_from_legis_num("H J RES 20", 119) == "hjres20-119"
    assert bill_id_from_legis_num("H CON RES 14", 119) == "hconres14-119"
    # The House also votes on Senate bills.
    assert bill_id_from_legis_num("S 5", 119) == "s5-119"
    # Non-bill business.
    assert bill_id_from_legis_num("QUORUM", 119) is None
    assert bill_id_from_legis_num("MOTION", 119) is None
    assert bill_id_from_legis_num("", 119) is None
    assert bill_id_from_legis_num(None, 119) is None


def test_house_vote_17_parses_to_wire_shape():
    vote = _house_vote_17()
    assert vote["id"] == "house-119-1-17"
    assert vote["congress"] == 119
    assert vote["chamber"] == "house"
    assert vote["session"] == 1
    assert vote["roll_number"] == 17
    assert vote["date"] == "2025-01-16"
    assert vote["question"] == "On Passage"
    assert vote["result"] == "Passed"
    assert vote["bill_id"] == "hr30-119"
    assert vote["source_url"] == house_vote_source_url(119, 1, 17)


def test_house_vote_17_totals_match_positions_and_official_tally():
    vote = _house_vote_17()
    # Official tally: Passed 274-145, 15 not voting (one seat vacant).
    assert vote["totals"] == {"yea": 274, "nay": 145, "present": 0, "not_voting": 15}
    assert len(vote["positions"]) == 434
    counted = {"yea": 0, "nay": 0, "present": 0, "not_voting": 0}
    for p in vote["positions"]:
        counted[p["position"]] += 1
    assert counted == vote["totals"]


def test_house_vote_17_positions_are_bioguide_keyed_and_sorted():
    vote = _house_vote_17()
    ids = [p["bioguide_id"] for p in vote["positions"]]
    assert ids == sorted(ids)
    assert len(set(ids)) == 434
    # Adams (D-NC), name-id A000370, voted Nay.
    adams = next(p for p in vote["positions"] if p["bioguide_id"] == "A000370")
    assert adams == {
        "bioguide_id": "A000370",
        "position": "nay",
        "party": "D",
        "state": "NC",
    }


def test_house_quorum_call_is_a_non_bill_vote_with_present_positions():
    text = (FIXTURES / "house_vote_119_1_roll001_quorum.xml").read_text(encoding="utf-8")
    vote = parse_house_vote(text)
    assert vote["id"] == "house-119-1-1"
    assert vote["bill_id"] is None
    assert vote["question"] == "Call by States"
    # Fixture is trimmed to 3 Present + 1 Not Voting; totals derive from
    # the included positions, not the vote-totals block.
    assert vote["totals"] == {"yea": 0, "nay": 0, "present": 3, "not_voting": 1}


def test_house_speaker_election_raises_unsupported():
    text = (FIXTURES / "house_vote_119_1_roll002_speaker.xml").read_text(encoding="utf-8")
    with pytest.raises(UnsupportedVoteError, match="candidate-ballot"):
        parse_house_vote(text)


def test_house_vote_ref_path_and_index_interop():
    ref = build_vote_ref(_house_vote_17())
    assert ref["path"] == "votes/congress119/house-1-17.json"
    assert ref["bill_id"] == "hr30-119"


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


# -------------------------------------------------------------- party split

def test_build_party_split_orders_positions_and_sorts_parties():
    positions = [
        {"bioguide_id": "A1", "position": "nay", "party": "R"},
        {"bioguide_id": "A2", "position": "yea", "party": "R"},
        {"bioguide_id": "A3", "position": "yea", "party": "D"},
        {"bioguide_id": "A4", "position": "yea", "party": "R"},
        {"bioguide_id": "A5", "position": "not_voting", "party": "I"},
        {"bioguide_id": "A6", "position": "yea", "party": None},  # no party: left out
    ]
    split = build_party_split(positions)
    # Position keys in wire order (yea before nay), party keys sorted,
    # "present" absent because no member holds it.
    assert list(split.keys()) == ["yea", "nay", "not_voting"]
    assert split["yea"] == {"D": 1, "R": 2}
    assert list(split["yea"].keys()) == ["D", "R"]
    assert split["nay"] == {"R": 1}
    assert split["not_voting"] == {"I": 1}


def test_senate_vote_ref_party_split_matches_official_breakdown():
    ref = build_vote_ref(_vote_618())
    assert ref["party_split"] == {
        "yea": {"D": 7, "I": 1, "R": 52},
        "nay": {"D": 38, "I": 1, "R": 1},
    }


def test_house_vote_ref_party_split_sums_to_totals():
    ref = build_vote_ref(_house_vote_17())
    assert ref["party_split"] == {
        "yea": {"D": 61, "R": 213},
        "nay": {"D": 145},
        "not_voting": {"D": 9, "R": 6},
    }
    for position, by_party in ref["party_split"].items():
        assert sum(by_party.values()) == ref["totals"][position]


# -------------------------------------------------------------------- index

def test_vote_ref_drops_positions_and_carries_path():
    ref = build_vote_ref(_vote_618())
    assert "positions" not in ref
    assert "congress" not in ref
    assert ref["path"] == "votes/congress119/senate-1-618.json"
    assert ref["bill_id"] == "hr5371-119"
    assert ref["totals"]["yea"] == 60
    # party_split sits between totals and path, matching the Kotlin
    # VoteRef declaration order for byte parity.
    assert list(ref.keys())[-3:] == ["totals", "party_split", "path"]


def test_votes_index_is_newest_first_with_count():
    older = dict(build_vote_ref(_vote_618()), date="2025-01-03", roll_number=2)
    newer = build_vote_ref(_vote_618())
    index = build_votes_index(119, [older, newer], generated_at="2026-07-21T00:00:00Z")
    assert index["generated_at"] == "2026-07-21T00:00:00Z"
    assert index["congress"] == 119
    assert index["vote_count"] == 2
    assert [v["date"] for v in index["votes"]] == ["2025-11-10", "2025-01-03"]


# ------------------------------------------------------------ bill linkage


def _bill(bill_id: str, **extra) -> dict:
    return {"id": bill_id, "title": "A bill", **extra}


def test_attach_vote_refs_groups_by_bill_newest_first():
    senate = build_vote_ref(_vote_618())  # hr5371-119, 2025-11-10
    house = dict(
        build_vote_ref(_vote_618()),
        id="house-119-1-2", chamber="house", roll_number=2, date="2025-01-03",
    )
    quorum = dict(
        build_vote_ref(_vote_618()),
        id="house-119-1-1", chamber="house", roll_number=1, bill_id=None,
    )
    bills = [_bill("hr5371-119"), _bill("s42-119")]
    out = attach_vote_refs(bills, [house, quorum, senate])
    assert out is bills
    assert [v["id"] for v in bills[0]["votes"]] == ["senate-119-1-618", "house-119-1-2"]
    # Bills with no roll calls still get the key — the Kotlin publisher's
    # encodeDefaults always emits it, so byte parity requires it here too.
    assert bills[1]["votes"] == []


def test_attach_vote_refs_recomputes_and_keeps_votes_last():
    stale = dict(build_vote_ref(_vote_618()), id="stale-ref")
    bill = _bill("hr5371-119")
    bill["votes"] = [stale]
    bill["congress_gov_url"] = "https://example.test"
    attach_vote_refs([bill], [build_vote_ref(_vote_618())])
    assert [v["id"] for v in bill["votes"]] == ["senate-119-1-618"]
    assert list(bill.keys())[-1] == "votes"


def test_strip_vote_refs_removes_only_the_derived_key():
    with_votes = _bill("hr5371-119", votes=[build_vote_ref(_vote_618())])
    without = _bill("s42-119")
    out = strip_vote_refs([with_votes, without])
    assert out is not None
    assert "votes" not in with_votes
    assert with_votes["title"] == "A bill"
    assert "votes" not in without
