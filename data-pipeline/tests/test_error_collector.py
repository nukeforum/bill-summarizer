"""Tests for the ErrorCollector helper used by all four pipeline scripts."""
from __future__ import annotations

import io
import json

from _common import ErrorCollector, ErrorRecord


def test_empty_collector_is_falsy_and_prints_nothing():
    ec = ErrorCollector()
    assert not ec
    assert len(ec) == 0
    assert ec.summary_lines() == []
    buf = io.StringIO()
    ec.print_summary(file=buf)
    assert buf.getvalue() == ""


def test_record_captures_exception_class_and_message():
    ec = ErrorCollector()
    try:
        json.loads("{not json")
    except Exception as exc:
        ec.record("build_bill_record", "hr1234", exc)
    assert len(ec) == 1
    rec = ec.records()[0]
    assert isinstance(rec, ErrorRecord)
    assert rec.kind == "build_bill_record"
    assert rec.identifier == "hr1234"
    assert rec.error_class == "JSONDecodeError"
    assert "Expecting" in rec.message or "delimiter" in rec.message
    assert rec.url is None
    assert rec.params is None


def test_record_optional_url_and_params_preserved():
    ec = ErrorCollector()
    ec.record(
        "hud_get",
        "CA",
        RuntimeError("HUD API 503"),
        url="https://www.huduser.gov/hudapi/public/usps",
        params={"type": 5, "query": "CA"},
    )
    rec = ec.records()[0]
    assert rec.url == "https://www.huduser.gov/hudapi/public/usps"
    assert rec.params == {"type": 5, "query": "CA"}


def test_summary_groups_by_kind_and_error_class():
    """Two RuntimeErrors in different code paths should be separate groups."""
    ec = ErrorCollector()
    ec.record("hud_get", "CA", RuntimeError("503"))
    ec.record("hud_get", "TX", RuntimeError("504"))
    ec.record("member_detail", "A001", RuntimeError("missing field"))
    lines = ec.summary_lines()
    blob = "\n".join(lines)
    assert "hud_get" in blob
    assert "member_detail" in blob
    # The two HUD errors are in the same group; the member error is its own group.
    # Counts should reflect that.
    assert "RuntimeError × 2" in blob, blob
    assert "RuntimeError × 1" in blob, blob


def test_summary_caps_examples_per_group():
    """When a group has >N errors, show only N examples but keep the full count."""
    ec = ErrorCollector()
    for i in range(8):
        ec.record("build_bill_record", f"hr{i}", ValueError(f"bad bill {i}"))
    lines = ec.summary_lines(examples_per_class=5)
    blob = "\n".join(lines)
    assert "ValueError × 8" in blob
    # Five identifiers should appear; the others should not be enumerated.
    enumerated = sum(1 for line in lines if "hr" in line and ":" in line)
    assert enumerated == 5, f"expected 5 enumerated examples, got {enumerated}\n{blob}"
    # An ellipsis-style "…3 more" or "(3 more)" line should appear.
    assert "more" in blob.lower(), blob


def test_summary_includes_url_and_params_when_present():
    ec = ErrorCollector()
    ec.record(
        "hud_get",
        "CA",
        RuntimeError("503"),
        url="https://www.huduser.gov/hudapi/public/usps",
        params={"type": 5, "query": "CA"},
    )
    blob = "\n".join(ec.summary_lines())
    assert "huduser.gov" in blob
    assert "query" in blob
    assert "CA" in blob


def test_print_summary_writes_to_provided_file_with_label():
    ec = ErrorCollector()
    ec.record("build_bill_record", "hr1", ValueError("oops"))
    buf = io.StringIO()
    ec.print_summary(label="Phase 1", file=buf)
    out = buf.getvalue()
    assert "Phase 1" in out
    assert "ValueError" in out
    assert "hr1" in out


def test_summary_total_count_present():
    ec = ErrorCollector()
    ec.record("a", "1", ValueError("x"))
    ec.record("b", "2", RuntimeError("y"))
    blob = "\n".join(ec.summary_lines())
    # Exact format intentionally loose; just assert the total appears.
    assert "2" in blob
    assert "error" in blob.lower()
