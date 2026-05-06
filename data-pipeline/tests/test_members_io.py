"""IO and shape tests for member-centric pipeline outputs."""
import json
from pathlib import Path

import _common
from _common import (
    load_members_index,
    save_members_index,
    save_member_legislation,
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
