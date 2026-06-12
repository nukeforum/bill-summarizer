"""Shared infrastructure for the bill-summarizer data pipeline.

Used by both ``fetch_bills.py`` (daily fresh pull) and ``backfill_bills.py``
(incremental historical crawler). All API client behaviour, bill filtering
rules, record building, and output-path conventions live here so the two
scripts behave identically on the parts that matter.
"""

from __future__ import annotations

import json
import re
import sys
import threading
import time
from collections import OrderedDict
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable, IO

import requests
import yaml


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


# ---------- error aggregation ---------------------------------------------


@dataclass
class ErrorRecord:
    """Single per-record failure captured by an ErrorCollector.

    ``kind`` is the script-defined call site (e.g. ``"build_bill_record"``,
    ``"member_detail"``, ``"hud_get"``). ``identifier`` is whatever the
    caller wants surfaced in the per-example line — typically the bill ref
    or bioguide ID. ``url`` and ``params`` are optional because not every
    failure path knows them (e.g. JSON parse errors past the HTTP layer).
    """
    kind: str
    identifier: str
    error_class: str
    message: str
    url: str | None = None
    params: dict[str, Any] | None = None


class ErrorCollector:
    """In-memory bucket for per-record failures, surfaced at end of run.

    Each script accumulates failures here instead of (or alongside) inline
    ``print`` calls. ``print_summary`` groups by (kind, error_class) and
    prints a deduplicated digest with the first ``examples_per_class``
    examples of each group plus a "(N more)" tail.

    Designed to pair with the existing rejection counters: rejection
    counters track filter outcomes, this tracks unexpected exceptions.
    """

    def __init__(self) -> None:
        self._errors: list[ErrorRecord] = []
        # ``record`` is called from worker threads when per-bill enrichment is
        # parallelized; keep the list mutation atomic.
        self._lock = threading.Lock()

    def record(
        self,
        kind: str,
        identifier: str,
        exc: BaseException,
        url: str | None = None,
        params: dict[str, Any] | None = None,
    ) -> None:
        rec = ErrorRecord(
            kind=kind,
            identifier=identifier,
            error_class=type(exc).__name__,
            message=str(exc),
            url=url,
            params=params,
        )
        with self._lock:
            self._errors.append(rec)

    def records(self) -> list[ErrorRecord]:
        return list(self._errors)

    def __len__(self) -> int:
        return len(self._errors)

    def __bool__(self) -> bool:
        return bool(self._errors)

    def summary_lines(self, examples_per_class: int = 5) -> list[str]:
        if not self._errors:
            return []
        groups: "OrderedDict[tuple[str, str], list[ErrorRecord]]" = OrderedDict()
        for rec in self._errors:
            key = (rec.kind, rec.error_class)
            groups.setdefault(key, []).append(rec)

        lines: list[str] = [f"{len(self._errors)} error(s) during run:"]
        for (kind, error_class), recs in groups.items():
            lines.append(f"  {kind} / {error_class} × {len(recs)}")
            for rec in recs[:examples_per_class]:
                detail = f"    - {rec.identifier}: {rec.message}"
                if rec.url:
                    detail += f" [url={rec.url}]"
                if rec.params:
                    detail += f" [params={rec.params}]"
                lines.append(detail)
            remaining = len(recs) - examples_per_class
            if remaining > 0:
                lines.append(f"    … {remaining} more")
        return lines

    def print_summary(
        self,
        label: str | None = None,
        file: IO[str] | None = None,
        examples_per_class: int = 5,
    ) -> None:
        """Print the summary digest, defaulting to stderr.

        Stderr is intentional: GitHub Actions surfaces stderr in the
        per-step "errors" panel even when stdout is voluminous.
        """
        if not self._errors:
            return
        out = file if file is not None else sys.stderr
        if label:
            print(f"--- {label} errors ---", file=out)
        for line in self.summary_lines(examples_per_class):
            print(line, file=out)


API_BASE = "https://api.congress.gov/v3"
USER_AGENT = "bill-summarizer-pipeline/1.0 (+https://github.com/nukeforum/bill-summarizer)"
LIST_PAGE_LIMIT = 250
REQUEST_TIMEOUT = 30
RETRY_COUNT = 3
RETRY_BACKOFF_SECONDS = 2.0

