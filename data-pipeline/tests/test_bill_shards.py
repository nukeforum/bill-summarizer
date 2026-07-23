"""Tests for the #40 sharded per-Congress bill manifest builder."""
import json

import _common


def _bill(bill_id, date):
    return {"id": bill_id, "latest_action": {"date": date}}


def test_sort_bills_recency_first_newest_first_id_tiebreak():
    bills = [
        _bill("hr1-119", "2025-03-01"),
        _bill("s2-119", "2026-04-30"),
        _bill("hr9-119", "2026-04-30"),
        _bill("hr3-119", "2024-01-01"),
    ]
    ordered = _common.sort_bills_recency_first(bills)
    assert [b["id"] for b in ordered] == ["hr9-119", "s2-119", "hr1-119", "hr3-119"]


def test_build_bill_shards_pages_and_windows():
    bills = [_bill(f"hr{i}-119", f"2026-{(i % 12) + 1:02d}-01") for i in range(1, 6)]
    index, shard_files = _common.build_bill_shards(
        119, bills, page_size=2, generated_at="2026-07-23T00:00:00Z", votes_coverage_flag=True
    )
    assert index["congress"] == 119
    assert index["page_size"] == 2
    assert index["total_bills"] == 5
    assert index["votes_coverage"] is True
    # 5 bills / page 2 -> 3 shards, only the last is short.
    assert [s["count"] for s in index["shards"]] == [2, 2, 1]
    assert [s["page"] for s in index["shards"]] == [1, 2, 3]
    assert [s["path"] for s in index["shards"]] == [
        "congress119_bills_p001.json",
        "congress119_bills_p002.json",
        "congress119_bills_p003.json",
    ]
    # Each shard's window bounds its own dates; page 1 holds the newest.
    for entry, (_, shard) in zip(index["shards"], shard_files):
        dates = [b["latest_action"]["date"] for b in shard["bills"]]
        assert entry["first_action_date"] == min(dates)
        assert entry["last_action_date"] == max(dates)
    assert index["shards"][0]["last_action_date"] >= index["shards"][1]["last_action_date"]


def test_build_bill_shards_shard_files_reuse_manifest_shape():
    bills = [_bill("hr1-119", "2026-05-01")]
    _, shard_files = _common.build_bill_shards(
        119, bills, page_size=500, generated_at="2026-07-23T00:00:00Z", votes_coverage_flag=False
    )
    name, shard = shard_files[0]
    assert name == "congress119_bills_p001.json"
    assert set(shard.keys()) == {"generated_at", "congress", "votes_coverage", "bills"}
    assert shard["congress"] == 119
    assert shard["bills"] == bills


def test_build_bill_shards_empty_congress_no_shards():
    index, shard_files = _common.build_bill_shards(
        119, [], generated_at="2026-07-23T00:00:00Z", votes_coverage_flag=False
    )
    assert index["total_bills"] == 0
    assert index["shards"] == []
    assert shard_files == []


def test_build_bill_shards_rejects_bad_page_size():
    try:
        _common.build_bill_shards(119, [], page_size=0)
    except ValueError:
        pass
    else:
        raise AssertionError("expected ValueError for page_size < 1")


def test_save_bill_shards_writes_index_and_shards(tmp_path, monkeypatch):
    monkeypatch.setattr(_common, "OUTPUT_DIR", tmp_path)
    bills = [_bill(f"hr{i}-119", f"2026-0{i}-01") for i in range(1, 4)]
    index = _common.save_bill_shards(119, bills, page_size=2)

    idx_file = tmp_path / "congress119_bills_index.json"
    assert idx_file.exists()
    written = json.loads(idx_file.read_text(encoding="utf-8"))
    assert written == index
    assert (tmp_path / "congress119_bills_p001.json").exists()
    assert (tmp_path / "congress119_bills_p002.json").exists()
    shard1 = json.loads((tmp_path / "congress119_bills_p001.json").read_text(encoding="utf-8"))
    assert len(shard1["bills"]) == 2


def test_save_bill_shards_prunes_stale_shards(tmp_path, monkeypatch):
    monkeypatch.setattr(_common, "OUTPUT_DIR", tmp_path)
    # First run: 3 bills, page 1 -> 3 shards.
    _common.save_bill_shards(119, [_bill(f"hr{i}-119", "2026-05-01") for i in range(1, 4)], page_size=1)
    assert (tmp_path / "congress119_bills_p003.json").exists()
    # Second run: fewer bills -> the orphaned third shard is pruned.
    _common.save_bill_shards(119, [_bill("hr1-119", "2026-05-01")], page_size=1)
    assert (tmp_path / "congress119_bills_p001.json").exists()
    assert not (tmp_path / "congress119_bills_p002.json").exists()
    assert not (tmp_path / "congress119_bills_p003.json").exists()


def test_save_bill_shards_reads_on_disk_manifest_when_bills_none(tmp_path, monkeypatch):
    monkeypatch.setattr(_common, "OUTPUT_DIR", tmp_path)
    (tmp_path / "congress119_bills.json").write_text(
        json.dumps({"congress": 119, "generated_at": "x", "bills": [_bill("hr1-119", "2026-05-01")]}),
        encoding="utf-8",
    )
    index = _common.save_bill_shards(119)
    assert index["total_bills"] == 1
    assert len(index["shards"]) == 1


def test_rebuild_index_populates_shard_index_path_when_present(tmp_path, monkeypatch):
    monkeypatch.setattr(_common, "OUTPUT_DIR", tmp_path)
    monkeypatch.setattr(_common, "STATE_DIR", tmp_path / "state")
    monkeypatch.setattr(_common, "current_congress", lambda *a, **kw: 119)
    (tmp_path / "congress119_bills.json").write_text(
        json.dumps({"congress": 119, "generated_at": "x", "bills": [_bill("hr1-119", "2026-05-01")]}),
        encoding="utf-8",
    )
    (tmp_path / "congress118_bills.json").write_text(
        json.dumps({"congress": 118, "generated_at": "x", "bills": []}),
        encoding="utf-8",
    )
    _common.save_bill_shards(119)  # shards 119 only
    idx = _common.rebuild_index()
    by_cong = {c["congress"]: c for c in idx["congresses"]}
    assert by_cong[119]["shard_index_path"] == "congress119_bills_index.json"
    assert by_cong[118]["shard_index_path"] is None
