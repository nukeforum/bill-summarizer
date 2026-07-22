"""Integration tests for fetch_votes.main with mocked senate.gov fetches.

The detail XML is the real fixture for roll call 119-1-618; the vote menu is
built inline so tests control exactly which roll calls the driver sees.
"""
from __future__ import annotations

import json
from pathlib import Path

import pytest
import requests

import _common
import fetch_votes
from _votes import parse_senate_vote

FIXTURES = Path(__file__).parent / "fixtures"

DETAIL_618 = (FIXTURES / "senate_vote_119_1_00618.xml").read_text(encoding="utf-8")
LIS_MAP = json.loads((FIXTURES / "lis_to_bioguide.json").read_text(encoding="utf-8"))


def _menu_xml(congress: int, session: int, vote_numbers: list[int]) -> str:
    votes = "".join(
        f"<vote><vote_number>{n:05d}</vote_number></vote>" for n in vote_numbers
    )
    return (
        "<vote_summary>"
        f"<congress>{congress}</congress><session>{session}</session>"
        f"<votes>{votes}</votes></vote_summary>"
    )


def _http_error(status_code: int) -> requests.HTTPError:
    response = requests.Response()
    response.status_code = status_code
    return requests.HTTPError(response=response)


class _FakeFetch:
    """URL -> response text; a ``requests.HTTPError`` value is raised instead.

    Unmapped URLs 404 (senate.gov's behavior for unpublished documents).
    Records every requested URL so tests can assert on fetch counts.
    """

    def __init__(self, responses: dict[str, str | requests.HTTPError]):
        self.responses = responses
        self.requested: list[str] = []

    def __call__(self, url: str) -> str:
        self.requested.append(url)
        result = self.responses.get(url)
        if result is None:
            raise _http_error(404)
        if isinstance(result, requests.HTTPError):
            raise result
        return result


@pytest.fixture
def env(tmp_path, monkeypatch):
    monkeypatch.setattr(_common, "OUTPUT_DIR", tmp_path)
    monkeypatch.setattr(fetch_votes, "build_lis_to_bioguide", lambda: dict(LIS_MAP))
    return tmp_path


MENU_1_URL = fetch_votes.senate_vote_menu_url(119, 1)
DETAIL_618_URL = fetch_votes.senate_vote_source_url(119, 1, 618)


def _fake(monkeypatch, responses) -> _FakeFetch:
    fake = _FakeFetch(responses)
    monkeypatch.setattr(fetch_votes, "_fetch", fake)
    return fake


def test_main_writes_vote_file_and_index(env, monkeypatch, capsys):
    _fake(monkeypatch, {MENU_1_URL: _menu_xml(119, 1, [618]), DETAIL_618_URL: DETAIL_618})

    assert fetch_votes.main(["--congress", "119"]) == 0

    vote_file = env / "votes" / "congress119" / "senate-1-618.json"
    saved = json.loads(vote_file.read_text(encoding="utf-8"))
    assert saved == parse_senate_vote(DETAIL_618, LIS_MAP)

    index = json.loads((env / "congress119_votes.json").read_text(encoding="utf-8"))
    assert index["congress"] == 119
    assert index["vote_count"] == 1
    ref = index["votes"][0]
    assert ref["id"] == "senate-119-1-618"
    assert ref["bill_id"] == "hr5371-119"
    assert ref["path"] == "votes/congress119/senate-1-618.json"
    assert "positions" not in ref
    assert "OK: 1 new, 0 already on disk, 0 failed" in capsys.readouterr().out


def test_second_run_skips_existing_votes(env, monkeypatch):
    responses = {MENU_1_URL: _menu_xml(119, 1, [618]), DETAIL_618_URL: DETAIL_618}
    first = _fake(monkeypatch, responses)
    assert fetch_votes.main(["--congress", "119"]) == 0
    assert first.requested.count(DETAIL_618_URL) == 1

    second = _fake(monkeypatch, responses)
    assert fetch_votes.main(["--congress", "119"]) == 0
    assert DETAIL_618_URL not in second.requested  # menu only, no detail refetch

    index = json.loads((env / "congress119_votes.json").read_text(encoding="utf-8"))
    assert index["vote_count"] == 1


