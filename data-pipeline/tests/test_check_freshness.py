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
    (output_dir / "congress119_votes.json").write_text(json.dumps({
        "congress": 119,
        "generated_at": _iso(now - timedelta(hours=6)),
        "vote_count": 0,
        "votes": [],
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


def test_stale_votes_index_flagged(tmp_path, monkeypatch):
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_fresh_world(tmp_path, monkeypatch, now)
    p = _common.OUTPUT_DIR / "congress119_votes.json"
    p.write_text(json.dumps({
        "congress": 119,
        "generated_at": _iso(now - timedelta(days=3)),
        "vote_count": 0,
        "votes": [],
    }), encoding="utf-8")
    failures = check_freshness.check(now=now)
    assert any("votes:" in f and "older than" in f for f in failures), failures


def test_missing_votes_index_flagged(tmp_path, monkeypatch):
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_fresh_world(tmp_path, monkeypatch, now)
    (_common.OUTPUT_DIR / "congress119_votes.json").unlink()
    failures = check_freshness.check(now=now)
    assert any("votes:" in f and "missing" in f for f in failures), failures


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


def _write_election_calendar(now: datetime, events: list[dict]) -> None:
    (_common.OUTPUT_DIR / "election_calendar.json").write_text(json.dumps({
        "generated_at": _iso(now),
        "source": "test",
        "elections": events,
    }), encoding="utf-8")


def test_absent_election_calendar_is_not_a_failure(tmp_path, monkeypatch):
    """The fresh world never seeds an election calendar; its absence is
    tolerated until the workflow is live (issue #23)."""
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_fresh_world(tmp_path, monkeypatch, now)
    assert not (_common.OUTPUT_DIR / "election_calendar.json").is_file()
    assert not any("election:" in f for f in check_freshness.check(now=now))


def test_passed_registration_deadline_on_upcoming_election_flagged(tmp_path, monkeypatch):
    """A registration deadline dated before today, for an election still in the
    future, is stale by definition and must trip the lookahead check (#35)."""
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_fresh_world(tmp_path, monkeypatch, now)
    today = now.date()
    _write_election_calendar(now, [{
        "state": "GA",
        "date": (today + timedelta(days=30)).isoformat(),
        "type": "primary",
        "election_year": 2026,
        "registration": {
            "online": (today - timedelta(days=5)).isoformat(),
            "source": "https://sos.ga.gov",
        },
    }])
    failures = check_freshness.check(now=now)
    assert any("election: GA" in f and "registration" in f and "stale" in f for f in failures), failures


def test_future_registration_deadline_on_upcoming_election_ok(tmp_path, monkeypatch):
    """A deadline that is still ahead of today is fine — that's the whole point
    of publishing it."""
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_fresh_world(tmp_path, monkeypatch, now)
    today = now.date()
    _write_election_calendar(now, [{
        "state": "GA",
        "date": (today + timedelta(days=30)).isoformat(),
        "type": "primary",
        "election_year": 2026,
        "registration": {"online": (today + timedelta(days=10)).isoformat()},
    }])
    assert not any("registration" in f for f in check_freshness.check(now=now))


def test_passed_registration_deadline_on_past_election_ignored(tmp_path, monkeypatch):
    """A past election is out of the lookahead window entirely; its (also-past)
    registration deadline is not flagged. The horizon check owns 'no upcoming
    election', not this per-event deadline check."""
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_fresh_world(tmp_path, monkeypatch, now)
    today = now.date()
    _write_election_calendar(now, [
        {  # keep a real upcoming event so the horizon check stays green
            "state": "US",
            "date": (today + timedelta(days=200)).isoformat(),
            "type": "general",
            "election_year": 2026,
        },
        {  # a past primary whose deadline is also past — not our concern
            "state": "TX",
            "date": (today - timedelta(days=30)).isoformat(),
            "type": "primary",
            "election_year": 2026,
            "registration": {"online": (today - timedelta(days=60)).isoformat()},
        },
    ])
    assert not any("registration" in f for f in check_freshness.check(now=now))


def test_same_day_registration_is_exempt(tmp_path, monkeypatch):
    """same_day carries no date, so an upcoming election that only advertises
    same-day registration never trips the deadline check."""
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_fresh_world(tmp_path, monkeypatch, now)
    today = now.date()
    _write_election_calendar(now, [{
        "state": "MN",
        "date": (today + timedelta(days=30)).isoformat(),
        "type": "primary",
        "election_year": 2026,
        "registration": {"same_day": True},
    }])
    assert not any("registration" in f for f in check_freshness.check(now=now))


def _write_shard_set(now: datetime, shards: list[dict], *, total_bills: int | None = None) -> None:
    """Publish a shard index plus a shard file per entry. Each entry is
    ``{"page", "path", "count"}``; the shard file carries ``count`` empty-ish
    bills so the index/file counts line up unless a test deliberately skews one."""
    entries = []
    for s in shards:
        entries.append({
            "page": s["page"],
            "path": s["path"],
            "count": s["count"],
            "first_action_date": None,
            "last_action_date": None,
        })
        actual = s.get("actual", s["count"])
        (_common.OUTPUT_DIR / s["path"]).write_text(json.dumps({
            "generated_at": _iso(now),
            "congress": 119,
            "votes_coverage": False,
            "bills": [{"id": f"b{i}"} for i in range(actual)],
        }), encoding="utf-8")
    (_common.OUTPUT_DIR / "congress119_bills_index.json").write_text(json.dumps({
        "generated_at": _iso(now),
        "congress": 119,
        "page_size": 500,
        "total_bills": sum(s["count"] for s in shards) if total_bills is None else total_bills,
        "votes_coverage": False,
        "shards": entries,
    }), encoding="utf-8")


def test_absent_shard_index_is_not_a_failure(tmp_path, monkeypatch):
    """The fresh world never seeds a shard index; its absence is tolerated during
    the dual-publish transition (issue #40)."""
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_fresh_world(tmp_path, monkeypatch, now)
    assert not (_common.OUTPUT_DIR / "congress119_bills_index.json").is_file()
    assert not any("shards:" in f for f in check_freshness.check(now=now))


def test_consistent_shard_set_is_green(tmp_path, monkeypatch):
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_fresh_world(tmp_path, monkeypatch, now)
    _write_shard_set(now, [
        {"page": 1, "path": "congress119_bills_p001.json", "count": 3},
        {"page": 2, "path": "congress119_bills_p002.json", "count": 1},
    ])
    assert not any("shards:" in f for f in check_freshness.check(now=now))


def test_missing_shard_file_flagged(tmp_path, monkeypatch):
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_fresh_world(tmp_path, monkeypatch, now)
    _write_shard_set(now, [{"page": 1, "path": "congress119_bills_p001.json", "count": 2}])
    (_common.OUTPUT_DIR / "congress119_bills_p001.json").unlink()
    failures = check_freshness.check(now=now)
    assert any("shards:" in f and "missing or unreadable shard" in f for f in failures), failures


def test_shard_count_mismatch_flagged(tmp_path, monkeypatch):
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_fresh_world(tmp_path, monkeypatch, now)
    # Index lists 5 but the shard file only holds 2 bills.
    _write_shard_set(now, [
        {"page": 1, "path": "congress119_bills_p001.json", "count": 5, "actual": 2},
    ])
    failures = check_freshness.check(now=now)
    assert any("holds 2 bills but the index lists 5" in f for f in failures), failures


def test_total_bills_mismatch_flagged(tmp_path, monkeypatch):
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_fresh_world(tmp_path, monkeypatch, now)
    # Shard counts sum to 3 but total_bills claims 99.
    _write_shard_set(now, [
        {"page": 1, "path": "congress119_bills_p001.json", "count": 3},
    ], total_bills=99)
    failures = check_freshness.check(now=now)
    assert any("total_bills=99" in f and "sum of shard counts 3" in f for f in failures), failures


def test_orphaned_shard_file_flagged(tmp_path, monkeypatch):
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_fresh_world(tmp_path, monkeypatch, now)
    _write_shard_set(now, [{"page": 1, "path": "congress119_bills_p001.json", "count": 2}])
    # A stale shard from a prior larger run the index no longer references.
    (_common.OUTPUT_DIR / "congress119_bills_p002.json").write_text(json.dumps({
        "generated_at": _iso(now), "congress": 119, "votes_coverage": False, "bills": [],
    }), encoding="utf-8")
    failures = check_freshness.check(now=now)
    assert any("orphaned shard congress119_bills_p002.json" in f for f in failures), failures


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
