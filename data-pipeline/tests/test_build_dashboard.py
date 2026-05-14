"""Tests for ``build_dashboard.render`` — verifies the HTML reflects the
state of disk artifacts and that the overall banner / per-row status chips
agree with the same thresholds the freshness alarm uses."""
from __future__ import annotations

import json
import os
from datetime import datetime, timedelta, timezone
from pathlib import Path

import _common
import build_dashboard
import check_freshness


def _iso(ts: datetime) -> str:
    return ts.replace(microsecond=0).isoformat().replace("+00:00", "Z")


def _seed_world(
    tmp_path: Path,
    monkeypatch,
    now: datetime,
    *,
    bills_age: timedelta = timedelta(hours=2),
    members_age: timedelta = timedelta(days=1),
    house_lookahead_days: int = 60,
    senate_lookahead_days: int = 60,
    backfill_active: int | None = 118,
    backfill_ran_ago: timedelta = timedelta(hours=2),
    create_zip: bool = True,
    zip_age: timedelta = timedelta(days=10),
) -> Path:
    """Lay down a fully-seeded ``docs/data`` + ``state`` tree under tmp_path
    and return the repo-root equivalent. Each kwarg lets one test perturb
    exactly one artifact."""
    repo_root = tmp_path
    output_dir = repo_root / "docs" / "data"
    state_dir = repo_root / "state"
    android_assets = repo_root / "android" / "app" / "src" / "main" / "assets"
    output_dir.mkdir(parents=True)
    state_dir.mkdir(parents=True)
    android_assets.mkdir(parents=True)
    (output_dir / "members").mkdir()

    monkeypatch.setattr(_common, "OUTPUT_DIR", output_dir)
    monkeypatch.setattr(_common, "STATE_DIR", state_dir)
    monkeypatch.setattr(check_freshness, "OUTPUT_DIR", output_dir)
    monkeypatch.setattr(check_freshness, "STATE_DIR", state_dir)
    monkeypatch.setattr(build_dashboard, "OUTPUT_DIR", output_dir)
    monkeypatch.setattr(build_dashboard, "STATE_DIR", state_dir)
    monkeypatch.setattr(_common, "current_congress", lambda *a, **kw: 119)
    monkeypatch.setattr(build_dashboard, "current_congress", lambda *a, **kw: 119)

    (output_dir / "congress119_bills.json").write_text(json.dumps({
        "generated_at": _iso(now - bills_age),
        "congress": 119,
        "bills": [{"id": f"hr{n}-119", "latest_action": {"date": "2026-04-01"}} for n in range(50)],
    }), encoding="utf-8")
    (output_dir / "members_119.json").write_text(json.dumps({
        "generated_at": _iso(now - members_age),
        "congress": 119,
        "members": [{"bioguide_id": f"X{n:06d}"} for n in range(536)],
    }), encoding="utf-8")
    (output_dir / "congresses.json").write_text(json.dumps({
        "generated_at": _iso(now),
        "current_congress": 119,
        "congresses": [{
            "congress": 119,
            "bill_count": 50,
            "first_action_date": "2025-01-15",
            "last_action_date": "2026-04-01",
            "manifest_path": "congress119_bills.json",
            "is_current": True,
            "backfill_complete": False,
        }],
    }), encoding="utf-8")

    today = now.date()
    house_last = (today + timedelta(days=house_lookahead_days)).isoformat()
    senate_last = (today + timedelta(days=senate_lookahead_days)).isoformat()
    (output_dir / "session_calendar.json").write_text(json.dumps({
        "generated_at": _iso(now),
        "chambers": {
            "house": {"session_days": [today.isoformat(), house_last]},
            "senate": {"session_days": [today.isoformat(), senate_last]},
        },
    }), encoding="utf-8")

    (state_dir / "backfill_state.json").write_text(json.dumps({
        "active_congress": backfill_active,
        "active_offset": 12_000 if backfill_active is not None else 0,
        "queue": [118, 117] if backfill_active is not None else [],
        "completed": [119] if backfill_active is not None else [119, 118, 117],
        "last_run_at": _iso(now - backfill_ran_ago),
    }), encoding="utf-8")

    if create_zip:
        zip_path = android_assets / "zip_to_cd.json"
        zip_path.write_text("{}", encoding="utf-8")
        # Backdate the mtime by zip_age.
        target = (now - zip_age).timestamp()
        os.utime(zip_path, (target, target))

    return repo_root


