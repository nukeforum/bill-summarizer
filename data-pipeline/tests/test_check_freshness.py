"""Tests for ``check_freshness.check`` — covers each failure axis and the
all-green path. Heavy fixture so each axis is independent."""
from __future__ import annotations

import json
from datetime import datetime, timedelta, timezone
from pathlib import Path

import _common
import check_freshness


def _iso(ts: datetime) -> str:
    return ts.replace(microsecond=0).isoformat().replace("+00:00", "Z")


def _seed_fresh_world(tmp_path: Path, monkeypatch, now: datetime) -> None:
    """Lay down a freshly-generated manifest, members file, calendar, and
    backfill state. Each individual test then mutates exactly one of these
    to assert the corresponding failure surfaces."""
    output_dir = tmp_path / "data"
    state_dir = tmp_path / "state"
    output_dir.mkdir()
    state_dir.mkdir()
    (output_dir / "members").mkdir()
    monkeypatch.setattr(_common, "OUTPUT_DIR", output_dir)
    monkeypatch.setattr(_common, "STATE_DIR", state_dir)
    monkeypatch.setattr(check_freshness, "OUTPUT_DIR", output_dir)
    monkeypatch.setattr(check_freshness, "STATE_DIR", state_dir)
    monkeypatch.setattr(_common, "current_congress", lambda *a, **kw: 119)
    monkeypatch.setattr(check_freshness, "current_congress", lambda *a, **kw: 119)

    (output_dir / "congress119_bills.json").write_text(json.dumps({
        "generated_at": _iso(now - timedelta(hours=6)),
        "congress": 119,
        "bills": [],
    }), encoding="utf-8")
    (output_dir / "members_119.json").write_text(json.dumps({
        "generated_at": _iso(now - timedelta(days=1)),
        "congress": 119,
        "members": [],
    }), encoding="utf-8")

    today = now.date()
    far = (today + timedelta(days=60)).isoformat()
    (output_dir / "session_calendar.json").write_text(json.dumps({
        "generated_at": _iso(now),
        "chambers": {
            "house": {"session_days": [today.isoformat(), far]},
            "senate": {"session_days": [today.isoformat(), far]},
        },
    }), encoding="utf-8")
    (state_dir / "backfill_state.json").write_text(json.dumps({
        "active_congress": 118,
        "active_offset": 0,
        "queue": [118, 117],
        "completed": [119],
        "last_run_at": _iso(now - timedelta(hours=6)),
    }), encoding="utf-8")


def test_all_green(tmp_path, monkeypatch):
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_fresh_world(tmp_path, monkeypatch, now)
    assert check_freshness.check(now=now) == []


def test_stale_bills_manifest_flagged(tmp_path, monkeypatch):
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_fresh_world(tmp_path, monkeypatch, now)
    stale = _iso(now - timedelta(days=3))
    p = _common.OUTPUT_DIR / "congress119_bills.json"
    p.write_text(json.dumps({"generated_at": stale, "congress": 119, "bills": []}),
                 encoding="utf-8")
    failures = check_freshness.check(now=now)
    assert any("bills:" in f and "older than" in f for f in failures), failures


def test_missing_bills_manifest_flagged(tmp_path, monkeypatch):
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_fresh_world(tmp_path, monkeypatch, now)
    (_common.OUTPUT_DIR / "congress119_bills.json").unlink()
    failures = check_freshness.check(now=now)
    assert any("bills:" in f and "missing" in f for f in failures), failures


def test_stale_members_index_flagged(tmp_path, monkeypatch):
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_fresh_world(tmp_path, monkeypatch, now)
    p = _common.OUTPUT_DIR / "members_119.json"
    p.write_text(json.dumps({
        "generated_at": _iso(now - timedelta(days=20)),
        "congress": 119,
        "members": [],
    }), encoding="utf-8")
    failures = check_freshness.check(now=now)
    assert any("members:" in f and "older than" in f for f in failures), failures


def test_calendar_no_lookahead_flagged(tmp_path, monkeypatch):
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_fresh_world(tmp_path, monkeypatch, now)
    today = now.date()
    # Last known House session day is only 10 days out — below threshold.
    (_common.OUTPUT_DIR / "session_calendar.json").write_text(json.dumps({
        "generated_at": _iso(now),
        "chambers": {
            "house": {"session_days": [(today + timedelta(days=10)).isoformat()]},
            "senate": {"session_days": [(today + timedelta(days=60)).isoformat()]},
        },
    }), encoding="utf-8")
    failures = check_freshness.check(now=now)
    assert any("calendar: house" in f and "less than" in f for f in failures), failures
    assert not any("calendar: senate" in f for f in failures), failures


def test_calendar_chamber_fully_past_flagged(tmp_path, monkeypatch):
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_fresh_world(tmp_path, monkeypatch, now)
    # House has only past days; Senate is fine.
    (_common.OUTPUT_DIR / "session_calendar.json").write_text(json.dumps({
        "generated_at": _iso(now),
        "chambers": {
            "house": {"session_days": ["2024-01-01"]},
            "senate": {"session_days": [(now.date() + timedelta(days=60)).isoformat()]},
        },
    }), encoding="utf-8")
    failures = check_freshness.check(now=now)
    assert any("calendar: house" in f and "no session days" in f for f in failures), failures


def test_stale_backfill_cursor_flagged(tmp_path, monkeypatch):
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_fresh_world(tmp_path, monkeypatch, now)
    (_common.STATE_DIR / "backfill_state.json").write_text(json.dumps({
        "active_congress": 118,
        "active_offset": 0,
        "queue": [118, 117],
        "completed": [119],
        "last_run_at": _iso(now - timedelta(days=5)),
    }), encoding="utf-8")
    failures = check_freshness.check(now=now)
    assert any("backfill:" in f and "older than" in f for f in failures), failures


def test_empty_backfill_queue_is_not_a_failure(tmp_path, monkeypatch):
    """When the backfill queue is exhausted the cursor stays at None and
    last_run_at can legitimately be ancient. Don't flag it."""
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_fresh_world(tmp_path, monkeypatch, now)
    (_common.STATE_DIR / "backfill_state.json").write_text(json.dumps({
        "active_congress": None,
        "active_offset": 0,
        "queue": [],
        "completed": [119, 118, 117],
        "last_run_at": _iso(now - timedelta(days=400)),
    }), encoding="utf-8")
    assert check_freshness.check(now=now) == []
