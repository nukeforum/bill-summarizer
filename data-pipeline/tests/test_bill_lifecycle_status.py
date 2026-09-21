"""Tests for the pre-floor lifecycle ``status`` field on a built bill record
(issues #39, #116).

Mirrors the Kotlin shadow's
``pipeline/shared/src/commonTest/.../fetch/BillRecordLifecycleStatusTest.kt``
case for case — same action texts, same expectations — so the two pipelines
cannot silently drift apart on the field that gates the #75 parity week.
"""
from __future__ import annotations

import json

from _common import build_bill_record


class _ActionTextClient:
    """Minimal ``CongressClient`` stand-in: the detail payload is fixed and
    every enrichment endpoint is empty, so the only thing a test varies is the
    summary's ``latestAction.text``."""

    def __init__(self, policy_area=None) -> None:
        self._policy_area = policy_area

    def get(self, path, **params):
        if path.endswith("/summaries"):
            return {"summaries": []}
        if path.endswith("/text"):
            return {"textVersions": []}
        if path.endswith("/subjects"):
            return {"subjects": {"legislativeSubjects": []}}
        return {"bill": {
            "title": "Stub title",
            "introducedDate": "2025-01-01",
            "sponsors": [{"fullName": "Sen. Stub, S. [D-XX]", "party": "D", "state": "XX"}],
            "titles": [],
            "policyArea": self._policy_area,
        }}


def _summary(action_text: str) -> dict:
    return {
        "type": "hr",
        "number": "1",
        "title": "",
        "latestAction": {"text": action_text, "actionDate": "2025-06-01"},
    }


def _record(action_text: str, outcome: str = "enacted", policy_area=None) -> dict:
    return build_bill_record(
        _ActionTextClient(policy_area), 119, _summary(action_text), outcome
    )


# ---------- each lifecycle stage reaches the record ------------------------

def test_status_introduced():
    assert _record("Introduced in House", outcome="enacted")["status"] == "introduced"


def test_status_in_committee():
    record = _record("Referred to the House Committee on Ways and Means.")
    assert record["status"] == "in_committee"


def test_status_reported():
    record = _record("Reported by the Committee on Finance. H. Rept. 119-42.")
    assert record["status"] == "reported"


def test_status_reported_beats_in_committee():
    # A reported action still names the committee; "reported" must win.
    record = _record("Placed on the Union Calendar, Calendar No. 88.")
    assert record["status"] == "reported"


# ---------- precedence: a terminal outcome beats a lifecycle status --------

def test_terminal_outcome_leaves_status_unset():
    # ``classify_bill_status`` owns the precedence: an enacted bill carries no
    # lifecycle status, so the key is omitted entirely.
    record = _record("Became Public Law No: 119-12.")
    assert "status" not in record


def test_terminal_outcome_wins_even_when_lifecycle_text_also_matches():
    # Action text that satisfies BOTH rule sets — "Passed House" is terminal,
    # "referred to the Committee" is a lifecycle match. The outcome wins.
    record = _record(
        "Passed House. Referred to the Committee on Homeland Security.",
        outcome="passed_house",
    )
    assert "status" not in record


def test_unrecognized_action_text_leaves_status_unset():
    assert "status" not in _record("Some unrecognized action text.")


# ---------- null/omitted shape (issue #116) --------------------------------

def test_omitted_status_leaves_sibling_nulls_explicit():
    # The omit rule is scoped to status/policy_area: every other nullable
    # field still serializes as an explicit null, matching Python's canonical
    # bytes and the Kotlin write serializer.
    record = _record("Became Public Law No: 119-12.")
    assert "status" not in record
    assert "policy_area" not in record
    assert record["short_title"] is None
    assert record["summary_crs"] is None
    assert record["text_url_html"] is None


def test_status_key_position_between_outcome_and_policy_area():
    # Key order is the byte-parity contract with the Kotlin shadow, whose
    # `Bill` declares `status` between `outcome` and `policy_area`.
    record = _record(
        "Referred to the House Committee on Ways and Means.",
        policy_area={"name": "Taxation"},
    )
    keys = list(record)
    assert keys[keys.index("outcome") + 1] == "status"
    assert keys[keys.index("status") + 1] == "policy_area"


def test_omitted_status_does_not_appear_in_serialized_json():
    text = json.dumps(_record("Became Public Law No: 119-12."), indent=2)
    assert '"status"' not in text


def test_present_status_appears_in_serialized_json():
    text = json.dumps(
        _record("Referred to the House Committee on Ways and Means."), indent=2
    )
    assert '"status": "in_committee"' in text
