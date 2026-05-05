"""Shared infrastructure for the bill-summarizer data pipeline.

Used by both ``fetch_bills.py`` (daily fresh pull) and ``backfill_bills.py``
(incremental historical crawler). All API client behaviour, bill filtering
rules, record building, and output-path conventions live here so the two
scripts behave identically on the parts that matter.
"""

from __future__ import annotations

import json
import re
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import requests


_PARTY_STATE_SUFFIX_RE = re.compile(r"\s*\[[A-Z]+-[A-Z]{2}(?:-\d+)?\]\s*$")


def clean_sponsor_name(full_name: str) -> str:
    """Strip the trailing party/state suffix Congress.gov bakes into fullName.

    Senators come back as ``Sen. Peters, Gary C. [D-MI]``; House reps add a
    district number, e.g. ``Rep. Smith, Adrian [R-NE-3]``. Both shapes are
    stripped. Names without a suffix (e.g. fallback to ``lastName``) pass
    through unchanged.
    """
    return _PARTY_STATE_SUFFIX_RE.sub("", full_name).strip()


# Self-check: cheap to run at import; if these ever fail something has shifted
# in upstream Congress.gov data and the rendered detail screen will look ugly.
assert clean_sponsor_name("Sen. Peters, Gary C. [D-MI]") == "Sen. Peters, Gary C."
assert clean_sponsor_name("Rep. Smith, Adrian [R-NE-3]") == "Rep. Smith, Adrian"
assert clean_sponsor_name("Sen. Sanders, Bernard") == "Sen. Sanders, Bernard"
assert clean_sponsor_name("Unknown") == "Unknown"


def _classify_text_format_url(url: str) -> str | None:
    """Classify a bill text URL by file extension.

    The Congress API's ``type`` field uses human labels like "Formatted Text"
    for HTML and "Formatted XML" for XML, so substring-matching it is fragile.
    The URL itself is unambiguous — pin classification to the extension.
    """
    lower = url.lower().split("?", 1)[0]
    if lower.endswith((".htm", ".html")):
        return "html"
    if lower.endswith(".xml"):
        return "xml"
    if lower.endswith(".pdf"):
        return "pdf"
    return None


assert _classify_text_format_url(
    "https://www.congress.gov/119/bills/s4465/BILLS-119s4465es.htm"
) == "html"
assert _classify_text_format_url(
    "https://www.congress.gov/119/bills/s4465/BILLS-119s4465es.xml"
) == "xml"
assert _classify_text_format_url(
    "https://www.congress.gov/119/bills/s4465/BILLS-119s4465es.pdf"
) == "pdf"
assert _classify_text_format_url("https://example.com/bill") is None


API_BASE = "https://api.congress.gov/v3"
USER_AGENT = "bill-summarizer-pipeline/1.0 (+https://github.com/nukeforum/bill-summarizer)"
LIST_PAGE_LIMIT = 250
REQUEST_TIMEOUT = 30
RETRY_COUNT = 3
RETRY_BACKOFF_SECONDS = 2.0

REPO_ROOT = Path(__file__).resolve().parents[2]
OUTPUT_DIR = REPO_ROOT / "docs" / "data"
STATE_DIR = REPO_ROOT / "data-pipeline" / "state"


# ---------- outcome classification ----------------------------------------

OUTCOME_PASSED_HOUSE = "passed_house"
OUTCOME_PASSED_SENATE = "passed_senate"
OUTCOME_ENACTED = "enacted"
OUTCOME_VETOED = "vetoed"
OUTCOME_FAILED = "failed"

_OUTCOME_RULES: list[tuple[str, tuple[str, ...]]] = [
    (OUTCOME_ENACTED, ("became public law", "became law")),
    (OUTCOME_VETOED, ("vetoed by president",)),
    (
        OUTCOME_FAILED,
        (
            "failed of passage",
            "motion to table agreed to",
            "failed to pass",
            "rejected",
        ),
    ),
    (
        OUTCOME_PASSED_HOUSE,
        (
            "passed/agreed to in house",
            "passed house",
            "on passage passed by the house",
            "agreed to in house",
        ),
    ),
    (
        OUTCOME_PASSED_SENATE,
        (
            "passed/agreed to in senate",
            "passed senate",
            "on passage passed by the senate",
            "agreed to in senate",
        ),
    ),
]


