"""Smoke tests verifying that pure helpers moved into _common.py work."""
from datetime import datetime, timezone

import _common


def test_clean_sponsor_name_strips_senate_suffix():
    assert _common.clean_sponsor_name("Sen. Peters, Gary C. [D-MI]") == "Sen. Peters, Gary C."


def test_clean_sponsor_name_strips_house_suffix_with_district():
    assert _common.clean_sponsor_name("Rep. Smith, Adrian [R-NE-3]") == "Rep. Smith, Adrian"


def test_classify_outcome_enacted_dominates():
    assert _common.classify_outcome("Became Public Law No: 119-12.") == _common.OUTCOME_ENACTED


def test_classify_outcome_passed_house():
    assert (
        _common.classify_outcome("On passage Passed by the House by recorded vote: 217 - 215")
        == _common.OUTCOME_PASSED_HOUSE
    )


def test_classify_outcome_unknown_returns_none():
    assert _common.classify_outcome("Referred to the Subcommittee on Immigration.") is None


def test_current_congress_for_2026():
    cong = _common.current_congress(datetime(2026, 5, 5, tzinfo=timezone.utc))
    assert cong == 119


def test_normalize_party_d_r_i():
    assert _common.normalize_party("Democratic") == "D"
    assert _common.normalize_party("Republican") == "R"
    assert _common.normalize_party("Independent") == "I"


def test_classify_text_format_url_html():
    assert _common._classify_text_format_url(
        "https://example.com/BILLS-119s4465es.htm"
    ) == "html"


def test_output_dir_under_repo_root():
    # Just confirm the constants resolve, not their exact value.
    assert _common.OUTPUT_DIR.name == "data"
    assert _common.STATE_DIR.name == "state"
