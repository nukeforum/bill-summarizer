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
from _votes import house_vote_source_url, parse_house_vote, parse_senate_vote

FIXTURES = Path(__file__).parent / "fixtures"

DETAIL_618 = (FIXTURES / "senate_vote_119_1_00618.xml").read_text(encoding="utf-8")
LIS_MAP = json.loads((FIXTURES / "lis_to_bioguide.json").read_text(encoding="utf-8"))
HOUSE_ROLL17 = (FIXTURES / "house_vote_119_1_roll017.xml").read_text(encoding="utf-8")
HOUSE_QUORUM = (FIXTURES / "house_vote_119_1_roll001_quorum.xml").read_text(encoding="utf-8")
HOUSE_SPEAKER = (FIXTURES / "house_vote_119_1_roll002_speaker.xml").read_text(encoding="utf-8")


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
    out = capsys.readouterr().out
    assert "OK: 1 new (1 Senate, 0 House), 0 already on disk, 0 unsupported, 0 failed" in out


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
    assert "OK: 1 new (1 Senate, 0 House), 0 already on disk, 0 unsupported, 1 failed" in captured.out
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


# ---------- House probe loop ------------------------------------------------

# The Senate side of these tests is a menu with zero votes, so all activity
# comes from the House probe. Unmapped House URLs 404, ending each session's
# probe (clerk.house.gov's behavior past the newest published roll).

EMPTY_MENU = _menu_xml(119, 1, [])


def _seed_house_vote(env: Path, congress: int, session: int, roll: int) -> None:
    """Write a minimal-but-valid vote file so the probe skips this roll."""
    vote = {
        "id": f"house-{congress}-{session}-{roll}",
        "congress": congress,
        "chamber": "house",
        "session": session,
        "roll_number": roll,
        "date": "2025-01-03",
        "question": "On Passage",
        "result": "Passed",
        "bill_id": None,
        "totals": {"yea": 0, "nay": 0, "present": 0, "not_voting": 0},
        "positions": [],
    }
    path = env / "votes" / f"congress{congress}" / f"house-{session}-{roll}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(vote), encoding="utf-8")


def test_house_fetches_past_existing_rolls_and_stops_at_404(env, monkeypatch, capsys):
    for roll in range(1, 17):
        _seed_house_vote(env, 119, 1, roll)
    fake = _fake(
        monkeypatch,
        {MENU_1_URL: EMPTY_MENU, house_vote_source_url(119, 1, 17): HOUSE_ROLL17},
    )

    assert fetch_votes.main(["--congress", "119"]) == 0

    saved = json.loads(
        (env / "votes" / "congress119" / "house-1-17.json").read_text(encoding="utf-8")
    )
    assert saved == parse_house_vote(HOUSE_ROLL17)
    # Existing rolls cost no fetches; the probe stopped at the first 404
    # (roll 18) and never looked further.
    assert house_vote_source_url(119, 1, 16) not in fake.requested
    assert house_vote_source_url(119, 1, 18) in fake.requested
    assert house_vote_source_url(119, 1, 19) not in fake.requested
    # Session 2 was probed and found unpublished (single 404).
    assert house_vote_source_url(119, 2, 1) in fake.requested
    assert house_vote_source_url(119, 2, 2) not in fake.requested

    out = capsys.readouterr().out
    assert "OK: 1 new (0 Senate, 1 House), 16 already on disk, 0 unsupported, 0 failed" in out
    index = json.loads((env / "congress119_votes.json").read_text(encoding="utf-8"))
    assert index["vote_count"] == 17
    ref = next(r for r in index["votes"] if r["id"] == "house-119-1-17")
    assert ref["bill_id"] == "hr30-119"
    assert ref["path"] == "votes/congress119/house-1-17.json"


def test_house_speaker_election_is_skipped_not_errored(env, monkeypatch, capsys):
    responses = {
        MENU_1_URL: EMPTY_MENU,
        house_vote_source_url(119, 1, 1): HOUSE_QUORUM,
        house_vote_source_url(119, 1, 2): HOUSE_SPEAKER,
    }
    fake = _fake(monkeypatch, responses)
    assert fetch_votes.main(["--congress", "119"]) == 0

    assert (env / "votes" / "congress119" / "house-1-1.json").exists()
    assert not (env / "votes" / "congress119" / "house-1-2.json").exists()
    # The probe continued past the unsupported vote to roll 3 (404 -> stop).
    assert house_vote_source_url(119, 1, 3) in fake.requested
    out = capsys.readouterr().out
    assert "OK: 1 new (0 Senate, 1 House), 0 already on disk, 1 unsupported, 0 failed" in out

    # Next run: roll 1 is on disk (skipped), the Speaker election is
    # re-probed and re-skipped — cheap, and keeps disk as the only cursor.
    second = _fake(monkeypatch, responses)
    assert fetch_votes.main(["--congress", "119"]) == 0
    assert house_vote_source_url(119, 1, 1) not in second.requested
    assert house_vote_source_url(119, 1, 2) in second.requested