def test_all_green(tmp_path, monkeypatch):
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_world(tmp_path, monkeypatch, now)
    html_out = build_dashboard.render(now=now)
    assert "overall-ok" in html_out
    assert "All artifacts fresh" in html_out
    # No <tr> carries the row-fail class — "row-fail" itself appears in the
    # CSS, so check for the actual row markup.
    assert '<tr class="row-fail"' not in html_out
    # Spot-check the key labels render.
    assert "Bills · Congress 119" in html_out
    assert "536 members" in html_out
    assert "Calendar · house" in html_out
    assert "Calendar · senate" in html_out


def test_stale_bills_flags_row_and_overall(tmp_path, monkeypatch):
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_world(tmp_path, monkeypatch, now, bills_age=timedelta(days=3))
    html_out = build_dashboard.render(now=now)
    assert "overall-fail" in html_out
    # The bills row is flagged.
    assert 'row-fail"><td class="chip">✗</td><td class="name">Bills · Congress 119' in html_out


def test_stale_members_flags_row(tmp_path, monkeypatch):
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_world(tmp_path, monkeypatch, now, members_age=timedelta(days=20))
    html_out = build_dashboard.render(now=now)
    assert "overall-fail" in html_out
    assert 'row-fail"><td class="chip">✗</td><td class="name">Members · 119' in html_out


def test_short_house_lookahead_flags_only_house(tmp_path, monkeypatch):
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_world(
        tmp_path, monkeypatch, now,
        house_lookahead_days=10,
        senate_lookahead_days=60,
    )
    html_out = build_dashboard.render(now=now)
    assert "overall-fail" in html_out
    assert 'row-fail"><td class="chip">✗</td><td class="name">Calendar · house' in html_out
    assert 'row-ok"><td class="chip">✓</td><td class="name">Calendar · senate' in html_out


def test_backfill_complete_renders_ok(tmp_path, monkeypatch):
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_world(
        tmp_path, monkeypatch, now,
        backfill_active=None,
        backfill_ran_ago=timedelta(days=365),
    )
    html_out = build_dashboard.render(now=now)
    # An ancient last_run_at is fine once the queue is empty.
    assert 'row-ok"><td class="chip">✓</td><td class="name">Backfill cursor' in html_out
    assert "all 3 Congresses complete" in html_out


def test_stale_backfill_cursor_flags_row(tmp_path, monkeypatch):
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_world(tmp_path, monkeypatch, now, backfill_ran_ago=timedelta(days=5))
    html_out = build_dashboard.render(now=now)
    assert 'row-fail"><td class="chip">✗</td><td class="name">Backfill cursor' in html_out


def test_missing_zip_crosswalk_flags_row(tmp_path, monkeypatch):
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_world(tmp_path, monkeypatch, now, create_zip=False)
    html_out = build_dashboard.render(now=now)
    assert 'row-fail"><td class="chip">✗</td><td class="name">ZIP → district crosswalk' in html_out


def test_aged_zip_crosswalk_flags_row(tmp_path, monkeypatch):
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_world(tmp_path, monkeypatch, now, zip_age=timedelta(days=200))
    html_out = build_dashboard.render(now=now)
    assert 'row-fail"><td class="chip">✗</td><td class="name">ZIP → district crosswalk' in html_out


def test_html_escapes_user_content(tmp_path, monkeypatch):
    """Manifest fields are escaped — a tampered ``generated_at`` should not
    break the page."""
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_world(tmp_path, monkeypatch, now)
    # Inject a hostile-looking value into one of the read fields.
    cong_path = _common.OUTPUT_DIR / "congresses.json"
    cong_path.write_text(json.dumps({
        "current_congress": 119,
        "congresses": [{
            "congress": 119,
            "bill_count": 1,
            "first_action_date": "<script>alert(1)</script>",
            "last_action_date": "2026-04-01",
            "manifest_path": "x",
            "is_current": True,
            "backfill_complete": False,
        }],
    }), encoding="utf-8")
    html_out = build_dashboard.render(now=now)
    assert "<script>alert(1)</script>" not in html_out
    assert "&lt;script&gt;alert(1)&lt;/script&gt;" in html_out


def test_progress_bar_reflects_completed_count(tmp_path, monkeypatch):
    now = datetime(2026, 6, 1, 12, 0, tzinfo=timezone.utc)
    _seed_world(tmp_path, monkeypatch, now)
    # Backfill state seeded with completed=[119], queue=[118, 117], active=118.
    # current_congress=119, OLDEST_API_CONGRESS=93 → total = 119-93+1 = 27.
    html_out = build_dashboard.render(now=now)
    assert 'value="1" max="27"' in html_out
    assert "1 of 27 Congresses complete" in html_out
