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


# ---------- outcome_from_vote / outcome_from_votes ------------------------


def test_outcome_from_vote_house_passage():
    # Real values from house_vote_119_1_roll017.xml (passage of H.R. 30).
    assert (
        _common.outcome_from_vote("house", "On Passage", "Passed")
        == _common.OUTCOME_PASSED_HOUSE
    )


def test_outcome_from_vote_senate_passage():
    # Real values from senate_vote_119_1_00618.xml (passage of H.R. 5371).
    assert (
        _common.outcome_from_vote("senate", "On Passage of the Bill", "Bill Passed")
        == _common.OUTCOME_PASSED_SENATE
    )


def test_outcome_from_vote_failed_passage():
    assert (
        _common.outcome_from_vote("house", "On Passage", "Failed")
        == _common.OUTCOME_FAILED
    )
    assert (
        _common.outcome_from_vote("senate", "On Passage of the Bill", "Bill Defeated")
        == _common.OUTCOME_FAILED
    )


def test_outcome_from_vote_amendment_rejection_is_not_a_bill_outcome():
    # The correctness fix #30 targets: a rejected *amendment* must NOT be read
    # as a failed bill the way the latest-action substring rules can.
    assert _common.classify_outcome("Amendment SA 2411 rejected") == _common.OUTCOME_FAILED
    assert _common.outcome_from_vote("senate", "On the Amendment", "Rejected") is None


def test_outcome_from_vote_procedural_motions_are_not_bill_outcomes():
    assert _common.outcome_from_vote("senate", "On the Cloture Motion", "Agreed to") is None
    assert _common.outcome_from_vote("house", "On Motion to Table", "Agreed to") is None
    assert _common.outcome_from_vote("senate", "On the Motion to Proceed", "Agreed to") is None


def test_outcome_from_vote_suspension_passage():
    assert (
        _common.outcome_from_vote(
            "house", "On Motion to Suspend the Rules and Pass, as Amended", "Passed"
        )
        == _common.OUTCOME_PASSED_HOUSE
    )


def test_outcome_from_votes_empty_returns_none():
    assert _common.outcome_from_votes([]) is None


def test_outcome_from_votes_ignores_amendment_votes():
    votes = [
        {"chamber": "senate", "question": "On the Amendment", "result": "Rejected",
         "date": "2025-03-01", "roll_number": 10},
    ]
    assert _common.outcome_from_votes(votes) is None


def test_outcome_from_votes_latest_decisive_vote_wins():
    # House passage, then Senate passage a day later: the Senate result wins.
    votes = [
        {"chamber": "house", "question": "On Passage", "result": "Passed",
         "date": "2025-03-01", "roll_number": 5},
        {"chamber": "senate", "question": "On the Amendment", "result": "Agreed to",
         "date": "2025-03-05", "roll_number": 90},
        {"chamber": "senate", "question": "On Passage of the Bill", "result": "Bill Passed",
         "date": "2025-03-02", "roll_number": 88},
    ]
    assert _common.outcome_from_votes(votes) == _common.OUTCOME_PASSED_SENATE


# ---------- reconcile_vote_outcomes --------------------------------------


def test_reconcile_overrides_misclassified_text_outcome():
    # #30 headline: an amendment rejection made the text classifier read the
    # bill as failed, but its passage roll call shows it passed the Senate.
    bills = [
        {
            "id": "hr5371-119",
            "outcome": _common.OUTCOME_FAILED,
            "votes": [
                {"chamber": "senate", "question": "On the Amendment SA 2411",
                 "result": "Rejected", "date": "2025-11-09", "roll_number": 610},
                {"chamber": "senate", "question": "On Passage of the Bill",
                 "result": "Bill Passed", "date": "2025-11-10", "roll_number": 618},
            ],
        }
    ]
    assert _common.reconcile_vote_outcomes(bills) == 1
    assert bills[0]["outcome"] == _common.OUTCOME_PASSED_SENATE


def test_reconcile_leaves_matching_outcome_untouched():
    bills = [
        {
            "id": "hr30-119",
            "outcome": _common.OUTCOME_PASSED_HOUSE,
            "votes": [
                {"chamber": "house", "question": "On Passage", "result": "Passed",
                 "date": "2025-02-01", "roll_number": 17},
            ],
        }
    ]
    assert _common.reconcile_vote_outcomes(bills) == 0
    assert bills[0]["outcome"] == _common.OUTCOME_PASSED_HOUSE