def test_house_consecutive_parse_failures_stop_probe(env, monkeypatch, capsys):
    fake = _fake(
        monkeypatch,
        {
            MENU_1_URL: EMPTY_MENU,
            house_vote_source_url(119, 1, 1): HOUSE_QUORUM,
            house_vote_source_url(119, 1, 2): "<not-a-vote/>",
            house_vote_source_url(119, 1, 3): "<not-a-vote/>",
            house_vote_source_url(119, 1, 4): "<not-a-vote/>",
            house_vote_source_url(119, 1, 5): HOUSE_QUORUM,
        },
    )
    assert fetch_votes.main(["--congress", "119"]) == 0
    # Three consecutive failures capped the session probe before roll 5.
    assert house_vote_source_url(119, 1, 5) not in fake.requested
    captured = capsys.readouterr()
    assert "3 consecutive House parse failures" in captured.out
    assert "OK: 1 new (0 Senate, 1 House), 0 already on disk, 0 unsupported, 3 failed" in captured.out


def test_house_identity_mismatch_stops_probe(env, monkeypatch, capsys):
    # Roll 1's URL serves XML identifying itself as roll 17: the year/session
    # mapping must be wrong, so nothing is published and the probe stops.
    fake = _fake(monkeypatch, {MENU_1_URL: EMPTY_MENU, house_vote_source_url(119, 1, 1): HOUSE_ROLL17})
    assert fetch_votes.main(["--congress", "119"]) == 0
    votes_dir = env / "votes" / "congress119"
    assert not votes_dir.exists() or not list(votes_dir.glob("house-*.json"))
    assert house_vote_source_url(119, 1, 2) not in fake.requested
    captured = capsys.readouterr()
    assert "house_vote / ValueError" in captured.err
    assert "identifies itself as house-119-1-17" in captured.err


def test_house_non_404_error_recorded_and_stops_probe(env, monkeypatch, capsys):
    fake = _fake(
        monkeypatch,
        {MENU_1_URL: EMPTY_MENU, house_vote_source_url(119, 1, 1): _http_error(500)},
    )
    assert fetch_votes.main(["--congress", "119"]) == 0
    assert house_vote_source_url(119, 1, 2) not in fake.requested
    assert "house_vote / HTTPError" in capsys.readouterr().err


def test_house_deferred_when_senate_exhausts_budget(env, monkeypatch, capsys):
    fake = _fake(
        monkeypatch,
        {
            MENU_1_URL: _menu_xml(119, 1, [618]),
            DETAIL_618_URL: DETAIL_618,
            house_vote_source_url(119, 1, 1): HOUSE_QUORUM,
        },
    )
    assert fetch_votes.main(["--congress", "119", "--max-new", "1"]) == 0
    assert house_vote_source_url(119, 1, 1) not in fake.requested
    assert "House probe deferred to next run" in capsys.readouterr().out


def test_build_lis_to_bioguide_unions_current_over_historical(monkeypatch):
    historical = "- id: {lis: S001, bioguide: OLD00001}\n- id: {lis: S002, bioguide: G000001}\n"
    current = "- id: {lis: S001, bioguide: NEW00001}\n"
    texts = {
        fetch_votes.LEGISLATORS_HISTORICAL_YAML_URL: historical,
        fetch_votes.LEGISLATORS_CURRENT_YAML_URL: current,
    }
    monkeypatch.setattr(fetch_votes, "_fetch", lambda url: texts[url])
    assert fetch_votes.build_lis_to_bioguide() == {"S001": "NEW00001", "S002": "G000001"}


# ------------------------------------------------------------ bill linkage


def _seed_bills_manifest(env: Path, *bill_ids: str) -> Path:
    path = env / "congress119_bills.json"
    path.write_text(
        json.dumps({
            "generated_at": "2026-01-01T00:00:00Z",
            "congress": 119,
            "bills": [{"id": bid, "title": "A bill"} for bid in bill_ids],
        }),
        encoding="utf-8",
    )
    return path


def test_main_attaches_vote_refs_to_bills_manifest(env, monkeypatch, capsys):
    _seed_bills_manifest(env, "hr5371-119", "s42-119")
    _fake(monkeypatch, {MENU_1_URL: _menu_xml(119, 1, [618]), DETAIL_618_URL: DETAIL_618})

    assert fetch_votes.main(["--congress", "119"]) == 0
    assert "vote refs refreshed" in capsys.readouterr().out

    manifest = json.loads((env / "congress119_bills.json").read_text(encoding="utf-8"))
    index = json.loads((env / "congress119_votes.json").read_text(encoding="utf-8"))
    bills = {b["id"]: b for b in manifest["bills"]}
    # The linked bill carries the index ref verbatim; the other gets [].
    assert bills["hr5371-119"]["votes"] == index["votes"]
    assert bills["s42-119"]["votes"] == []
    assert manifest["generated_at"] != "2026-01-01T00:00:00Z"


def test_bills_manifest_untouched_when_vote_refs_unchanged(env, monkeypatch, capsys):
    manifest_path = _seed_bills_manifest(env, "hr5371-119")
    responses = {MENU_1_URL: _menu_xml(119, 1, [618]), DETAIL_618_URL: DETAIL_618}
    _fake(monkeypatch, responses)
    assert fetch_votes.main(["--congress", "119"]) == 0
    enriched = manifest_path.read_bytes()
    capsys.readouterr()

    # No new votes on the second run: the manifest must not churn (its
    # generated_at would otherwise bump on every nightly votes run).
    _fake(monkeypatch, responses)
    assert fetch_votes.main(["--congress", "119"]) == 0
    assert "vote refs refreshed" not in capsys.readouterr().out
    assert manifest_path.read_bytes() == enriched
