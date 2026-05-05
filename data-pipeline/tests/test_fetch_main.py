"""Integration test for fetch_bills.main with mocked Congress.gov client."""
import json
from datetime import datetime, timezone
from unittest.mock import patch

import _common
import fetch_bills


class _FakeClient:
    """In-memory Congress.gov stub.

    ``list_responses`` is iterated for /bill/{congress} list calls; each entry
    is the body of the next response. Detail/summary/text endpoints fall
    through to a static mapping.
    """
    def __init__(self, list_responses, detail_map):
        self._list_iter = iter(list_responses)
        self._detail_map = detail_map

    def get(self, path, **params):
        if "/bill/" in path and path.count("/") == 2:
            try:
                return next(self._list_iter)
            except StopIteration:
                return {"bills": []}
        return self._detail_map.get(path, {})


def _summary(bill_type, number, action_text, action_date, title="A bill"):
    return {
        "type": bill_type,
        "number": str(number),
        "title": title,
        "latestAction": {"text": action_text, "actionDate": action_date},
    }


def _detail_resp(bill_type, number, congress):
    return {"bill": {
        "title": "A bill",
        "introducedDate": "2025-01-15",
        "sponsors": [{"fullName": "Sen. Test, Test [D-XX]", "party": "D", "state": "XX"}],
        "titles": [],
    }}


def _detail_map(bill_type, number, congress):
    base = f"/bill/{congress}/{bill_type}/{number}"
    return {
        base: _detail_resp(bill_type, number, congress),
        f"{base}/summaries": {"summaries": []},
        f"{base}/text": {"textVersions": []},
    }


def test_main_merges_into_existing_manifest(tmp_path, monkeypatch):
    monkeypatch.setenv("CONGRESS_API_KEY", "stub")
    monkeypatch.setattr(_common, "OUTPUT_DIR", tmp_path)
    monkeypatch.setattr(_common, "STATE_DIR", tmp_path / "state")
    monkeypatch.setattr(_common, "current_congress", lambda *a, **kw: 119)
    monkeypatch.setattr(fetch_bills, "current_congress", lambda *a, **kw: 119)

    # Seed an existing manifest with one historical bill that should NOT
    # be touched by the daily run (its action date is older than the cutoff).
    existing_path = tmp_path / "congress119_bills.json"
    existing_path.write_text(json.dumps({
        "generated_at": "2026-01-01T00:00:00Z",
        "congress": 119,
        "bills": [{
            "id": "hr999-119",
            "congress": 119,
            "type": "hr",
            "number": "999",
            "title": "Old bill the daily script should preserve",
            "latest_action": {"date": "2025-06-15", "text": "Became Public Law"},
            "outcome": "enacted",
            "sponsor": {"name": "Sen. Old, Old", "party": "D", "state": "XX"},
            "introduced_date": "2025-01-01",
            "short_title": None,
            "summary_crs": None,
            "text_url_html": None,
            "text_url_xml": None,
            "text_url_pdf": None,
            "congress_gov_url": "https://example.com",
        }],
    }), encoding="utf-8")

    # Today the daily run finds one new passed-house bill.
    list_resp = {
        "bills": [_summary("hr", 1, "Passed House by recorded vote: 220-211", "2026-04-30")],
    }
    detail = _detail_map("hr", "1", 119)
    fake = _FakeClient([list_resp, {"bills": []}], detail)

    with patch("fetch_bills.CongressClient", return_value=fake):
        rc = fetch_bills.main()
    assert rc == 0

    on_disk = json.loads(existing_path.read_text(encoding="utf-8"))
    ids = {b["id"] for b in on_disk["bills"]}
    assert ids == {"hr999-119", "hr1-119"}, "old bill must be preserved, new bill appended"

    alias = json.loads((tmp_path / "bills.json").read_text(encoding="utf-8"))
    assert alias == on_disk

    index = json.loads((tmp_path / "congresses.json").read_text(encoding="utf-8"))
    assert index["current_congress"] == 119
    assert any(c["congress"] == 119 and c["bill_count"] == 2 for c in index["congresses"])
