"""Integration tests for backfill_bills.main."""
import json
from unittest.mock import patch

import _common


class _FakeClient:
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


def _summary(bill_type, number, action_text, action_date):
    return {
        "type": bill_type,
        "number": str(number),
        "title": "A bill",
        "latestAction": {"text": action_text, "actionDate": action_date},
    }


def _detail_map_for(congress, type_number_pairs):
    out = {}
    for bt, num in type_number_pairs:
        base = f"/bill/{congress}/{bt}/{num}"
        out[base] = {"bill": {
            "title": "A bill",
            "introducedDate": "2025-01-15",
            "sponsors": [{"fullName": "Sen. Test, Test [D-XX]", "party": "D", "state": "XX"}],
            "titles": [],
        }}
        out[f"{base}/summaries"] = {"summaries": []}
        out[f"{base}/text"] = {"textVersions": []}
    return out


def test_main_first_run_seeds_state_and_writes_manifest(tmp_path, monkeypatch):
    import backfill_bills

    monkeypatch.setenv("CONGRESS_API_KEY", "stub")
    monkeypatch.setattr(_common, "OUTPUT_DIR", tmp_path)
    monkeypatch.setattr(_common, "STATE_DIR", tmp_path / "state")
    monkeypatch.setattr(_common, "current_congress", lambda *a, **kw: 119)
    monkeypatch.setattr(backfill_bills, "BACKFILL_PAGES_PER_RUN", 1)

    list_resp = {
        "bills": [
            _summary("hr", 1, "Became Public Law No: 119-1", "2025-02-15"),
            _summary("s", 2, "Referred to committee.", "2025-03-01"),
        ],
    }
    detail = _detail_map_for(119, [("hr", "1"), ("s", "2")])
    fake = _FakeClient([list_resp], detail)

    with patch("backfill_bills.CongressClient", return_value=fake):
        rc = backfill_bills.main()
    assert rc == 0

    manifest = json.loads((tmp_path / "congress119_bills.json").read_text(encoding="utf-8"))
    ids = {b["id"] for b in manifest["bills"]}
    assert ids == {"hr1-119"}, "only the passed bill should be kept"

    # Alias is byte-identical for current Congress.
    alias = json.loads((tmp_path / "bills.json").read_text(encoding="utf-8"))
    assert alias == manifest

    state = json.loads((tmp_path / "state" / "backfill_state.json").read_text(encoding="utf-8"))
    # Page returned 2 < LIST_PAGE_LIMIT (250) so 119 is marked complete.
    assert 119 in state["completed"]
    assert state["active_congress"] == 118


def test_main_resumes_from_existing_state(tmp_path, monkeypatch):
    import backfill_bills

    monkeypatch.setenv("CONGRESS_API_KEY", "stub")
    monkeypatch.setattr(_common, "OUTPUT_DIR", tmp_path)
    state_dir = tmp_path / "state"
    state_dir.mkdir()
    monkeypatch.setattr(_common, "STATE_DIR", state_dir)
    monkeypatch.setattr(_common, "current_congress", lambda *a, **kw: 119)
    monkeypatch.setattr(backfill_bills, "BACKFILL_PAGES_PER_RUN", 1)

    # Pre-seed state mid-Congress at offset 500.
    (state_dir / "backfill_state.json").write_text(json.dumps({
        "active_congress": 118,
        "active_offset": 500,
        "queue": [118, 117, 116],
        "completed": [119],
        "last_run_at": "2026-05-04T00:00:00Z",
    }), encoding="utf-8")

    captured_offsets: list[int] = []

    class _RecordingFake:
        def get(self, path, **params):
            if "/bill/" in path and path.count("/") == 2:
                captured_offsets.append(params.get("offset", 0))
                return {"bills": [
                    _summary("hr", 5, "Passed House by recorded vote: 220-211", "2024-06-01"),
                ]}
            base = f"/bill/118/hr/5"
            return {
                base: {"bill": {"title": "Test", "introducedDate": "2024-01-01", "sponsors": [{"fullName": "Rep. X, Y [D-CA-12]"}], "titles": []}},
                f"{base}/summaries": {"summaries": []},
                f"{base}/text": {"textVersions": []},
            }.get(path, {})

    with patch("backfill_bills.CongressClient", return_value=_RecordingFake()):
        rc = backfill_bills.main()
    assert rc == 0

    # Started at offset 500, not 0.
    assert captured_offsets[0] == 500

    state = json.loads((state_dir / "backfill_state.json").read_text(encoding="utf-8"))
    # Short page (1 < LIST_PAGE_LIMIT) ⇒ 118 complete; advance to 117.
    assert 118 in state["completed"]
    assert state["active_congress"] == 117


def test_main_full_page_bumps_offset(tmp_path, monkeypatch):
    import backfill_bills

    monkeypatch.setenv("CONGRESS_API_KEY", "stub")
    monkeypatch.setattr(_common, "OUTPUT_DIR", tmp_path)
    state_dir = tmp_path / "state"
    state_dir.mkdir()
    monkeypatch.setattr(_common, "STATE_DIR", state_dir)
    monkeypatch.setattr(_common, "current_congress", lambda *a, **kw: 119)
    # One page per run, but the page is "full" (== LIST_PAGE_LIMIT) so the
    # cursor advances rather than completing the Congress.
    monkeypatch.setattr(backfill_bills, "BACKFILL_PAGES_PER_RUN", 1)

    full_page = {"bills": [
        _summary("hr", n, "Referred to committee.", "2025-01-01")
        for n in range(_common.LIST_PAGE_LIMIT)
    ]}
    fake = _FakeClient([full_page], {})

    with patch("backfill_bills.CongressClient", return_value=fake):
        rc = backfill_bills.main()
    assert rc == 0

    state = json.loads((state_dir / "backfill_state.json").read_text(encoding="utf-8"))
    assert 119 not in state["completed"]
    assert state["active_congress"] == 119
    assert state["active_offset"] == _common.LIST_PAGE_LIMIT