def test_failed_vote_is_recorded_and_retried_next_run(env, monkeypatch, capsys):
    # Roll 619 is on the menu but its detail XML isn't published yet (404).
    menu = _menu_xml(119, 1, [618, 619])
    _fake(monkeypatch, {MENU_1_URL: menu, DETAIL_618_URL: DETAIL_618})

    assert fetch_votes.main(["--congress", "119"]) == 0
    captured = capsys.readouterr()
    assert "OK: 1 new, 0 already on disk, 1 failed" in captured.out
    assert "senate_vote / HTTPError" in captured.err
    assert not (env / "votes" / "congress119" / "senate-1-619.json").exists()

    # Index only carries what's actually on disk.
    index = json.loads((env / "congress119_votes.json").read_text(encoding="utf-8"))
    assert [ref["id"] for ref in index["votes"]] == ["senate-119-1-618"]

    # Next run retries the missing vote (618 skipped, 619 attempted again).
    retry = _fake(
        monkeypatch,
        {
            MENU_1_URL: menu,
            DETAIL_618_URL: DETAIL_618,
            fetch_votes.senate_vote_source_url(119, 1, 619): DETAIL_618,
        },
    )
    assert fetch_votes.main(["--congress", "119"]) == 0
    assert fetch_votes.senate_vote_source_url(119, 1, 619) in retry.requested
    # ...but 619's detail XML identifies itself as roll 618, so it is
    # rejected rather than published under the wrong id.
    assert not (env / "votes" / "congress119" / "senate-1-619.json").exists()


def test_missing_session_menu_is_tolerated(env, monkeypatch):
    # Session 2 menu 404s (unmapped): only session 1 is processed.
    _fake(monkeypatch, {MENU_1_URL: _menu_xml(119, 1, [618]), DETAIL_618_URL: DETAIL_618})
    assert fetch_votes.main(["--congress", "119"]) == 0


def test_no_menus_at_all_is_fatal(env, monkeypatch, capsys):
    _fake(monkeypatch, {})
    assert fetch_votes.main(["--congress", "119"]) == 1
    assert "no Senate vote menu available" in capsys.readouterr().err


def test_non_404_menu_error_is_fatal(env, monkeypatch):
    _fake(monkeypatch, {MENU_1_URL: _http_error(500)})
    assert fetch_votes.main(["--congress", "119"]) == 1


def test_max_new_caps_fetches_and_defers_rest(env, monkeypatch, capsys):
    _fake(monkeypatch, {MENU_1_URL: _menu_xml(119, 1, [618, 619]), DETAIL_618_URL: DETAIL_618})
    assert fetch_votes.main(["--congress", "119", "--max-new", "1"]) == 0
    assert "deferred to next run" in capsys.readouterr().out
    # Exactly one vote landed; the deferred one was never attempted, so it
    # is not an error.
    index = json.loads((env / "congress119_votes.json").read_text(encoding="utf-8"))
    assert index["vote_count"] == 1


def test_menu_congress_mismatch_is_fatal(env, monkeypatch, capsys):
    _fake(monkeypatch, {MENU_1_URL: _menu_xml(118, 1, [618])})
    assert fetch_votes.main(["--congress", "119"]) == 1
    assert "identifies itself as congress 118" in capsys.readouterr().err


def test_build_lis_to_bioguide_unions_current_over_historical(monkeypatch):
    historical = "- id: {lis: S001, bioguide: OLD00001}\n- id: {lis: S002, bioguide: G000001}\n"
    current = "- id: {lis: S001, bioguide: NEW00001}\n"
    texts = {
        fetch_votes.LEGISLATORS_HISTORICAL_YAML_URL: historical,
        fetch_votes.LEGISLATORS_CURRENT_YAML_URL: current,
    }
    monkeypatch.setattr(fetch_votes, "_fetch", lambda url: texts[url])
    assert fetch_votes.build_lis_to_bioguide() == {"S001": "NEW00001", "S002": "G000001"}
