"""Tests for rebuild_index — produces docs/data/congresses.json from disk."""
import json

import _common


def _write_manifest(dir_path, congress, bills):
    (dir_path / f"congress{congress}_bills.json").write_text(
        json.dumps({"congress": congress, "generated_at": "2026-05-05T00:00:00Z", "bills": bills}),
        encoding="utf-8",
    )


def test_rebuild_index_empty_dir(tmp_path, monkeypatch):
    monkeypatch.setattr(_common, "OUTPUT_DIR", tmp_path)
    monkeypatch.setattr(_common, "STATE_DIR", tmp_path / "state")
    monkeypatch.setattr(_common, "current_congress", lambda *a, **kw: 119)
    idx = _common.rebuild_index()
    assert idx["current_congress"] == 119
    assert idx["congresses"] == []
    assert (tmp_path / "congresses.json").exists()


def test_rebuild_index_reads_existing_manifests(tmp_path, monkeypatch):
    monkeypatch.setattr(_common, "OUTPUT_DIR", tmp_path)
    monkeypatch.setattr(_common, "STATE_DIR", tmp_path / "state")
    monkeypatch.setattr(_common, "current_congress", lambda *a, **kw: 119)
    _write_manifest(tmp_path, 119, [
        {"id": "hr1-119", "latest_action": {"date": "2025-03-01"}},
        {"id": "s2-119", "latest_action": {"date": "2026-04-30"}},
    ])
    _write_manifest(tmp_path, 118, [
        {"id": "hr5-118", "latest_action": {"date": "2024-12-15"}},
    ])
    idx = _common.rebuild_index()
    assert [c["congress"] for c in idx["congresses"]] == [119, 118]
    cur = idx["congresses"][0]
    assert cur["bill_count"] == 2
    assert cur["first_action_date"] == "2025-03-01"
    assert cur["last_action_date"] == "2026-04-30"
    assert cur["manifest_path"] == "congress119_bills.json"
    assert cur["is_current"] is True
    assert cur["backfill_complete"] is False
    prev = idx["congresses"][1]
    assert prev["is_current"] is False


def test_rebuild_index_marks_completed_from_state(tmp_path, monkeypatch):
    monkeypatch.setattr(_common, "OUTPUT_DIR", tmp_path)
    state_dir = tmp_path / "state"
    state_dir.mkdir()
    monkeypatch.setattr(_common, "STATE_DIR", state_dir)
    monkeypatch.setattr(_common, "current_congress", lambda *a, **kw: 119)
    (state_dir / "backfill_state.json").write_text(
        json.dumps({"completed": [118, 117]}),
        encoding="utf-8",
    )
    _write_manifest(tmp_path, 118, [])
    _write_manifest(tmp_path, 117, [])
    idx = _common.rebuild_index()
    by_cong = {c["congress"]: c for c in idx["congresses"]}
    assert by_cong[118]["backfill_complete"] is True
    assert by_cong[117]["backfill_complete"] is True


def test_rebuild_index_ignores_unrelated_files(tmp_path, monkeypatch):
    monkeypatch.setattr(_common, "OUTPUT_DIR", tmp_path)
    monkeypatch.setattr(_common, "STATE_DIR", tmp_path / "state")
    monkeypatch.setattr(_common, "current_congress", lambda *a, **kw: 119)
    (tmp_path / "bills.json").write_text("{}", encoding="utf-8")
    (tmp_path / "congresses.json").write_text("{}", encoding="utf-8")
    _write_manifest(tmp_path, 119, [])
    idx = _common.rebuild_index()
    assert [c["congress"] for c in idx["congresses"]] == [119]
