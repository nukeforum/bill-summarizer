"""Tests for the HUD ZIP→CD crosswalk asset builder."""
import json
from pathlib import Path

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
