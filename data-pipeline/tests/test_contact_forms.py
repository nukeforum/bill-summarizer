"""Tests for the unitedstates/congress-legislators contact-form parser."""
from __future__ import annotations

import textwrap

import _common


def test_parse_extracts_contact_form_from_last_term():
    yaml_text = textwrap.dedent("""
        - id:
            bioguide: O000174
          terms:
            - type: sen
              start: "2021-01-20"
              contact_form: https://example.com/old-form
            - type: sen
              start: "2027-01-03"
              contact_form: https://www.ossoff.senate.gov/contact-us/
        - id:
            bioguide: S001234
          terms:
            - type: rep
              contact_form: https://smith.house.gov/contact
    """)
    out = _common.parse_contact_forms_yaml(yaml_text)
    assert out == {
        "O000174": "https://www.ossoff.senate.gov/contact-us/",
        "S001234": "https://smith.house.gov/contact",
    }


def test_parse_skips_entries_with_no_contact_form():
    yaml_text = textwrap.dedent("""
        - id:
            bioguide: A000001
          terms:
            - type: rep
              start: "2023-01-03"
        - id:
            bioguide: B000002
          terms:
            - type: rep
              contact_form: https://b.house.gov/contact
    """)
    out = _common.parse_contact_forms_yaml(yaml_text)
    assert out == {"B000002": "https://b.house.gov/contact"}


def test_parse_skips_entries_with_no_bioguide_id():
    yaml_text = textwrap.dedent("""
        - id:
            govtrack: 99999
          terms:
            - type: rep
              contact_form: https://nobody.house.gov/contact
        - id:
            bioguide: C000003
          terms:
            - type: rep
              contact_form: https://c.house.gov/contact
    """)
    out = _common.parse_contact_forms_yaml(yaml_text)
    assert out == {"C000003": "https://c.house.gov/contact"}


def test_parse_handles_empty_input():
    assert _common.parse_contact_forms_yaml("[]") == {}


def test_parse_falls_back_through_terms_when_last_lacks_contact_form():
    """A few entries carry contact_form on an earlier term only; accept the
    most recent term that has one rather than dropping the legislator."""
    yaml_text = textwrap.dedent("""
        - id:
            bioguide: D000004
          terms:
            - type: rep
              start: "2019-01-03"
              contact_form: https://d.house.gov/contact
            - type: sen
              start: "2025-01-03"
    """)
    out = _common.parse_contact_forms_yaml(yaml_text)
    assert out == {"D000004": "https://d.house.gov/contact"}


from unittest.mock import patch

from _common import fetch_contact_forms_index, CONTACT_FORMS_YAML_URL


def test_fetch_contact_forms_index_uses_default_url_and_parses_response():
    sample_yaml = (
        "- id:\n"
        "    bioguide: E000005\n"
        "  terms:\n"
        "    - type: rep\n"
        "      contact_form: https://e.house.gov/contact\n"
    )

    class _Resp:
        text = sample_yaml
        def raise_for_status(self) -> None: return None

    with patch("_common.requests.get", return_value=_Resp()) as mock_get:
        out = fetch_contact_forms_index()
    mock_get.assert_called_once_with(CONTACT_FORMS_YAML_URL, timeout=30)
    assert out == {"E000005": "https://e.house.gov/contact"}


def test_fetch_contact_forms_index_propagates_http_errors():
    import requests as _requests

    class _Resp:
        text = ""
        def raise_for_status(self) -> None:
            raise _requests.HTTPError("500 Server Error")

    with patch("_common.requests.get", return_value=_Resp()):
        import pytest as _pytest
        with _pytest.raises(_requests.HTTPError):
            fetch_contact_forms_index()