# Per-bill enrichment fans out to detail / summaries / text endpoints. Each
# bill is independent, so we process a batch of bills concurrently. Workers
# stay modest because Congress.gov's published quota is ~5000 req/hr per
# key and ``CongressClient.get`` already retries on transient failure.
BUILD_RECORD_WORKERS = 4

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


def build_bill_records_parallel(
    client: CongressClient,
    congress: int,
    items: Iterable[tuple[dict[str, Any], str]],
    errors: ErrorCollector,
    max_workers: int = BUILD_RECORD_WORKERS,
) -> tuple[list[dict[str, Any]], int]:
    """Build bill records for ``items`` (each ``(summary, outcome)``) in parallel.

    Each call to ``build_bill_record`` issues three sequential Congress.gov
    requests (detail / summaries / text) — independent across bills, so we
    fan them out across a thread pool. Returns ``(records, failure_count)``.
    Build failures are recorded into ``errors`` keyed by ``"build_bill_record"``
    and excluded from the returned list. Result order is **not** stable
    (futures complete in arbitrary order); callers that need a sort must
    sort the returned list.
    """
    pending = list(items)
    if not pending:
        return [], 0

    records: list[dict[str, Any]] = []
    failures = 0
    with ThreadPoolExecutor(max_workers=max_workers) as pool:
        future_to_summary = {
            pool.submit(build_bill_record, client, congress, summary, outcome): summary
            for summary, outcome in pending
        }
        for fut in as_completed(future_to_summary):
            summary = future_to_summary[fut]
            try:
                records.append(fut.result())
            except Exception as exc:  # noqa: BLE001 - one bad bill must not kill the run
                ref = f"{summary.get('type')}{summary.get('number')}"
                errors.record("build_bill_record", ref, exc)
                failures += 1
    return records, failures


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


# ---------- merging --------------------------------------------------------


@dataclass
class MergeStats:
    """Per-batch counts. Bills present only in *existing* are silently preserved."""
    added: int = 0
    updated: int = 0
    unchanged: int = 0


def merge_records(
    existing: list[dict[str, Any]],
    incoming: list[dict[str, Any]],
) -> tuple[list[dict[str, Any]], MergeStats]:
    """Merge ``incoming`` into ``existing`` keyed by record ``id``.

    Incoming wins on id collision (the record is a snapshot of current truth,
    not a history log). Bills present only in ``existing`` are preserved.
    Output is sorted by ``latest_action.date`` descending, ties broken by
    ``id`` ascending, so the manifest stays newest-first AND byte-stable
    regardless of which batch contributed each record. The tiebreaker
    matters for the Kotlin-parity check: without it, bills sharing an
    action date keep insertion order (ultimately thread-pool completion
    order), which differs run to run and between the two implementations.
    """
    existing_by_id = {r["id"]: r for r in existing}
    stats = MergeStats()
    merged: dict[str, dict[str, Any]] = dict(existing_by_id)
    for rec in incoming:
        bid = rec["id"]
        if bid not in existing_by_id:
            merged[bid] = rec
            stats.added += 1
        elif existing_by_id[bid] != rec:
            merged[bid] = rec
            stats.updated += 1
        else:
            stats.unchanged += 1
    out = sorted(merged.values(), key=lambda r: r["id"])
    out.sort(key=lambda r: r["latest_action"]["date"], reverse=True)
    return out, stats


# ---------- manifest IO ----------------------------------------------------


def now_iso() -> str:
    """UTC seconds-precision ISO-8601 with trailing Z, matching legacy format."""
    return (
        datetime.now(timezone.utc)
        .replace(microsecond=0)
        .isoformat()
        .replace("+00:00", "Z")
    )


def manifest_path_for(congress: int) -> Path:
    return OUTPUT_DIR / f"congress{congress}_bills.json"


def empty_manifest(congress: int) -> dict[str, Any]:
    return {
        "generated_at": now_iso(),
        "congress": congress,
        "bills": [],
    }


