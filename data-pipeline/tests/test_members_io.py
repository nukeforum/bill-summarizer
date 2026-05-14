"""IO and shape tests for member-centric pipeline outputs."""
import json

import _common
from _common import (
    load_members_index,
    save_members_index,
    save_member_legislation,
    parse_member_summary,
    parse_member_legislation_item,
)


def test_save_and_load_members_index_roundtrip(tmp_path, monkeypatch):
    monkeypatch.setattr(_common, "OUTPUT_DIR", tmp_path)
    payload = {
        "congress": 119,
        "generated_at": "2026-05-06T03:00:00Z",
        "members": [
            {
                "bioguide_id": "S001234",
                "name": "Sen. Smith, Adrian",
                "party": "D",
                "state": "MI",
                "district": None,
                "chamber": "senate",
                "photo_url": "https://example.com/s.jpg",
                "official_url": "https://smith.senate.gov",
                "sponsored_count": 12,
                "cosponsored_count": 187,
                "address": "123 Senate Office Bldg",
                "phone": "+1-202-0000",
            }
        ],
    }
    save_members_index(119, payload)
    loaded = load_members_index(119)
    assert loaded == payload


def test_save_member_legislation_writes_to_subdir(tmp_path, monkeypatch):
    monkeypatch.setattr(_common, "OUTPUT_DIR", tmp_path)
    payload = {
        "bioguide_id": "S001234",
        "congress": 119,
        "kind": "sponsored",
        "generated_at": "2026-05-06T03:00:00Z",
        "bills": [
            {
                "id": "hr1234-119",
                "type": "hr",
                "number": "1234",
                "congress": 119,
                "title": "An act",
                "introduced_date": "2026-01-15",
                "latest_action": {"date": "2026-04-22", "text": "Referred."},
                "policy_area": "Health",
            }
        ],
    }
    save_member_legislation("S001234", "sponsored", payload)
    expected = tmp_path / "members" / "S001234_sponsored.json"
    assert expected.exists()
    assert json.loads(expected.read_text()) == payload


def test_load_members_index_missing_returns_none(tmp_path, monkeypatch):
    monkeypatch.setattr(_common, "OUTPUT_DIR", tmp_path)
    assert load_members_index(119) is None


def test_parse_member_summary_house_with_district():
    raw = {
        "bioguideId": "S001234",
        "name": "Smith, Adrian",
        "directOrderName": "Adrian Smith",
        "partyName": "Republican",
        "state": "Nebraska",
        "district": 3,
        "depiction": {"imageUrl": "https://example.com/s.jpg"},
        "officialUrl": "https://smith.house.gov",
        "addressInformation": {
            "officeAddress": "123 Cannon HOB",
            "phoneNumber": "+1-202-0000",
        },
        "sponsoredLegislation": {"count": 12},
        "cosponsoredLegislation": {"count": 187},
        "terms": [{"chamber": "House of Representatives"}],
    }
    out = parse_member_summary(raw)
    assert out == {
        "bioguide_id": "S001234",
        "name": "Adrian Smith",
        "party": "R",
        "state": "NE",
        "district": 3,
        "chamber": "house",
        "photo_url": "https://example.com/s.jpg",
        "official_url": "https://smith.house.gov",
        "sponsored_count": 12,
        "cosponsored_count": 187,
        "address": "123 Cannon HOB",
        "phone": "+1-202-0000",
    }


def test_parse_member_summary_senator_no_district():
    raw = {
        "bioguideId": "P001234",
        "directOrderName": "Gary Peters",
        "partyName": "Democratic",
        "state": "Michigan",
        "officialUrl": "https://peters.senate.gov",
        "addressInformation": {"officeAddress": "1 Hart SOB", "phoneNumber": "+1-202-1111"},
        "sponsoredLegislation": {"count": 5},
        "cosponsoredLegislation": {"count": 99},
        "terms": [{"chamber": "Senate"}],
        "depiction": {},
    }
    out = parse_member_summary(raw)
    assert out["chamber"] == "senate"
    assert out["state"] == "MI"
    assert out["district"] is None


def test_parse_member_summary_at_large_house_rep():
    """At-large House reps come back from Congress.gov with district=null;
    normalize to 0 for picker matching."""
    raw = {
        "bioguideId": "B001234",
        "directOrderName": "Becca Balint",
        "partyName": "Democratic",
        "state": "Vermont",
        "district": None,
        "terms": [{"chamber": "House of Representatives"}],
        "depiction": {"imageUrl": "x"},
        "officialUrl": "https://balint.house.gov",
        "addressInformation": {"officeAddress": "x", "phoneNumber": "x"},
        "sponsoredLegislation": {"count": 0},
        "cosponsoredLegislation": {"count": 0},
    }
    out = parse_member_summary(raw)
    assert out["chamber"] == "house"
    assert out["district"] == 0
    assert out["state"] == "VT"


def test_parse_member_legislation_item():
    raw = {
        "introducedDate": "2026-01-15",
        "type": "HR",
        "number": 1234,
        "congress": 119,
        "latestTitle": "An Act",
        "latestAction": {"actionDate": "2026-04-22", "text": "Referred to committee."},
        "policyArea": {"name": "Health"},
    }
    out = parse_member_legislation_item(raw)
    assert out == {
        "id": "hr1234-119",
        "type": "hr",
        "number": "1234",
        "congress": 119,
        "title": "An Act",
        "introduced_date": "2026-01-15",
        "latest_action": {"date": "2026-04-22", "text": "Referred to committee."},
        "policy_area": "Health",
    }


def test_parse_member_legislation_item_missing_policy_area():
    raw = {
        "introducedDate": "2026-01-15",
        "type": "S",
        "number": 99,
        "congress": 119,
        "latestTitle": "A bill",
        "latestAction": {"actionDate": "2026-02-01", "text": "Read."},
    }
    out = parse_member_legislation_item(raw)
    assert out["policy_area"] is None
    assert out["id"] == "s99-119"


def test_parse_member_legislation_item_string_policy_area():
    """Defensive: API has been seen returning policyArea as a bare string in the past."""
    raw = {
        "introducedDate": "2026-02-01",
        "type": "S",
        "number": 12,
        "congress": 119,
        "latestTitle": "A bill",
        "latestAction": {"actionDate": "2026-02-15", "text": "Read."},
        "policyArea": "Education",
    }
    out = parse_member_legislation_item(raw)
    assert out["policy_area"] == "Education"
    assert out["id"] == "s12-119"
