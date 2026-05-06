"""Build a compact ZIP→{state, [districts]} JSON for the Android app.

Two source modes:
  csv: read a HUD-published CSV manually downloaded to disk.
  api: fetch from HUD's /hudapi/public/usps endpoint using a bearer key.
       Iterates Congressional Districts (type=10) and inverts to ZIPs.

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

# State FIPS → USPS code. Includes territories.
STATE_FIPS_TO_USPS: dict[str, str] = {
    "01": "AL", "02": "AK", "04": "AZ", "05": "AR", "06": "CA", "08": "CO",
    "09": "CT", "10": "DE", "11": "DC", "12": "FL", "13": "GA", "15": "HI",
    "16": "ID", "17": "IL", "18": "IN", "19": "IA", "20": "KS", "21": "KY",
    "22": "LA", "23": "ME", "24": "MD", "25": "MA", "26": "MI", "27": "MN",
    "28": "MS", "29": "MO", "30": "MT", "31": "NE", "32": "NV", "33": "NH",
    "34": "NJ", "35": "NM", "36": "NY", "37": "NC", "38": "ND", "39": "OH",
    "40": "OK", "41": "OR", "42": "PA", "44": "RI", "45": "SC", "46": "SD",
    "47": "TN", "48": "TX", "49": "UT", "50": "VT", "51": "VA", "53": "WA",
    "54": "WV", "55": "WI", "56": "WY",
    "60": "AS", "66": "GU", "69": "MP", "72": "PR", "78": "VI",
}

# 119th Congress House delegations. Extend if a state's count changes after
# redistricting; alternatively, derive from members_NNN.json — but pinning
# here keeps the script self-contained for one-shot runs.
HOUSE_DISTRICT_COUNTS: dict[str, int] = {
    "AL": 7, "AK": 1, "AZ": 9, "AR": 4, "CA": 52, "CO": 8, "CT": 5,
    "DE": 1, "FL": 28, "GA": 14, "HI": 2, "ID": 2, "IL": 17, "IN": 9,
    "IA": 4, "KS": 4, "KY": 6, "LA": 6, "ME": 2, "MD": 8, "MA": 9,
    "MI": 13, "MN": 8, "MS": 4, "MO": 8, "MT": 2, "NE": 3, "NV": 4,
    "NH": 2, "NJ": 12, "NM": 3, "NY": 26, "NC": 14, "ND": 1, "OH": 15,
    "OK": 5, "OR": 6, "PA": 17, "RI": 2, "SC": 7, "SD": 1, "TN": 9,
    "TX": 38, "UT": 4, "VA": 11, "VT": 1, "WA": 10, "WV": 2, "WI": 8,
    "WY": 1,
    # Delegate jurisdictions: 1 non-voting delegate.
    "DC": 1, "AS": 1, "GU": 1, "MP": 1, "PR": 1, "VI": 1,
}


def _cd_codes() -> list[tuple[str, int, str]]:
    """Yield (USPS state code, district number, 4-digit CD query code) for every
    voting + non-voting House seat in the 119th Congress."""
    fips_by_usps = {usps: fips for fips, usps in STATE_FIPS_TO_USPS.items()}
    out = []
    for usps, count in HOUSE_DISTRICT_COUNTS.items():
        fips = fips_by_usps[usps]
        for d in range(1, count + 1):
            cd_query = f"{fips}{d:02d}"
            out.append((usps, d, cd_query))
    return out


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
    queries = _cd_codes()
    print(f"Fetching {len(queries)} CD→ZIP crosswalks (year={year} q{quarter})")

    debug_remaining = 1
    misses = 0
    for usps, district, cd_query in queries:
        params = {"type": 10, "query": cd_query, "year": year, "quarter": quarter}
        try:
            body = _hud_get(session, api_key, params, debug=(debug_remaining > 0))
        except Exception as exc:
            print(f"  ! {usps}-{district} (cd={cd_query}): {exc}", file=sys.stderr)
            misses += 1
            continue
        debug_remaining = max(0, debug_remaining - 1)
        try:
            rows = _extract_results(body)
        except KeyError as exc:
            print(f"  ! {usps}-{district} (cd={cd_query}): {exc}", file=sys.stderr)
            misses += 1
            continue
        added = 0
        for row in rows:
            zip_code = _extract_zip(row)
            if not zip_code:
                continue
            entry = by_zip[zip_code]
            entry["state"] = usps
            entry["districts"].add(district)
            added += 1
        print(f"  + {usps}-{district}: {added} ZIPs")
        if sleep_seconds > 0:
            time.sleep(sleep_seconds)

    print(
        f"\nFetched {len(queries) - misses}/{len(queries)} districts; "
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