def test_reconcile_keeps_text_outcome_when_no_passage_vote():
    # Voice-vote / amendment-only bills have no decisive roll call, so the
    # text-derived outcome stands.
    bills = [
        {
            "id": "s42-119",
            "outcome": _common.OUTCOME_FAILED,
            "votes": [
                {"chamber": "senate", "question": "On the Cloture Motion",
                 "result": "Rejected", "date": "2025-03-01", "roll_number": 40},
            ],
        },
        {"id": "hr99-119", "outcome": _common.OUTCOME_PASSED_HOUSE, "votes": []},
    ]
    assert _common.reconcile_vote_outcomes(bills) == 0
    assert bills[0]["outcome"] == _common.OUTCOME_FAILED
    assert bills[1]["outcome"] == _common.OUTCOME_PASSED_HOUSE


def test_reconcile_fills_absent_outcome_from_passage_vote():
    bills = [{"id": "hr30-119", "outcome": None, "votes": [
        {"chamber": "house", "question": "On Passage", "result": "Passed",
         "date": "2025-02-01", "roll_number": 17},
    ]}]
    assert _common.reconcile_vote_outcomes(bills) == 1
    assert bills[0]["outcome"] == _common.OUTCOME_PASSED_HOUSE


# ---------- next_election_year_from_terms (#32) ----------------------------


def _house_term(start, end):
    return {"chamber": "House of Representatives", "startYear": start, "endYear": end}


def _senate_term(start, end):
    return {"chamber": "Senate", "startYear": start, "endYear": end}


def test_next_election_year_house_is_end_minus_one():
    # A House term is 2 years; the general is the even year before expiry.
    terms = [_house_term(2023, 2025), _house_term(2025, 2027)]
    assert _common.next_election_year_from_terms(terms, "house") == 2026


def test_next_election_year_senate_is_end_minus_one():
    # A Senate term is 6 years; endYear 2031 → general in 2030.
    terms = [_senate_term(2025, 2031)]
    assert _common.next_election_year_from_terms(terms, "senate") == 2030


def test_next_election_year_coerces_string_years():
    terms = [_senate_term("2019", "2025")]
    assert _common.next_election_year_from_terms(terms, "senate") == 2024


def test_next_election_year_omits_on_no_terms_or_unknown_chamber():
    assert _common.next_election_year_from_terms(None, "house") is None
    assert _common.next_election_year_from_terms([], "senate") is None
    assert _common.next_election_year_from_terms([_house_term(2025, 2027)], "unknown") is None


def test_next_election_year_omits_on_missing_year_fields():
    assert _common.next_election_year_from_terms([{"chamber": "Senate"}], "senate") is None
    assert _common.next_election_year_from_terms([_senate_term(2025, None)], "senate") is None


def test_next_election_year_omits_appointed_senator_partial_term():
    # An appointed senator filling a partial term shows a non-6-year span,
    # so we omit rather than guess a wrong ballot year.
    terms = [_senate_term(2023, 2027)]  # 4-year span, not 6
    assert _common.next_election_year_from_terms(terms, "senate") is None


def test_next_election_year_omits_special_election_house_partial_term():
    terms = [_house_term(2024, 2027)]  # 3-year span, not 2
    assert _common.next_election_year_from_terms(terms, "house") is None


def test_next_election_year_omits_when_derived_year_is_odd():
    # An even endYear would derive an odd (non-general) year — anomalous data.
    terms = [_house_term(2024, 2026)]
    assert _common.next_election_year_from_terms(terms, "house") is None


def test_parse_member_summary_includes_next_election_year_for_house():
    raw = {
        "bioguideId": "H001234",
        "directOrderName": "Rep. Doe, Jane",
        "partyName": "Democratic",
        "state": "California",
        "district": 5,
        "terms": [_house_term(2025, 2027)],
    }
    record = _common.parse_member_summary(raw)
    assert record["next_election_year"] == 2026


def test_parse_member_summary_omits_next_election_year_key_when_ambiguous():
    raw = {
        "bioguideId": "S009999",
        "directOrderName": "Sen. Roe, Sam",
        "partyName": "Republican",
        "state": "Texas",
        "terms": [_senate_term(2023, 2027)],  # appointed / partial term
    }
    record = _common.parse_member_summary(raw)
    assert "next_election_year" not in record