def classify_outcome(action_text: str) -> str | None:
    needle = action_text.lower()
    for outcome, patterns in _OUTCOME_RULES:
        if any(p in needle for p in patterns):
            return outcome
    return None


# ---------- congress math -------------------------------------------------


def current_congress(today: datetime | None = None) -> int:
    """The 119th Congress runs Jan 3, 2025 - Jan 3, 2027."""
    today = today or datetime.now(timezone.utc)
    # Each Congress is 2 years; 1789 was the 1st.
    # During Jan 1-2 of an odd year the previous Congress is technically still
    # in session, but cron runs daily so a one-day off-by-one doesn't matter.
    return (today.year - 1789) // 2 + 1


# ---------- HTTP ----------------------------------------------------------


class CongressClient:
    def __init__(self, api_key: str) -> None:
        self._session = requests.Session()
        self._session.headers.update({"User-Agent": USER_AGENT, "Accept": "application/json"})
        self._api_key = api_key

    def get(self, path: str, **params: Any) -> dict[str, Any]:
        params["api_key"] = self._api_key
        url = f"{API_BASE}{path}"
        last_exc: Exception | None = None
        for attempt in range(1, RETRY_COUNT + 1):
            try:
                resp = self._session.get(url, params=params, timeout=REQUEST_TIMEOUT)
                if resp.status_code == 404:
                    return {}
                resp.raise_for_status()
                return resp.json()
            except (requests.RequestException, ValueError) as exc:
                last_exc = exc
                if attempt < RETRY_COUNT:
                    time.sleep(RETRY_BACKOFF_SECONDS * attempt)
        assert last_exc is not None
        raise last_exc


# ---------- record building ------------------------------------------------


def parse_iso_date(value: str | None) -> datetime | None:
    if not value:
        return None
    try:
        # Congress.gov returns dates like "2026-04-20" or "2026-04-20T13:45:00Z".
        if "T" in value:
            return datetime.fromisoformat(value.replace("Z", "+00:00"))
        return datetime.fromisoformat(value).replace(tzinfo=timezone.utc)
    except ValueError:
        return None


def normalize_party(value: str | None) -> str:
    if not value:
        return ""
    v = value.strip().upper()
    if v.startswith("D"):
        return "D"
    if v.startswith("R"):
        return "R"
    if v.startswith("I"):
        return "I"
    return v[:1]


def evaluate_bill(
    bill_summary: dict[str, Any], cutoff: datetime
) -> tuple[str | None, str]:
    """Return (outcome, reject_reason). outcome is None when rejected."""
    bill_type = (bill_summary.get("type") or "").lower()
    bill_number = str(bill_summary.get("number") or "")
    if not bill_type or not bill_number:
        return None, "missing_type_or_number"

    latest_action = bill_summary.get("latestAction") or {}
    action_text = latest_action.get("text") or ""
    outcome = classify_outcome(action_text)
    if outcome is None:
        return None, "no_outcome_match"

    action_date = parse_iso_date(latest_action.get("actionDate") or latest_action.get("date"))
    if action_date is None:
        return None, "unparseable_action_date"
    if action_date < cutoff:
        return None, "action_too_old"

    return outcome, "kept"


