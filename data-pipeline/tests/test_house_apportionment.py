"""Tests for the House-apportionment vacancy audit (#106).

find_vacant_house_districts tells a real currently-vacant seat (no sworn-in
member, correctly omitted by Congress.gov's currentMember=true filter) apart
from an actual fetch/parse defect. It is not itself a "fix" for a vacancy —
a vacant seat is expected to stay absent from members_out until Congress.gov
reports a successor — it is the audit that makes that fact visible instead
of silent.
"""
from _common import HOUSE_APPORTIONMENT, find_vacant_house_districts


def _house_member(state, district, bid="X000001"):
    return {"bioguide_id": bid, "chamber": "house", "state": state, "district": district}


def _senate_member(state, bid="X000002"):
    return {"bioguide_id": bid, "chamber": "senate", "state": state, "district": None}


def test_no_vacancies_when_every_apportioned_seat_is_present():
    assert find_vacant_house_districts(_full_house()) == []


def _full_house(*, omit=frozenset()):
    """Every apportioned seat filled, except (state, district) pairs in omit."""
    return [
        _house_member(state, district, bid=f"{state}{district}")
        for state, count in HOUSE_APPORTIONMENT.items()
        for district in range(1, count + 1)
        if (state, district) not in omit
    ]


def test_reports_a_single_missing_district_ga13():
    """Regression for #106: GA has 14 districts; omitting GA-13 (the
    seat David Scott held until his death) must surface as a vacancy."""
    members = _full_house(omit={("GA", 13)})
    assert find_vacant_house_districts(members) == [("GA", 13)]


def test_reports_multiple_states_sorted_by_state_then_district():
    members = _full_house(omit={("GA", 13), ("CA", 14), ("TX", 23), ("FL", 20)})
    assert find_vacant_house_districts(members) == [
        ("CA", 14), ("FL", 20), ("GA", 13), ("TX", 23),
    ]


def test_ignores_senators_and_at_large_delegate_district_zero():
    members = _full_house()
    members.append(_senate_member("GA"))
    members.append(_house_member("VT", 0, bid="VT0"))  # at-large; district=0, outside the table
    assert find_vacant_house_districts(members) == []


def test_apportionment_table_excludes_at_large_and_delegate_jurisdictions():
    at_large_and_delegates = {"AK", "DE", "ND", "SD", "VT", "WY", "DC", "AS", "GU", "MP", "PR", "VI"}
    assert at_large_and_delegates.isdisjoint(HOUSE_APPORTIONMENT)
    # 50 states minus the 6 at-large ones = 44 multi-district states.
    assert len(HOUSE_APPORTIONMENT) == 44