def load_manifest(congress: int) -> dict[str, Any]:
    path = manifest_path_for(congress)
    if not path.exists():
        return empty_manifest(congress)
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def _write_json(path: Path, payload: dict[str, Any]) -> None:
    """Write JSON with trailing newline, ensuring parent directories exist.

    Used by all save functions to maintain consistent file shape across
    manifests, member indices, and member legislation files.
    """
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2, sort_keys=False)
        f.write("\n")


def save_manifest(congress: int, manifest: dict[str, Any]) -> dict[str, Any]:
    """Persist ``manifest`` for ``congress``.

    Stamps the canonical ``generated_at`` and ``congress`` fields and writes
    ``congress{NNN}_bills.json``. Returns the persisted manifest.
    """
    final: dict[str, Any] = {
        "generated_at": now_iso(),
        "congress": congress,
        "bills": manifest.get("bills", []),
    }
    _write_json(manifest_path_for(congress), final)
    return final


# ---------- members ------------------------------------------------------

MEMBERS_SUBDIR = "members"


def members_index_path(congress: int) -> Path:
    return OUTPUT_DIR / f"members_{congress:03d}.json"


def member_legislation_path(bioguide_id: str, kind: str) -> Path:
    return OUTPUT_DIR / MEMBERS_SUBDIR / f"{bioguide_id}_{kind}.json"


def load_members_index(congress: int) -> dict[str, Any] | None:
    p = members_index_path(congress)
    if not p.exists():
        return None
    with p.open("r", encoding="utf-8") as f:
        return json.load(f)


def save_members_index(congress: int, payload: dict[str, Any]) -> dict[str, Any]:
    _write_json(members_index_path(congress), payload)
    return payload


def save_member_legislation(
    bioguide_id: str, kind: str, payload: dict[str, Any]
) -> dict[str, Any]:
    if kind not in ("sponsored", "cosponsored"):
        raise ValueError(f"unknown kind: {kind!r}")
    _write_json(member_legislation_path(bioguide_id, kind), payload)
    return payload


_STATE_NAME_TO_CODE = {
    "Alabama": "AL", "Alaska": "AK", "Arizona": "AZ", "Arkansas": "AR",
    "California": "CA", "Colorado": "CO", "Connecticut": "CT", "Delaware": "DE",
    "Florida": "FL", "Georgia": "GA", "Hawaii": "HI", "Idaho": "ID",
    "Illinois": "IL", "Indiana": "IN", "Iowa": "IA", "Kansas": "KS",
    "Kentucky": "KY", "Louisiana": "LA", "Maine": "ME", "Maryland": "MD",
    "Massachusetts": "MA", "Michigan": "MI", "Minnesota": "MN", "Mississippi": "MS",
    "Missouri": "MO", "Montana": "MT", "Nebraska": "NE", "Nevada": "NV",
    "New Hampshire": "NH", "New Jersey": "NJ", "New Mexico": "NM", "New York": "NY",
    "North Carolina": "NC", "North Dakota": "ND", "Ohio": "OH", "Oklahoma": "OK",
    "Oregon": "OR", "Pennsylvania": "PA", "Rhode Island": "RI", "South Carolina": "SC",
    "South Dakota": "SD", "Tennessee": "TN", "Texas": "TX", "Utah": "UT",
    "Vermont": "VT", "Virginia": "VA", "Washington": "WA", "West Virginia": "WV",
    "Wisconsin": "WI", "Wyoming": "WY",
    "District of Columbia": "DC", "American Samoa": "AS", "Guam": "GU",
    "Northern Mariana Islands": "MP", "Puerto Rico": "PR", "Virgin Islands": "VI",
}


def _state_code(state_name: str | None) -> str:
    if not state_name:
        return ""
    if len(state_name) == 2:
        return state_name.upper()
    code = _STATE_NAME_TO_CODE.get(state_name)
    if code is not None:
        return code
    fallback = state_name[:2].upper()
    print(
        f"  ! unknown state name {state_name!r}; falling back to {fallback!r}",
        file=sys.stderr,
    )
    return fallback


def _chamber_from_terms(terms: list[dict[str, Any]] | None) -> str:
    """Pick the most recent term's chamber."""
    if not terms:
        return "unknown"
    chamber = (terms[-1].get("chamber") or "").lower()
    if "senate" in chamber:
        return "senate"
    if "house" in chamber:
        return "house"
    return "unknown"


