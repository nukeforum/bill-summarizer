"""Tests for the unitedstates/congress-legislators contact-info parser + fetcher."""
from __future__ import annotations

import textwrap
from unittest.mock import patch

import pytest
import requests as _requests

from _common import (
    LEGISLATORS_CURRENT_YAML_URL,
    fetch_contact_info_index,
    parse_contact_info_yaml,
)


def test_parse_extracts_both_fields_from_last_term():
    yaml_text = textwrap.dedent("""
        - id:
            bioguide: O000174
          terms:
            - type: sen
              url: https://old.example.com
              contact_form: https://example.com/old-form
            - type: sen
              url: https://www.ossoff.senate.gov
              contact_form: https://www.ossoff.senate.gov/contact-us/
    """)
    out = parse_contact_info_yaml(yaml_text)
    assert out == {
        "O000174": {
            "contact_form": "https://www.ossoff.senate.gov/contact-us/",
            "website": "https://www.ossoff.senate.gov",
        },
    }


def test_parse_returns_website_only_when_contact_form_missing_everywhere():
    """Mirrors the ~58% of House reps in real data: homepage but no form."""
    yaml_text = textwrap.dedent("""
        - id:
            bioguide: P000609
          terms:
            - type: rep
              url: https://palmer.house.gov
    """)
    out = parse_contact_info_yaml(yaml_text)
    assert out == {
        "P000609": {
            "contact_form": None,
            "website": "https://palmer.house.gov",
        },
    }


def test_parse_falls_back_through_terms_independently_per_field():
    """contact_form on an earlier term + url on the latest term — both surface."""
    yaml_text = textwrap.dedent("""
        - id:
            bioguide: D000004
          terms:
            - type: rep
              contact_form: https://d.house.gov/contact
              url: https://old.d.house.gov
            - type: sen
              url: https://www.d.senate.gov
    """)
    out = parse_contact_info_yaml(yaml_text)
    assert out == {
        "D000004": {
            "contact_form": "https://d.house.gov/contact",
            "website": "https://www.d.senate.gov",
        },
    }


def test_parse_skips_entries_with_no_bioguide_id():
    yaml_text = textwrap.dedent("""
        - id:
            govtrack: 99999
          terms:
            - type: rep
              contact_form: https://nobody.house.gov/contact
              url: https://nobody.house.gov
        - id:
            bioguide: C000003
          terms:
            - type: rep
              url: https://c.house.gov
    """)
    out = parse_contact_info_yaml(yaml_text)
    assert out == {
        "C000003": {"contact_form": None, "website": "https://c.house.gov"},
    }


def test_parse_handles_empty_input():
    assert parse_contact_info_yaml("[]") == {}


def test_parse_entry_with_no_terms_emits_both_null():
    yaml_text = textwrap.dedent("""
        - id:
            bioguide: X000001
          terms: []
    """)
    out = parse_contact_info_yaml(yaml_text)
    assert out == {"X000001": {"contact_form": None, "website": None}}


def test_fetch_contact_info_index_uses_default_url_and_parses_response():
    sample_yaml = (
        "- id:\n"
        "    bioguide: E000005\n"
        "  terms:\n"
        "    - type: rep\n"
        "      url: https://e.house.gov\n"
        "      contact_form: https://e.house.gov/contact\n"
    )

    class _Resp:
        text = sample_yaml
        def raise_for_status(self) -> None: return None

    with patch("_common.requests.get", return_value=_Resp()) as mock_get:
        out = fetch_contact_info_index()
    mock_get.assert_called_once_with(LEGISLATORS_CURRENT_YAML_URL, timeout=30)
    assert out == {
        "E000005": {
            "contact_form": "https://e.house.gov/contact",
            "website": "https://e.house.gov",
        },
    }


def test_fetch_contact_info_index_propagates_http_errors():
    class _Resp:
        text = ""
        def raise_for_status(self) -> None:
            raise _requests.HTTPError("500 Server Error")

    with patch("_common.requests.get", return_value=_Resp()):
        with pytest.raises(_requests.HTTPError):
            fetch_contact_info_index()
