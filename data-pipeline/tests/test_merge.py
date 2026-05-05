"""Tests for merge_records — the heart of the append-instead-of-replace change."""
import _common


def _bill(bid: str, date: str, title: str = "Some bill") -> dict:
    """Minimal bill record shape for merge tests."""
    return {
        "id": bid,
        "title": title,
        "latest_action": {"date": date, "text": "..."},
    }


def test_empty_existing_all_added():
    incoming = [_bill("hr1-119", "2025-03-01"), _bill("s2-119", "2025-04-01")]
    merged, stats = _common.merge_records([], incoming)
    assert stats.added == 2
    assert stats.updated == 0
    assert stats.unchanged == 0
    assert {r["id"] for r in merged} == {"hr1-119", "s2-119"}


def test_empty_incoming_existing_preserved():
    existing = [_bill("hr1-119", "2025-03-01")]
    merged, stats = _common.merge_records(existing, [])
    assert stats.added == 0
    assert stats.updated == 0
    assert stats.unchanged == 0
    assert merged == existing


def test_overlap_with_changes_counts_as_updated():
    existing = [_bill("hr1-119", "2025-03-01", title="Original")]
    incoming = [_bill("hr1-119", "2025-04-01", title="Updated")]
    merged, stats = _common.merge_records(existing, incoming)
    assert stats.updated == 1
    assert stats.added == 0
    assert stats.unchanged == 0
    assert len(merged) == 1
    assert merged[0]["title"] == "Updated"
    assert merged[0]["latest_action"]["date"] == "2025-04-01"


def test_overlap_identical_counts_as_unchanged():
    rec = _bill("hr1-119", "2025-03-01")
    merged, stats = _common.merge_records([rec], [dict(rec)])
    assert stats.unchanged == 1
    assert stats.updated == 0
    assert stats.added == 0
    assert merged == [rec]


def test_mixed_added_updated_preserved():
    existing = [
        _bill("hr1-119", "2025-03-01", title="Old A"),
        _bill("hr2-119", "2025-02-01", title="Old B (untouched)"),
    ]
    incoming = [
        _bill("hr1-119", "2025-05-01", title="New A"),
        _bill("hr3-119", "2025-04-15", title="Brand new C"),
    ]
    merged, stats = _common.merge_records(existing, incoming)
    assert stats.added == 1
    assert stats.updated == 1
    assert stats.unchanged == 0
    by_id = {r["id"]: r for r in merged}
    assert by_id["hr1-119"]["title"] == "New A"
    assert by_id["hr2-119"]["title"] == "Old B (untouched)"
    assert by_id["hr3-119"]["title"] == "Brand new C"


def test_merge_sorts_by_latest_action_date_desc():
    existing = [_bill("hr2-119", "2025-02-01")]
    incoming = [
        _bill("hr3-119", "2025-04-15"),
        _bill("hr1-119", "2025-03-01"),
    ]
    merged, _ = _common.merge_records(existing, incoming)
    dates = [r["latest_action"]["date"] for r in merged]
    assert dates == sorted(dates, reverse=True)
    assert dates[0] == "2025-04-15"
