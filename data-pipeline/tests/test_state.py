"""Tests for backfill state seeding, persistence, and cursor advancement."""
import json

import _common


def test_initial_state_seeds_queue_to_93(monkeypatch):
    monkeypatch.setattr(_common, "current_congress", lambda *a, **kw: 119)
    state = _common.initial_state()
    assert state["active_congress"] == 119
    assert state["active_offset"] == 0
    assert state["queue"][0] == 119
    assert state["queue"][-1] == 93
    # 119 down to 93 inclusive = 27 entries
    assert len(state["queue"]) == 27
    assert state["completed"] == []
    assert state["last_run_at"] is None


def test_load_state_returns_initial_when_missing(tmp_path, monkeypatch):
    monkeypatch.setattr(_common, "STATE_DIR", tmp_path)
    monkeypatch.setattr(_common, "current_congress", lambda *a, **kw: 119)
    state = _common.load_state()
    assert state["active_congress"] == 119


def test_load_state_returns_initial_when_unparseable(tmp_path, monkeypatch):
    monkeypatch.setattr(_common, "STATE_DIR", tmp_path)
    monkeypatch.setattr(_common, "current_congress", lambda *a, **kw: 119)
    (tmp_path / "backfill_state.json").write_text("not json", encoding="utf-8")
    state = _common.load_state()
    assert state["active_congress"] == 119


def test_save_then_load_roundtrip(tmp_path, monkeypatch):
    monkeypatch.setattr(_common, "STATE_DIR", tmp_path)
    payload = {
        "active_congress": 118,
        "active_offset": 750,
        "queue": [118, 117],
        "completed": [119],
        "last_run_at": "2026-05-05T00:00:00Z",
    }
    _common.save_state(payload)
    raw = (tmp_path / "backfill_state.json").read_text(encoding="utf-8")
    assert json.loads(raw) == payload
    assert raw.endswith("\n")
    loaded = _common.load_state()
    assert loaded == payload


def test_advance_state_full_page_bumps_offset(monkeypatch):
    monkeypatch.setattr(_common, "current_congress", lambda *a, **kw: 119)
    state = _common.initial_state()
    new = _common.advance_state(state, page_returned=_common.LIST_PAGE_LIMIT, pages_consumed=4)
    assert new["active_congress"] == 119
    assert new["active_offset"] == 4 * _common.LIST_PAGE_LIMIT
    assert 119 not in new["completed"]
    assert new["last_run_at"] is not None


def test_advance_state_short_page_marks_complete_and_pops_queue(monkeypatch):
    monkeypatch.setattr(_common, "current_congress", lambda *a, **kw: 119)
    state = _common.initial_state()
    new = _common.advance_state(state, page_returned=12, pages_consumed=1)
    assert 119 in new["completed"]
    assert new["active_congress"] == 118
    assert new["active_offset"] == 0
    assert 119 not in new["queue"]
    assert new["queue"][0] == 118


def test_advance_state_short_page_with_queue_exhausted(monkeypatch):
    monkeypatch.setattr(_common, "current_congress", lambda *a, **kw: 119)
    state = {
        "active_congress": 93,
        "active_offset": 1500,
        "queue": [93],
        "completed": list(range(94, 120)),
        "last_run_at": None,
    }
    new = _common.advance_state(state, page_returned=5, pages_consumed=1)
    assert 93 in new["completed"]
    assert new["queue"] == []
    assert new["active_congress"] is None
    assert new["active_offset"] == 0


def test_advance_state_idempotent_through_save_load_roundtrip(tmp_path, monkeypatch):
    """Calling advance_state, persisting, reloading, and advancing again must
    produce the same cursor sequence as a contiguous in-memory chain."""
    monkeypatch.setattr(_common, "STATE_DIR", tmp_path)
    monkeypatch.setattr(_common, "current_congress", lambda *a, **kw: 119)

    state = _common.initial_state()
    assert state["active_offset"] == 0

    # First chunk: full page returned, cursor advances to 250.
    new = _common.advance_state(state, page_returned=_common.LIST_PAGE_LIMIT, pages_consumed=1)
    _common.save_state(new)

    reloaded = _common.load_state()
    assert reloaded["active_offset"] == _common.LIST_PAGE_LIMIT
    assert reloaded["active_congress"] == 119
    assert reloaded["queue"] == new["queue"]
    assert reloaded["completed"] == new["completed"]

    # Second chunk: another full page, cursor advances to 500.
    second = _common.advance_state(reloaded, page_returned=_common.LIST_PAGE_LIMIT, pages_consumed=1)
    _common.save_state(second)

    reloaded2 = _common.load_state()
    assert reloaded2["active_offset"] == 2 * _common.LIST_PAGE_LIMIT
    # Third chunk: short page exhausts Congress 119, advances to 118.
    third = _common.advance_state(reloaded2, page_returned=10, pages_consumed=1)
    assert 119 in third["completed"]
    assert third["active_congress"] == 118
    assert third["active_offset"] == 0
