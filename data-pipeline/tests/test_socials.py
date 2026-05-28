"""Tests for the unitedstates/congress-legislators socials parser + fetcher."""
from __future__ import annotations

import textwrap
from unittest.mock import patch

import pytest
import requests as _requests

from _common import (
    LEGISLATORS_SOCIAL_MEDIA_YAML_URL,
    KNOWN_SOCIAL_PLATFORMS,
    fetch_socials_index,
    parse_socials_yaml,
)


def test_parse_extracts_big_four_in_known_order():
    yaml_text = textwrap.dedent("""
        - id:
            bioguide: F000483
          social:
            instagram: replaurafriedman
            facebook: RepLauraFriedman
            twitter: RepLauraFriedman
            youtube: "@laurafriedman"
    """)
    out = parse_socials_yaml(yaml_text)
    assert out == {
        "F000483": [
            {"platform": "twitter",   "handle": "RepLauraFriedman"},
            {"platform": "facebook",  "handle": "RepLauraFriedman"},
            {"platform": "youtube",   "handle": "@laurafriedman"},
            {"platform": "instagram", "handle": "replaurafriedman"},
        ],
    }


def test_parse_includes_only_populated_platforms():
    yaml_text = textwrap.dedent("""
        - id:
            bioguide: A000001
          social:
            twitter: SenAlpha
            youtube: "@senalpha"
    """)
    out = parse_socials_yaml(yaml_text)
    assert out == {
        "A000001": [
            {"platform": "twitter", "handle": "SenAlpha"},
            {"platform": "youtube", "handle": "@senalpha"},
        ],
    }


def test_parse_includes_forward_compat_threads_and_bluesky():
    yaml_text = textwrap.dedent("""
        - id:
            bioguide: B000002
          social:
            twitter: SenBeta
            threads: senbeta
            bluesky: senbeta.bsky.social
    """)
    out = parse_socials_yaml(yaml_text)
    assert out == {
        "B000002": [
            {"platform": "twitter", "handle": "SenBeta"},
            {"platform": "threads", "handle": "senbeta"},
            {"platform": "bluesky", "handle": "senbeta.bsky.social"},
        ],
    }


def test_parse_drops_unknown_platforms_and_id_variants():
    yaml_text = textwrap.dedent("""
        - id:
            bioguide: C000003
          social:
            twitter: SenGamma
            twitter_id: 12345
            facebook_id: 67890
            tiktok: sengamma
    """)
    out = parse_socials_yaml(yaml_text)
    assert out == {
        "C000003": [
            {"platform": "twitter", "handle": "SenGamma"},
        ],
    }


def test_parse_skips_entries_without_bioguide_id():
    yaml_text = textwrap.dedent("""
        - id:
            govtrack: 99999
          social:
            twitter: nobody
        - id:
            bioguide: D000004
          social:
            facebook: SenDelta
    """)
    out = parse_socials_yaml(yaml_text)
    assert out == {
        "D000004": [
            {"platform": "facebook", "handle": "SenDelta"},
        ],
    }


def test_parse_skips_entries_with_no_social_block():
    yaml_text = textwrap.dedent("""
        - id:
            bioguide: E000005
    """)
    out = parse_socials_yaml(yaml_text)
    assert out == {}


def test_parse_handles_blank_and_null_yaml():
    assert parse_socials_yaml("") == {}
    assert parse_socials_yaml("null") == {}
    assert parse_socials_yaml("[]") == {}


def test_known_social_platforms_constant_is_the_six_in_scope():
    assert KNOWN_SOCIAL_PLATFORMS == (
        "twitter", "facebook", "youtube", "instagram", "threads", "bluesky",
    )


def test_fetch_socials_index_uses_default_url_and_parses_response():
    sample_yaml = (
        "- id:\n"
        "    bioguide: F000006\n"
        "  social:\n"
        "    twitter: SenZeta\n"
    )

    class _Resp:
        text = sample_yaml
        def raise_for_status(self) -> None: return None

    with patch("_common.requests.get", return_value=_Resp()) as mock_get:
        out = fetch_socials_index()
    mock_get.assert_called_once_with(LEGISLATORS_SOCIAL_MEDIA_YAML_URL, timeout=30)
    assert out == {
        "F000006": [{"platform": "twitter", "handle": "SenZeta"}],
    }


def test_fetch_socials_index_propagates_http_errors():
    class _Resp:
        text = ""
        def raise_for_status(self) -> None:
            raise _requests.HTTPError("500 Server Error")

    with patch("_common.requests.get", return_value=_Resp()):
        with pytest.raises(_requests.HTTPError):
            fetch_socials_index()
