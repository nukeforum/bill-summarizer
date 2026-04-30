"""
Fetch recently voted-on U.S. Congressional bills and write a static JSON
manifest the Android app consumes.

Reads CONGRESS_API_KEY from the environment, queries the Congress.gov v3 API
for the current Congress, keeps bills whose latest passage-type action falls
within the last RECENT_DAYS, enriches each one with sponsor / CRS summary /
full-text URLs, and writes docs/data/bills.json.
"""

from __future__ import annotations

import json
import os
import sys
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Iterable

import requests

API_BASE = "https://api.congress.gov/v3"
USER_AGENT = "bill-summarizer-pipeline/1.0 (+https://github.com/nukeforum/bill-summarizer)"
RECENT_DAYS = 60
LIST_PAGE_LIMIT = 250
LIST_PAGES_MAX = 4  # 1000 most-recently-updated bills is plenty for a 60-day window
REQUEST_TIMEOUT = 30
RETRY_COUNT = 3
RETRY_BACKOFF_SECONDS = 2.0

REPO_ROOT = Path(__file__).resolve().parents[2]
OUTPUT_PATH = REPO_ROOT / "docs" / "data" / "bills.json"


# ---------- outcome classification ----------------------------------------

OUTCOME_PASSED_HOUSE = "passed_house"
OUTCOME_PASSED_SENATE = "passed_senate"
OUTCOME_ENACTED = "enacted"
OUTCOME_VETOED = "vetoed"
OUTCOME_FAILED = "failed"

# Order matters: enacted/vetoed dominate over chamber passage when both apply.
_OUTCOME_RULES: list[tuple[str, tuple[str, ...]]] = [
    (OUTCOME_ENACTED, ("became public law", "became law")),
    (OUTCOME_VETOED, ("vetoed by president",)),
    (OUTCOME_FAILED, ("failed of passage", "motion to table agreed to")),
    (OUTCOME_PASSED_HOUSE, ("passed/agreed to in house", "passed house")),
    (OUTCOME_PASSED_SENATE, ("passed/agreed to in senate", "passed senate")),
]


def classify_outcome(action_text: str) -> str | None:
    """Return one of the OUTCOME_* constants if action_text matches a rule."""
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


# ---------- pipeline -------------------------------------------------------


def list_recent_bills(client: CongressClient, congress: int) -> Iterable[dict[str, Any]]:
    """Yield bill summaries from /bill/{congress}, sorted by updateDate desc."""
    for page in range(LIST_PAGES_MAX):
        offset = page * LIST_PAGE_LIMIT
        body = client.get(
            f"/bill/{congress}",
            limit=LIST_PAGE_LIMIT,
            offset=offset,
            sort="updateDate+desc",
        )
        bills = body.get("bills") or []
        if not bills:
            return
        yield from bills
        if len(bills) < LIST_PAGE_LIMIT:
            return


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


def build_bill_record(
    client: CongressClient,
    congress: int,
    bill_summary: dict[str, Any],
    cutoff: datetime,
) -> dict[str, Any] | None:
    bill_type = (bill_summary.get("type") or "").lower()
    bill_number = str(bill_summary.get("number") or "")
    if not bill_type or not bill_number:
        return None

    latest_action = bill_summary.get("latestAction") or {}
    outcome = classify_outcome(latest_action.get("text") or "")
    if outcome is None:
        return None

    action_date = parse_iso_date(latest_action.get("actionDate") or latest_action.get("date"))
    if action_date is None or action_date < cutoff:
        return None

    detail = (
        client.get(f"/bill/{congress}/{bill_type}/{bill_number}").get("bill") or {}
    )
    sponsors = detail.get("sponsors") or []
    sponsor = sponsors[0] if sponsors else {}
    sponsor_name = sponsor.get("fullName") or sponsor.get("lastName") or "Unknown"

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
    """Congress.gov exposes alternate titles in detail.titles."""
    titles = detail.get("titles") or []
    for entry in titles:
        title_type = (entry.get("titleType") or "").lower()
        if "short title" in title_type:
            return entry.get("title")
    return None


def _fetch_latest_crs_summary(
    client: CongressClient, congress: int, bill_type: str, bill_number: str
) -> str | None:
    body = client.get(f"/bill/{congress}/{bill_type}/{bill_number}/summaries")
    summaries = body.get("summaries") or []
    if not summaries:
        return None
    # Pick the most recently updated CRS summary.
    summaries.sort(key=lambda s: s.get("updateDate") or "", reverse=True)
    return summaries[0].get("text")


def _fetch_text_urls(
    client: CongressClient, congress: int, bill_type: str, bill_number: str
) -> dict[str, str]:
    body = client.get(f"/bill/{congress}/{bill_type}/{bill_number}/text")
    versions = body.get("textVersions") or []
    if not versions:
        return {}
    # Use the latest (first when sorted by date desc) version's formats.
    versions.sort(key=lambda v: v.get("date") or "", reverse=True)
    formats = versions[0].get("formats") or []
    out: dict[str, str] = {}
    for fmt in formats:
        url = fmt.get("url")
        if not url:
            continue
        ftype = (fmt.get("type") or "").lower()
        if "html" in ftype and "html" not in out:
            out["html"] = url
        elif ("xml" in ftype or "uslm" in ftype) and "xml" not in out:
            out["xml"] = url
        elif "pdf" in ftype and "pdf" not in out:
            out["pdf"] = url
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


# ---------- main -----------------------------------------------------------


def main() -> int:
    api_key = os.environ.get("CONGRESS_API_KEY")
    if not api_key:
        print("CONGRESS_API_KEY is not set in the environment.", file=sys.stderr)
        return 2

    now = datetime.now(timezone.utc)
    cutoff = now - timedelta(days=RECENT_DAYS)
    congress = current_congress(now)
    print(f"Fetching bills for the {congress}th Congress (cutoff {cutoff.date()})")

    client = CongressClient(api_key)
    seen_ids: set[str] = set()
    records: list[dict[str, Any]] = []

    for summary in list_recent_bills(client, congress):
        try:
            record = build_bill_record(client, congress, summary, cutoff)
        except requests.RequestException as exc:
            bill_ref = f"{summary.get('type')}{summary.get('number')}"
            print(f"  ! skipping {bill_ref}: {exc}", file=sys.stderr)
            continue
        if record is None:
            continue
        if record["id"] in seen_ids:
            continue
        seen_ids.add(record["id"])
        records.append(record)
        print(f"  + {record['id']}: {record['outcome']} on {record['latest_action']['date']}")

    records.sort(key=lambda r: r["latest_action"]["date"], reverse=True)

    manifest = {
        "generated_at": now.replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "congress": congress,
        "bills": records,
    }

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT_PATH.open("w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2, sort_keys=False)
        f.write("\n")
    print(f"Wrote {len(records)} bills to {OUTPUT_PATH.relative_to(REPO_ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