def build_bill_record(
    client: CongressClient,
    congress: int,
    bill_summary: dict[str, Any],
    outcome: str,
) -> dict[str, Any]:
    bill_type = (bill_summary["type"] or "").lower()
    bill_number = str(bill_summary["number"])
    latest_action = bill_summary.get("latestAction") or {}

    detail = (
        client.get(f"/bill/{congress}/{bill_type}/{bill_number}").get("bill") or {}
    )
    sponsors = detail.get("sponsors") or []
    sponsor = sponsors[0] if sponsors else {}
    sponsor_name = clean_sponsor_name(
        sponsor.get("fullName") or sponsor.get("lastName") or "Unknown"
    )

    summary_text = _fetch_latest_crs_summary(client, congress, bill_type, bill_number)
    text_urls = _fetch_text_urls(client, congress, bill_type, bill_number)

    return {
        "id": f"{bill_type}{bill_number}-{congress}",
        "congress": congress,
        "type": bill_type,
        "number": bill_number,
        "title": detail.get("title") or bill_summary.get("title") or "",
        "short_title": _extract_short_title(detail),
        "sponsor": {
            "name": sponsor_name,
            "party": normalize_party(sponsor.get("party")),
            "state": (sponsor.get("state") or "").upper(),
        },
        "introduced_date": detail.get("introducedDate") or "",
        "latest_action": {
            "date": (latest_action.get("actionDate") or latest_action.get("date") or "")[:10],
            "text": latest_action.get("text") or "",
        },
        "outcome": outcome,
        "summary_crs": summary_text,
        "text_url_html": text_urls.get("html"),
        "text_url_xml": text_urls.get("xml"),
        "text_url_pdf": text_urls.get("pdf"),
        "congress_gov_url": _build_congress_gov_url(congress, bill_type, bill_number),
    }


def _extract_short_title(detail: dict[str, Any]) -> str | None:
    """Try to pull a short title out of detail.titles.

    The shape varies: sometimes a list of {title, titleType} dicts, sometimes
    a {url: ...} pointer to a sub-resource, sometimes a list of plain strings.
    Be permissive — short_title is a nice-to-have, not load-bearing.
    """
    titles = detail.get("titles")
    if not isinstance(titles, list):
        return None
    for entry in titles:
        if not isinstance(entry, dict):
            continue
        title_type = (entry.get("titleType") or "").lower()
        if "short title" in title_type:
            value = entry.get("title")
            if isinstance(value, str):
                return value
    return None


def _fetch_latest_crs_summary(
    client: CongressClient, congress: int, bill_type: str, bill_number: str
) -> str | None:
    body = client.get(f"/bill/{congress}/{bill_type}/{bill_number}/summaries")
    summaries = body.get("summaries")
    if not isinstance(summaries, list) or not summaries:
        return None
    dict_summaries = [s for s in summaries if isinstance(s, dict)]
    if not dict_summaries:
        return None
    dict_summaries.sort(key=lambda s: s.get("updateDate") or "", reverse=True)
    text = dict_summaries[0].get("text")
    return text if isinstance(text, str) and text else None


def _fetch_text_urls(
    client: CongressClient, congress: int, bill_type: str, bill_number: str
) -> dict[str, str]:
    body = client.get(f"/bill/{congress}/{bill_type}/{bill_number}/text")
    versions = body.get("textVersions")
    if not isinstance(versions, list) or not versions:
        return {}
    dict_versions = [v for v in versions if isinstance(v, dict)]
    if not dict_versions:
        return {}
    dict_versions.sort(key=lambda v: v.get("date") or "", reverse=True)
    formats: list[Any] = []
    for version in dict_versions:
        candidate = version.get("formats")
        if isinstance(candidate, list) and candidate:
            formats = candidate
            break
    if not formats:
        return {}
    out: dict[str, str] = {}
    for fmt in formats:
        if not isinstance(fmt, dict):
            continue
        url = fmt.get("url")
        if not isinstance(url, str):
            continue
        kind = _classify_text_format_url(url)
        if kind and kind not in out:
            out[kind] = url
    return out


_BILL_TYPE_TO_SLUG = {
    "hr": "house-bill",
    "s": "senate-bill",
    "hjres": "house-joint-resolution",
    "sjres": "senate-joint-resolution",
    "hconres": "house-concurrent-resolution",
    "sconres": "senate-concurrent-resolution",
    "hres": "house-resolution",
    "sres": "senate-resolution",
}


def _build_congress_gov_url(congress: int, bill_type: str, bill_number: str) -> str:
    slug = _BILL_TYPE_TO_SLUG.get(bill_type, bill_type)
    return f"https://www.congress.gov/bill/{congress}th-congress/{slug}/{bill_number}"
