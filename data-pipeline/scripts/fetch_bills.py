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
from collections import Counter
from datetime import datetime, timedelta, timezone
from typing import Any, Iterable

from _common import (
    CongressClient,
    LIST_PAGE_LIMIT,
    REPO_ROOT,
    build_bill_record,
    current_congress,
    evaluate_bill,
)

RECENT_DAYS = 60
LIST_PAGES_MAX = 8
SAMPLE_REJECTIONS = 8

OUTPUT_PATH = REPO_ROOT / "docs" / "data" / "bills.json"


def list_recent_bills(
    client: CongressClient,
    congress: int,
    cutoff: datetime,
) -> Iterable[dict[str, Any]]:
    """Yield bill summaries from /bill/{congress} sorted by updateDate desc."""
    from_dt = cutoff.strftime("%Y-%m-%dT%H:%M:%SZ")
    for page in range(LIST_PAGES_MAX):
        offset = page * LIST_PAGE_LIMIT
        body = client.get(
            f"/bill/{congress}",
            limit=LIST_PAGE_LIMIT,
            offset=offset,
            sort="updateDate desc",
            fromDateTime=from_dt,
        )
        bills = body.get("bills") or []
        if not bills:
            return
        yield from bills
        if len(bills) < LIST_PAGE_LIMIT:
            return


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
    reject_counts: Counter[str] = Counter()
    rejection_samples: list[str] = []
    total_evaluated = 0

    for summary in list_recent_bills(client, congress, cutoff):
        total_evaluated += 1
        outcome, reason = evaluate_bill(summary, cutoff)
        if outcome is None:
            reject_counts[reason] += 1
            if reason == "no_outcome_match" and len(rejection_samples) < SAMPLE_REJECTIONS:
                action_text = (summary.get("latestAction") or {}).get("text") or ""
                ref = f"{summary.get('type')}{summary.get('number')}"
                rejection_samples.append(f"{ref}: {action_text[:140]}")
            continue

        try:
            record = build_bill_record(client, congress, summary, outcome)
        except Exception as exc:  # noqa: BLE001 - one bad bill must not kill the run
            ref = f"{summary.get('type')}{summary.get('number')}"
            print(f"  ! skipping {ref}: {type(exc).__name__}: {exc}", file=sys.stderr)
            reject_counts["build_error"] += 1
            continue

        if record["id"] in seen_ids:
            reject_counts["duplicate"] += 1
            continue
        seen_ids.add(record["id"])
        records.append(record)
        print(f"  + {record['id']}: {record['outcome']} on {record['latest_action']['date']}")

    records.sort(key=lambda r: r["latest_action"]["date"], reverse=True)

    print()
    print(f"Evaluated {total_evaluated} bills, kept {len(records)}.")
    if reject_counts:
        print("Rejections:")
        for reason, count in reject_counts.most_common():
            print(f"  - {reason}: {count}")
    if rejection_samples:
        print("Sample latestAction texts that did not match any outcome rule:")
        for sample in rejection_samples:
            print(f"  · {sample}")

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