def parse_member_summary(raw: dict[str, Any]) -> dict[str, Any]:
    addr = raw.get("addressInformation") or {}
    sponsored = raw.get("sponsoredLegislation") or {}
    cosponsored = raw.get("cosponsoredLegislation") or {}
    depiction = raw.get("depiction") or {}
    name = (
        raw.get("directOrderName")
        or raw.get("name")
        or "Unknown"
    )
    # Normalize at-large House reps: Congress.gov inconsistently returns either
    # null or 0 for the lone district in at-large states (VT, AK, DE, ND, SD,
    # WY) and delegate jurisdictions (DC, AS, GU, MP, PR, VI). The Android
    # picker stores district=0 for these, so map null -> 0 here for consistent
    # matching.
    chamber = _chamber_from_terms(raw.get("terms"))
    district = raw.get("district")
    if chamber == "house" and district is None:
        district = 0
    return {
        "bioguide_id": raw.get("bioguideId") or "",
        "name": name,
        "party": normalize_party(raw.get("partyName") or raw.get("party")),
        "state": _state_code(raw.get("state")),
        "district": district,
        "chamber": chamber,
        "photo_url": depiction.get("imageUrl") or None,
        "official_url": raw.get("officialUrl") or None,
        "sponsored_count": int(sponsored.get("count") or 0),
        "cosponsored_count": int(cosponsored.get("count") or 0),
        "address": addr.get("officeAddress") or None,
        "phone": addr.get("phoneNumber") or None,
    }


def parse_member_legislation_item(raw: dict[str, Any]) -> dict[str, Any]:
    bill_type = (raw.get("type") or "").lower()
    number = str(raw.get("number") or "")
    congress = int(raw.get("congress") or 0)
    latest = raw.get("latestAction") or {}
    policy = raw.get("policyArea") or None
    return {
        "id": f"{bill_type}{number}-{congress}",
        "type": bill_type,
        "number": number,
        "congress": congress,
        "title": raw.get("latestTitle") or raw.get("title") or "",
        "introduced_date": raw.get("introducedDate") or "",
        "latest_action": {
            "date": (latest.get("actionDate") or latest.get("date") or "")[:10],
            "text": latest.get("text") or "",
        },
        "policy_area": (policy.get("name") if isinstance(policy, dict) else policy),
    }


# ---------- unitedstates/congress-legislators contact info ---------------


def parse_contact_info_yaml(text: str) -> dict[str, dict[str, str | None]]:
    """Extract {bioguide: {"contact_form", "website"}} from legislators-current.yaml.

    The upstream YAML (https://github.com/unitedstates/congress-legislators)
    is a list of legislator entries. Each entry has an ``id.bioguide`` key
    and a ``terms`` list; ``contact_form`` and ``url`` (the homepage) live
    on individual terms.

    ``contact_form`` is read from the **current (last) term only**.
    ``url`` (homepage) walks terms in reverse and picks the most recent
    non-empty value, because homepages are stable enough that an older
    term's URL is still useful when the current term hasn't been populated.

    Why the asymmetry: a 2026-05-13 investigation found that of 135
    legislators whose ``contact_form`` came from a prior term, 61%
    returned 404, NXDOMAIN, or 403/410 — congressional offices routinely
    rotate form URLs across terms (and decommission entire
    ``<name>forms.house.gov`` subdomains), so a stale form URL is far
    more likely to be dead than a stale homepage. Reps without a
    current-term ``contact_form`` route through the website-fallback UI
    in the Android client, which has 100% coverage.

    Coverage in production (verified 2026-05-13 against the live YAML):
    all 536 current legislators have a ``url``; ~89 of 536 carry a
    current-term ``contact_form``. The website field is the fallback
    that lets the UI render a contact entry-point for every rep.
    """
    data = yaml.safe_load(text) or []
    out: dict[str, dict[str, str | None]] = {}
    for entry in data:
        if not isinstance(entry, dict):
            continue
        ids = entry.get("id") or {}
        bioguide = ids.get("bioguide")
        if not bioguide:
            continue
        terms = entry.get("terms") or []
        current_term = terms[-1] if terms and isinstance(terms[-1], dict) else {}
        contact_form: str | None = current_term.get("contact_form") or None
        website: str | None = None
        for term in reversed(terms):
            if not isinstance(term, dict):
                continue
            w = term.get("url")
            if w:
                website = w
                break
        out[bioguide] = {"contact_form": contact_form, "website": website}
    return out


