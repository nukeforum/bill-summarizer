"""Tests for legislative-subject fetching (issue #28).

Covers ``_fetch_subjects`` (the ``/subjects`` endpoint reader) and its
inclusion in ``build_bill_record``. Subjects are the finer-grained
multi-value terms distinct from the single-value ``policy_area``.
"""
from __future__ import annotations

from typing import Any

from _common import _fetch_subjects, build_bill_record


class _StubClient:
    """Returns canned endpoint bodies keyed by path suffix.

    ``subjects_body`` is whatever the ``/subjects`` endpoint should return;
    the detail/summaries/text endpoints return empty-but-valid shapes.
    """

    def __init__(self, subjects_body: dict[str, Any]) -> None:
        self._subjects_body = subjects_body
        self.calls: list[str] = []

    def get(self, path: str, **params: Any) -> dict[str, Any]:
        self.calls.append(path)
        if path.endswith("/subjects"):
            return self._subjects_body
        if path.endswith("/summaries"):
            return {"summaries": []}
        if path.endswith("/text"):
            return {"textVersions": []}
        return {"bill": {"title": "Stub", "introducedDate": "2025-01-01", "sponsors": []}}


def test_fetch_subjects_extracts_legislative_subject_names():
    body = {
        "subjects": {
            "legislativeSubjects": [
                {"name": "Firearms and explosives"},
                {"name": "Crime and law enforcement"},
            ],
            "policyArea": {"name": "Crime and Law Enforcement"},
        }
    }
    client = _StubClient(body)
    assert _fetch_subjects(client, 119, "hr", "1") == [
        "Crime and law enforcement",
        "Firearms and explosives",
    ]


def test_fetch_subjects_sorts_and_dedupes_for_deterministic_order():
    body = {
        "subjects": {
            "legislativeSubjects": [
                {"name": "Zebras"},
                {"name": "Aardvarks"},
                {"name": "Zebras"},
            ]
        }
    }
    client = _StubClient(body)
    assert _fetch_subjects(client, 119, "hr", "1") == ["Aardvarks", "Zebras"]


def test_fetch_subjects_empty_when_no_legislative_subjects():
    client = _StubClient({"subjects": {"policyArea": {"name": "Health"}}})
    assert _fetch_subjects(client, 119, "hr", "1") == []


def test_fetch_subjects_empty_on_malformed_payload():
    assert _fetch_subjects(_StubClient({}), 119, "hr", "1") == []
    assert _fetch_subjects(_StubClient({"subjects": []}), 119, "hr", "1") == []
    bad_entries = {"subjects": {"legislativeSubjects": ["not-a-dict", {"noname": 1}]}}
    assert _fetch_subjects(_StubClient(bad_entries), 119, "hr", "1") == []


def test_build_bill_record_includes_subjects():
    body = {"subjects": {"legislativeSubjects": [{"name": "Immigration"}]}}
    client = _StubClient(body)
    summary = {"type": "hr", "number": "1", "latestAction": {"actionDate": "2025-01-02"}}
    record = build_bill_record(client, 119, summary, "enacted")
    assert record["subjects"] == ["Immigration"]


def test_build_bill_record_subjects_always_present_even_when_empty():
    # Byte-parity with the KMP writer (encodeDefaults=true emits "subjects":[])
    # requires the key to be present on every record, not omitted when empty.
    client = _StubClient({"subjects": {"legislativeSubjects": []}})
    summary = {"type": "hr", "number": "2", "latestAction": {"actionDate": "2025-01-02"}}
    record = build_bill_record(client, 119, summary, "enacted")
    assert record["subjects"] == []
