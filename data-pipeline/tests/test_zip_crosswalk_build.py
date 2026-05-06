"""Tests for the HUD ZIP→CD crosswalk asset builder."""
import json
from pathlib import Path

import pytest

import build_zip_crosswalk

FIXTURE = Path(__file__).parent / "fixtures" / "hud_zip_cd_sample.csv"


def test_build_emits_grouped_districts(tmp_path):
    out = tmp_path / "zip_to_cd.json"
    build_zip_crosswalk.build(FIXTURE, out)
    data = json.loads(out.read_text())
    assert data["78701"] == {"state": "TX", "districts": [21, 25, 35]}
    assert data["10001"] == {"state": "NY", "districts": [12]}


def test_build_normalizes_at_large_district_zero(tmp_path):
    out = tmp_path / "zip_to_cd.json"
    build_zip_crosswalk.build(FIXTURE, out)
    data = json.loads(out.read_text())
    assert data["99501"] == {"state": "AK", "districts": [0]}


def test_build_normalizes_delegate_district_to_zero(tmp_path):
    out = tmp_path / "zip_to_cd.json"
    build_zip_crosswalk.build(FIXTURE, out)
    data = json.loads(out.read_text())
    assert data["20001"] == {"state": "DC", "districts": [0]}
    assert data["00601"] == {"state": "PR", "districts": [0]}


def test_build_districts_sorted(tmp_path):
    out = tmp_path / "zip_to_cd.json"
    build_zip_crosswalk.build(FIXTURE, out)
    data = json.loads(out.read_text())
    assert data["78701"]["districts"] == sorted(data["78701"]["districts"])


# --- API mode tests --------------------------------------------------------


class _FakeResp:
    def __init__(self, status_code, body):
        self.status_code = status_code
        self._body = body
        self.text = json.dumps(body) if body else ""

    def json(self):
        return self._body


def test_build_from_api_inverts_state_query_to_zip(tmp_path, monkeypatch):
    """API mode: per-state response is reduced into ZIP→{state, [districts]}."""
    fake_responses = {
        "AR": {"data": {"results": [
            {"zip": "72101", "cd": "0501", "res_ratio": 0.9},
            {"zip": "72102", "cd": "0501", "res_ratio": 0.1},
            {"zip": "72102", "cd": "0502", "res_ratio": 0.5},
            {"zip": "72103", "cd": "0502", "res_ratio": 0.5},
        ]}},
        "VT": {"data": {"results": [
            # Vermont at-large: HUD CD code "00" → district 0.
            {"zip": "05001", "cd": "5000", "res_ratio": 1.0},
        ]}},
        "DC": {"data": {"results": [
            # Delegate jurisdiction: HUD CD code "98" → district 0.
            {"zip": "20001", "cd": "1198", "res_ratio": 1.0},
        ]}},
        # Other states absent → 404, treated as miss.
    }

    class _FakeSession:
        def get(self, url, headers=None, params=None, timeout=None):
            state = params["query"]
            body = fake_responses.get(state)
            if body is None:
                return _FakeResp(404, {"error": "not found"})
            return _FakeResp(200, body)

    monkeypatch.setattr(
        build_zip_crosswalk.requests, "Session", lambda: _FakeSession()
    )
    # Limit the iteration to states we actually fixture.
    monkeypatch.setattr(build_zip_crosswalk, "_STATE_QUERIES", ["AR", "VT", "DC"])

    out = tmp_path / "zip_to_cd.json"
    build_zip_crosswalk.build_from_api(
        output_json=out,
        api_key="stub",
        year=2024,
        quarter=4,
    )

    data = json.loads(out.read_text())
    assert data["72101"] == {"state": "AR", "districts": [1]}
    assert data["72102"] == {"state": "AR", "districts": [1, 2]}
    assert data["72103"] == {"state": "AR", "districts": [2]}
    assert data["05001"] == {"state": "VT", "districts": [0]}  # at-large
    assert data["20001"] == {"state": "DC", "districts": [0]}  # delegate ("98" → 0)


def test_build_from_api_no_zips_collected_raises(tmp_path, monkeypatch):
    """If the API returns no usable rows, refuse to write empty asset."""

    class _AlwaysMissSession:
        def get(self, url, headers=None, params=None, timeout=None):
            return _FakeResp(404, {})

    monkeypatch.setattr(
        build_zip_crosswalk.requests, "Session", lambda: _AlwaysMissSession()
    )
    monkeypatch.setattr(build_zip_crosswalk, "_STATE_QUERIES", ["AR"])

    with pytest.raises(RuntimeError, match="empty asset"):
        build_zip_crosswalk.build_from_api(
            output_json=tmp_path / "out.json",
            api_key="stub",
            year=2024,
            quarter=4,
        )


def test_normalize_cd_code():
    assert build_zip_crosswalk._normalize_cd_code("0501") == 1
    assert build_zip_crosswalk._normalize_cd_code("0512") == 12
    assert build_zip_crosswalk._normalize_cd_code("5000") == 0   # Vermont at-large
    assert build_zip_crosswalk._normalize_cd_code("1198") == 0   # DC delegate
    assert build_zip_crosswalk._normalize_cd_code("0000") == 0
    assert build_zip_crosswalk._normalize_cd_code("") == 0


def test_extract_results_handles_alternate_shapes():
    body_data_results = {"data": {"results": [{"zip": "1"}]}}
    body_top_level = {"results": [{"zip": "2"}]}
    assert build_zip_crosswalk._extract_results(body_data_results) == [{"zip": "1"}]
    assert build_zip_crosswalk._extract_results(body_top_level) == [{"zip": "2"}]


def test_extract_zip_handles_key_variants():
    assert build_zip_crosswalk._extract_zip({"zip": "12345"}) == "12345"
    assert build_zip_crosswalk._extract_zip({"ZIP": "12345"}) == "12345"
    assert build_zip_crosswalk._extract_zip({"zipcode": 12345}) == "12345"
    assert build_zip_crosswalk._extract_zip({"foo": "bar"}) is None


def test_extract_cd_value_handles_key_variants():
    assert build_zip_crosswalk._extract_cd_value({"cd": "0501"}) == "0501"
    assert build_zip_crosswalk._extract_cd_value({"geoid": "0501"}) == "0501"
    assert build_zip_crosswalk._extract_cd_value({"foo": "bar"}) is None