LEGISLATORS_CURRENT_YAML_URL = (
    "https://raw.githubusercontent.com/"
    "unitedstates/congress-legislators/main/legislators-current.yaml"
)


def fetch_contact_info_index(
    url: str = LEGISLATORS_CURRENT_YAML_URL,
) -> dict[str, dict[str, str | None]]:
    """Download legislators-current.yaml and return per-bioguide contact info.

    Raises ``requests.HTTPError`` on a non-2xx status. Caller decides whether
    to treat the failure as fatal — Phase 1 of fetch_members.py treats it as
    non-fatal (member records still flow, just with both contact fields null).
    """
    resp = requests.get(url, timeout=30)
    resp.raise_for_status()
    return parse_contact_info_yaml(resp.text)


# ---------- unitedstates/congress-legislators socials --------------------


# Allow-list of platform keys we surface from legislators-social-media.yaml.
# Order here is the canonical output order — both Python and Kotlin parsers
# preserve it per entry. Numeric `_id` variants (twitter_id, facebook_id,
# youtube_id) and unknown keys (e.g. tiktok) are silently dropped.
KNOWN_SOCIAL_PLATFORMS: tuple[str, ...] = (
    "twitter", "facebook", "youtube", "instagram", "threads", "bluesky",
)


def parse_socials_yaml(text: str) -> dict[str, list[dict[str, str]]]:
    """Extract {bioguide: [{platform, handle}, ...]} from legislators-social-media.yaml.

    The upstream YAML (https://github.com/unitedstates/congress-legislators)
    is a list of legislator entries. Each entry has an ``id.bioguide`` key
    and an optional ``social`` map carrying per-platform usernames/handles.

    Only platforms in ``KNOWN_SOCIAL_PLATFORMS`` are surfaced, in that
    constant's order. Numeric ``_id`` variants and any unknown platform
    keys are silently dropped. Entries with no ``social`` block, no
    bioguide id, or no populated known-platform handles are omitted from
    the output entirely (so consumers can ``.get(bid, [])`` for the
    empty case).
    """
    data = yaml.safe_load(text) or []
    out: dict[str, list[dict[str, str]]] = {}
    for entry in data:
        if not isinstance(entry, dict):
            continue
        ids = entry.get("id") or {}
        bioguide = ids.get("bioguide")
        if not bioguide:
            continue
        social = entry.get("social") or {}
        if not isinstance(social, dict):
            continue
        handles: list[dict[str, str]] = []
        for platform in KNOWN_SOCIAL_PLATFORMS:
            raw = social.get(platform)
            if raw is None:
                continue
            handle = str(raw).strip()
            if not handle:
                continue
            handles.append({"platform": platform, "handle": handle})
        if handles:
            out[bioguide] = handles
    return out


LEGISLATORS_SOCIAL_MEDIA_YAML_URL = (
    "https://raw.githubusercontent.com/"
    "unitedstates/congress-legislators/main/legislators-social-media.yaml"
)


def fetch_socials_index(
    url: str = LEGISLATORS_SOCIAL_MEDIA_YAML_URL,
) -> dict[str, list[dict[str, str]]]:
    """Download legislators-social-media.yaml and return per-bioguide handles.

    Raises ``requests.HTTPError`` on a non-2xx status. Caller decides whether
    to treat the failure as fatal — Phase 1 of fetch_members.py treats it as
    non-fatal (member records still flow, just with socials=[]).
    """
    resp = requests.get(url, timeout=30)
    resp.raise_for_status()
    return parse_socials_yaml(resp.text)


# ---------- index ----------------------------------------------------------


_CONGRESS_FILE_RE = re.compile(r"^congress(\d+)_bills\.json$")


def _load_state_silent() -> dict[str, Any]:
    """Best-effort load of the backfill state file. Never raises."""
    state_path = STATE_DIR / "backfill_state.json"
    if not state_path.exists():
        return {}
    try:
        with state_path.open("r", encoding="utf-8") as f:
            return json.load(f)
    except (json.JSONDecodeError, OSError):
        return {}


