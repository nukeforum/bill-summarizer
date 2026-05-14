"""Tests for manifest path resolution and load/save round-trips."""
import json

import _common


def test_manifest_path_for(tmp_path, monkeypatch):
    monkeypatch.setattr(_common, "OUTPUT_DIR", tmp_path)
    p = _common.manifest_path_for(119)
    assert p == tmp_path / "congress119_bills.json"


def test_load_manifest_returns_scaffold_when_missing(tmp_path, monkeypatch):
    monkeypatch.setattr(_common, "OUTPUT_DIR", tmp_path)
    m = _common.load_manifest(118)
    assert m["congress"] == 118
    assert m["bills"] == []
    assert "generated_at" in m


def test_save_then_load_roundtrip(tmp_path, monkeypatch):
    monkeypatch.setattr(_common, "OUTPUT_DIR", tmp_path)
    monkeypatch.setattr(_common, "current_congress", lambda *a, **kw: 119)
    manifest = {
        "generated_at": "ignored",
        "congress": 999,
        "bills": [{"id": "hr1-117", "latest_action": {"date": "2022-01-01"}}],
    }
    _common.save_manifest(117, manifest)

    raw = (tmp_path / "congress117_bills.json").read_text(encoding="utf-8")
    on_disk = json.loads(raw)
    # save_manifest is authoritative for ``congress`` and ``generated_at``
    assert on_disk["congress"] == 117
    assert on_disk["generated_at"].endswith("Z")
    assert on_disk["bills"] == manifest["bills"]
    assert raw.endswith("\n")  # trailing newline


def test_save_manifest_does_not_write_bills_json_alias(tmp_path, monkeypatch):
    """The byte-identical ``bills.json`` alias was removed once the shipped
    app migrated to the per-Congress URL via ``congresses.json``."""
    monkeypatch.setattr(_common, "OUTPUT_DIR", tmp_path)
    monkeypatch.setattr(_common, "current_congress", lambda *a, **kw: 119)
    _common.save_manifest(119, {"congress": 119, "bills": []})
    assert (tmp_path / "congress119_bills.json").exists()
    assert not (tmp_path / "bills.json").exists()

    _common.save_manifest(118, {"congress": 118, "bills": []})
    assert (tmp_path / "congress118_bills.json").exists()
    assert not (tmp_path / "bills.json").exists()
