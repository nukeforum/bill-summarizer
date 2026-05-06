"""Integration test for fetch_members.main with a mocked Congress.gov client."""
import json
from unittest.mock import patch

import pytest

import _common
import fetch_members


class _FakeClient:
    """In-memory Congress.gov stub for member endpoints."""

    def __init__(self, list_pages, member_details, sponsored_pages, cosponsored_pages):
        self._list_pages = list(list_pages)
        self._member_details = member_details
        self._sponsored_pages = {k: list(v) for k, v in sponsored_pages.items()}
        self._cosponsored_pages = {k: list(v) for k, v in cosponsored_pages.items()}

    def get(self, path, **params):
        if path.startswith("/member/congress/") and path.count("/") == 3:
            return self._list_pages.pop(0) if self._list_pages else {"members": []}
        if path.startswith("/member/") and path.endswith("/sponsored-legislation"):
            bid = path.split("/")[2]
            pages = self._sponsored_pages.get(bid, [])
            return pages.pop(0) if pages else {"sponsoredLegislation": []}
        if path.startswith("/member/") and path.endswith("/cosponsored-legislation"):
            bid = path.split("/")[2]
            pages = self._cosponsored_pages.get(bid, [])
            return pages.pop(0) if pages else {"cosponsoredLegislation": []}
        if path.startswith("/member/") and path.count("/") == 2:
            bid = path.split("/")[2]
            return {"member": self._member_details.get(bid, {})}
        return {}


def _member_list_entry(bid, name, state, district=None, chamber="House of Representatives", party="Democratic"):
    return {
        "bioguideId": bid,
        "directOrderName": name,
        "state": state,
        "district": district,
        "partyName": party,
        "terms": [{"chamber": chamber}],
        "depiction": {"imageUrl": f"https://example.com/{bid}.jpg"},
    }


def _member_detail(bid, sponsored_count, cosponsored_count, official_url, addr="123 Cannon HOB", phone="+1-202-0000"):
    return {
        "bioguideId": bid,
        "directOrderName": "X Y",
        "officialUrl": official_url,
        "addressInformation": {"officeAddress": addr, "phoneNumber": phone},
        "sponsoredLegislation": {"count": sponsored_count},
        "cosponsoredLegislation": {"count": cosponsored_count},
    }


def _legislation_item(bill_type, number, congress, title, action_date, action_text):
    return {
        "type": bill_type,
        "number": number,
        "congress": congress,
        "latestTitle": title,
        "introducedDate": "2026-01-15",
        "latestAction": {"actionDate": action_date, "text": action_text},
        "policyArea": {"name": "Health"},
    }


def test_main_writes_index_and_per_member_files(tmp_path, monkeypatch):
    monkeypatch.setenv("CONGRESS_API_KEY", "stub")
    monkeypatch.setattr(_common, "OUTPUT_DIR", tmp_path)
    list_pages = [{
        "members": [
            _member_list_entry("A000001", "Alice", "Nebraska", 3, "House of Representatives", "Republican"),
            _member_list_entry("B000002", "Bob", "Michigan", None, "Senate", "Democratic"),
        ],
    }]
    member_details = {
        "A000001": _member_detail("A000001", 1, 2, "https://alice.house.gov"),
        "B000002": _member_detail("B000002", 0, 0, "https://bob.senate.gov"),
    }
    sponsored_pages = {
        "A000001": [
            {"sponsoredLegislation": [
                _legislation_item("HR", 1, 119, "Alice Bill", "2026-04-01", "Referred."),
            ]},
        ],
        "B000002": [{"sponsoredLegislation": []}],
    }
    cosponsored_pages = {
        "A000001": [
            {"cosponsoredLegislation": [
                _legislation_item("HR", 22, 119, "Other Bill", "2026-04-02", "Referred."),
                _legislation_item("S", 5, 119, "Senate Bill", "2026-03-15", "Read."),
            ]},
        ],
        "B000002": [{"cosponsoredLegislation": []}],
    }

    fake = _FakeClient([list_pages[0]], member_details, sponsored_pages, cosponsored_pages)
    with patch.object(fetch_members, "CongressClient", return_value=fake), \
         patch.object(fetch_members, "current_congress", return_value=119):
        rc = fetch_members.main()
    assert rc == 0

    index_path = tmp_path / "members_119.json"
    assert index_path.exists()
    index = json.loads(index_path.read_text())
    assert index["congress"] == 119
    assert {m["bioguide_id"] for m in index["members"]} == {"A000001", "B000002"}
    alice = next(m for m in index["members"] if m["bioguide_id"] == "A000001")
    assert alice == {
        "bioguide_id": "A000001",
        "name": "X Y",  # from member detail directOrderName
        "party": "R",
        "state": "NE",
        "district": 3,
        "chamber": "house",
        "photo_url": "https://example.com/A000001.jpg",
        "official_url": "https://alice.house.gov",
        "sponsored_count": 1,
        "cosponsored_count": 2,
        "address": "123 Cannon HOB",
        "phone": "+1-202-0000",
    }

    sponsored_path = tmp_path / "members" / "A000001_sponsored.json"
    cosponsored_path = tmp_path / "members" / "A000001_cosponsored.json"
    assert sponsored_path.exists() and cosponsored_path.exists()
    sponsored = json.loads(sponsored_path.read_text())
    assert sponsored["bioguide_id"] == "A000001"
    assert sponsored["kind"] == "sponsored"
    assert [b["id"] for b in sponsored["bills"]] == ["hr1-119"]
    cosponsored = json.loads(cosponsored_path.read_text())
    assert [b["id"] for b in cosponsored["bills"]] == ["hr22-119", "s5-119"]


def test_main_handles_empty_legislation(tmp_path, monkeypatch):
    monkeypatch.setenv("CONGRESS_API_KEY", "stub")
    monkeypatch.setattr(_common, "OUTPUT_DIR", tmp_path)
    list_pages = [{
        "members": [_member_list_entry("Z999999", "Zed", "Vermont", 0, "House of Representatives", "Independent")],
    }]
    member_details = {"Z999999": _member_detail("Z999999", 0, 0, "https://z.house.gov")}
    fake = _FakeClient([list_pages[0]], member_details,
                       {"Z999999": [{"sponsoredLegislation": []}]},
                       {"Z999999": [{"cosponsoredLegislation": []}]})
    with patch.object(fetch_members, "CongressClient", return_value=fake), \
         patch.object(fetch_members, "current_congress", return_value=119):
        rc = fetch_members.main()
    assert rc == 0
    sponsored = json.loads((tmp_path / "members" / "Z999999_sponsored.json").read_text())
    assert sponsored["bills"] == []


def test_main_no_api_key_returns_2(monkeypatch):
    monkeypatch.delenv("CONGRESS_API_KEY", raising=False)
    assert fetch_members.main() == 2


def test_fetch_legislation_rejects_unknown_kind():
    """The kind whitelist must reject typos; otherwise we'd silently no-op."""
    fake = _FakeClient([], {}, {}, {})
    with pytest.raises(ValueError, match="unknown kind"):
        fetch_members.fetch_legislation(fake, "X000001", "bogus")