def rebuild_index() -> dict[str, Any]:
    """Walk OUTPUT_DIR for per-Congress manifests, write docs/data/congresses.json."""
    state = _load_state_silent()
    completed = set(state.get("completed", []))
    current = current_congress()

    entries: list[dict[str, Any]] = []
    for path in sorted(OUTPUT_DIR.glob("congress*_bills.json")):
        match = _CONGRESS_FILE_RE.match(path.name)
        if not match:
            continue
        congress = int(match.group(1))
        with path.open("r", encoding="utf-8") as f:
            data = json.load(f)
        bills = data.get("bills") or []
        dates = [
            b["latest_action"]["date"]
            for b in bills
            if isinstance(b, dict)
            and isinstance(b.get("latest_action"), dict)
            and b["latest_action"].get("date")
        ]
        entries.append({
            "congress": congress,
            "bill_count": len(bills),
            "first_action_date": min(dates) if dates else None,
            "last_action_date": max(dates) if dates else None,
            "manifest_path": path.name,
            "is_current": congress == current,
            "backfill_complete": congress in completed,
        })
    entries.sort(key=lambda e: e["congress"], reverse=True)

    index = {
        "generated_at": now_iso(),
        "current_congress": current,
        "congresses": entries,
    }
    _write_json(OUTPUT_DIR / "congresses.json", index)
    return index


# ---------- backfill state -------------------------------------------------


OLDEST_API_CONGRESS = 93  # earliest Congress reachable via Congress.gov v3 API
BACKFILL_PAGES_PER_RUN = 4


def _state_path() -> Path:
    return STATE_DIR / "backfill_state.json"


def initial_state(now: datetime | None = None) -> dict[str, Any]:
    current = current_congress(now)
    queue = list(range(current, OLDEST_API_CONGRESS - 1, -1))
    return {
        "active_congress": queue[0] if queue else None,
        "active_offset": 0,
        "queue": queue,
        "completed": [],
        "last_run_at": None,
    }


def load_state() -> dict[str, Any]:
    p = _state_path()
    if not p.exists():
        return initial_state()
    try:
        with p.open("r", encoding="utf-8") as f:
            return json.load(f)
    except (json.JSONDecodeError, OSError):
        return initial_state()


def save_state(state: dict[str, Any]) -> None:
    p = _state_path()
    p.parent.mkdir(parents=True, exist_ok=True)
    with p.open("w", encoding="utf-8") as f:
        json.dump(state, f, ensure_ascii=False, indent=2, sort_keys=False)
        f.write("\n")


def advance_state(
    state: dict[str, Any],
    page_returned: int,
    pages_consumed: int,
    had_non_empty_page: bool = False,
) -> dict[str, Any]:
    """Return a new state dict updated for the latest backfill chunk.

    If the most recent list page returned fewer than LIST_PAGE_LIMIT items,
    the active Congress is exhausted: mark it complete, drop it from the
    queue, and shift to the next Congress (or set active_congress=None when
    nothing is left). Otherwise just bump the offset by the pages consumed.

    Guard against transient empty-page responses: an empty first page
    (page_returned == 0, had_non_empty_page == False) at offset 0 is treated
    as a hiccup, not exhaustion — leave the cursor in place so the next run
    retries. Recovery would otherwise need manual state-file surgery.
    """
    new = dict(state)
    new["queue"] = list(state.get("queue", []))
    new["completed"] = list(state.get("completed", []))
    new["last_run_at"] = now_iso()

    active = state.get("active_congress")
    prior_offset = state.get("active_offset", 0)

    if page_returned < LIST_PAGE_LIMIT:
        saw_evidence = (
            page_returned > 0 or had_non_empty_page or prior_offset > 0
        )
        if not saw_evidence:
            new["active_offset"] = prior_offset
            return new
        if active is not None and active not in new["completed"]:
            new["completed"].append(active)
        new["queue"] = [c for c in new["queue"] if c != active]
        new["active_congress"] = new["queue"][0] if new["queue"] else None
        new["active_offset"] = 0
    else:
        new["active_offset"] = prior_offset + pages_consumed * LIST_PAGE_LIMIT

    return new
