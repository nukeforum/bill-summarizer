"""Build a compact ZIP→{state, [districts]} JSON for the Android app.

Two source modes:
  csv: read a HUD-published CSV manually downloaded to disk.
  api: fetch from HUD's /hudapi/public/usps endpoint using a bearer key.
       Iterates states (type=5, zip-cd) and reduces to ZIPs.

The API mode is preferred — quarterly automated builds run in GitHub Actions
via update-zip-crosswalk.yml.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import sys
import time
from collections import defaultdict
from pathlib import Path
from typing import Any

import requests


# --- shared output ---------------------------------------------------------

def _emit(by_zip: dict[str, dict[str, Any]], output_json: Path) -> None:
    output: dict[str, dict[str, Any]] = {}
    for zip_code, entry in by_zip.items():
        output[zip_code] = {
            "state": entry["state"],
            "districts": sorted(entry["districts"]),
        }
    output_json.parent.mkdir(parents=True, exist_ok=True)
    with output_json.open("w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, separators=(",", ":"))


# --- CSV mode (original) ---------------------------------------------------

def _normalize_cd(raw: str) -> int:
    s = raw.strip().lstrip("0") or "0"
    n = int(s)
    if n == 98:  # HUD non-voting delegate code
        return 0
    return n


def build_from_csv(source_csv: Path, output_json: Path) -> None:
    by_zip: dict[str, dict[str, Any]] = defaultdict(
        lambda: {"state": "", "districts": set()}
    )
    with source_csv.open("r", encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            zip_code = (row.get("ZIP") or "").zfill(5)
            state = (row.get("STATE") or "").upper()
            cd_raw = row.get("CD") or "0"
            if not zip_code or not state:
                continue
            entry = by_zip[zip_code]
            entry["state"] = state
            entry["districts"].add(_normalize_cd(cd_raw))
    _emit(by_zip, output_json)


# --- API mode --------------------------------------------------------------

HUD_API = "https://www.huduser.gov/hudapi/public/usps"
USER_AGENT = "bill-summarizer-pipeline/1.0"

# 50 states + DC + 5 territories. The HUD API supports 2-letter state codes
# in the query param (since 2021 Q1) for all crosswalk types.
_STATE_QUERIES: list[str] = [
    "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA",
    "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD",
    "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ",
    "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC",
    "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY",
    "DC", "AS", "GU", "MP", "PR", "VI",
]


def _normalize_cd_code(raw: str) -> int:
    """Normalize HUD's 4-digit CD GEOID (FIPS+CD, e.g. "0501") to a district int.

    The last 2 digits are the CD code. "00" (at-large) and "98" (non-voting
    delegate) both map to 0 in our output for UI uniformity.
    """
    s = str(raw).strip()
    if not s:
        return 0
    # Last 2 digits are the CD code; strip leading zeros.
    cd_digits = s[-2:].lstrip("0") or "0"
    n = int(cd_digits)
    if n == 98:
        return 0
    return n


def _hud_get(
    session: requests.Session, key: str, params: dict[str, Any], debug: bool
) -> dict[str, Any]:
    headers = {
        "Authorization": f"Bearer {key}",
        "User-Agent": USER_AGENT,
        "Accept": "application/json",
    }
    r = session.get(HUD_API, headers=headers, params=params, timeout=30)
    if r.status_code != 200:
        raise RuntimeError(
            f"HUD API {r.status_code} for params={params}: "
            f"{r.text[:300]!r}"
        )
    body = r.json()
    if debug:
        # Log the first response so first-CI-run reveals the actual shape.
        data_keys = (
            list(body.get("data").keys())
            if isinstance(body.get("data"), dict)
            else "n/a"
        )
        print(
            f"  [debug] response keys: {list(body.keys())}; "
            f"data keys: {data_keys}",
            file=sys.stderr,
        )
    return body


def _extract_results(body: dict[str, Any]) -> list[dict[str, Any]]:
    """Adaptively pull the results array from a HUD API response.

    The shape isn't fully documented; try common keys.
    """
    data = body.get("data") or body
    if isinstance(data, dict):
        for key in ("results", "result", "items"):
            v = data.get(key)
            if isinstance(v, list):
                return v
    if isinstance(body.get("results"), list):
        return body["results"]
    raise KeyError(
        f"could not find results list in response; top keys: {list(body.keys())}"
    )


def _extract_zip(row: dict[str, Any]) -> str | None:
    for key in ("zip", "ZIP", "Zip", "zipcode", "zip_code"):
        v = row.get(key)
        if v is not None:
            return str(v).zfill(5)
    return None


def _extract_cd_value(row: dict[str, Any]) -> str | None:
    """Pull the 4-digit CD GEOID from a type=5 result row.

    The docs say the per-row geometry field name varies by crosswalk type
    ('cd' for zip-cd). It also might be exposed as 'geoid'. Try both.
    """
    for key in ("cd", "CD", "geoid", "GEOID"):
        v = row.get(key)
        if v is not None:
            return str(v)
    return None


def build_from_api(
    output_json: Path,
    api_key: str,
    year: int,
    quarter: int,
    sleep_seconds: float = 0.0,
) -> None:
    by_zip: dict[str, dict[str, Any]] = defaultdict(
        lambda: {"state": "", "districts": set()}
    )
    session = requests.Session()
    print(f"Fetching ZIP-CD crosswalk per state (year={year} q{quarter})")

    debug_remaining = 1
    misses = 0
    for state in _STATE_QUERIES:
        params = {"type": 5, "query": state, "year": year, "quarter": quarter}
        try:
            body = _hud_get(session, api_key, params, debug=(debug_remaining > 0))
        except Exception as exc:
            print(f"  ! {state}: {exc}", file=sys.stderr)
            misses += 1
            continue
        debug_remaining = max(0, debug_remaining - 1)
        try:
            rows = _extract_results(body)
        except KeyError as exc:
            print(f"  ! {state}: {exc}", file=sys.stderr)
            misses += 1
            continue
        added = 0
        for row in rows:
            zip_code = _extract_zip(row)
            cd_value = _extract_cd_value(row)
            if not zip_code or not cd_value:
                continue
            district = _normalize_cd_code(cd_value)
            entry = by_zip[zip_code]
            entry["state"] = state
            entry["districts"].add(district)
            added += 1
        print(f"  + {state}: {added} ZIP×CD pairs")
        if sleep_seconds > 0:
            time.sleep(sleep_seconds)

    print(
        f"\nFetched {len(_STATE_QUERIES) - misses}/{len(_STATE_QUERIES)} states; "
        f"unique ZIPs: {len(by_zip)}; misses: {misses}"
    )
    if not by_zip:
        raise RuntimeError("no ZIPs collected — refusing to write empty asset")
    _emit(by_zip, output_json)


# --- CLI -------------------------------------------------------------------

def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest="mode", required=True)

    p_csv = sub.add_parser("csv", help="Build from a manually-downloaded HUD CSV")
    p_csv.add_argument("source_csv", type=Path)
    p_csv.add_argument("output_json", type=Path)

    p_api = sub.add_parser("api", help="Build from the HUD HTTP API")
    p_api.add_argument("output_json", type=Path)
    p_api.add_argument("--year", type=int, required=True)
    p_api.add_argument("--quarter", type=int, choices=[1, 2, 3, 4], required=True)
    p_api.add_argument(
        "--sleep", type=float, default=0.0,
        help="Seconds to sleep between requests (rate limiting)",
    )

    args = p.parse_args(argv[1:])

    if args.mode == "csv":
        build_from_csv(args.source_csv, args.output_json)
        print(f"wrote {args.output_json}")
        return 0

    if args.mode == "api":
        api_key = os.environ.get("HUDUSER_API_KEY")
        if not api_key:
            print("HUDUSER_API_KEY not set in environment", file=sys.stderr)
            return 2
        build_from_api(
            output_json=args.output_json,
            api_key=api_key,
            year=args.year,
            quarter=args.quarter,
            sleep_seconds=args.sleep,
        )
        print(f"wrote {args.output_json}")
        return 0

    return 2


# Back-compat alias for the original positional `build(csv, out)` API used by tests.
def build(source_csv: Path, output_json: Path) -> None:
    build_from_csv(source_csv, output_json)


if __name__ == "__main__":
    sys.exit(main(sys.argv))
