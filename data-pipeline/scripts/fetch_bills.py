"""
Fetch recently voted-on U.S. Congressional bills and merge them into the
current Congress's manifest.

Reads CONGRESS_API_KEY from the environment, queries the Congress.gov v3 API
for the current Congress, keeps bills whose latest passage-type action falls
within the last RECENT_DAYS, enriches each one, then merges the result into
docs/data/congress{NNN}_bills.json and rewrites docs/data/congresses.json.
"""

from __future__ import annotations

import os
import sys
from collections import Counter
from datetime import datetime, timedelta, timezone
from typing import Any, Iterable

from _common import (
    CongressClient,
    ErrorCollector,
    LIST_PAGE_LIMIT,
    build_bill_records_parallel,
    current_congress,
    evaluate_bill,
    load_manifest,
    merge_records,
    rebuild_index,
    save_manifest,
)

RECENT_DAYS = 60
LIST_PAGES_MAX = 8
SAMPLE_REJECTIONS = 8


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
    reject_counts: Counter[str] = Counter()
    rejection_samples: list[str] = []
    errors = ErrorCollector()
    kept_summaries: list[tuple[dict[str, Any], str]] = []
    total_evaluated = 0

    # Phase 1: paginated list + filter. Sequential because pagination is
    # stateful and the per-summary work (``evaluate_bill``) is pure-Python.
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
        kept_summaries.append((summary, outcome))

    # Phase 2: per-bill enrichment, fanned out across a thread pool. Each
    # build issues 3 sequential Congress.gov GETs; with N workers we get
    # roughly an N× speedup until the API's per-key rate limit binds.
    fresh_records, build_failures = build_bill_records_parallel(
        client, congress, kept_summaries, errors
    )
    reject_counts["build_error"] += build_failures

    # Drop duplicates after the parallel pass (rare; same bill could appear
    # twice across paginated list responses if its updateDate shifts).
    seen_ids: set[str] = set()
    deduped: list[dict[str, Any]] = []
    for record in fresh_records:
        if record["id"] in seen_ids:
            reject_counts["duplicate"] += 1
            continue
        seen_ids.add(record["id"])
        deduped.append(record)
    fresh_records = deduped
    fresh_records.sort(key=lambda r: r["latest_action"]["date"], reverse=True)

    for record in fresh_records:
        print(f"  + {record['id']}: {record['outcome']} on {record['latest_action']['date']}")

    print()
    print(f"Evaluated {total_evaluated} bills, kept {len(fresh_records)}.")
    if reject_counts:
        print("Rejections:")
        for reason, count in reject_counts.most_common():
            print(f"  - {reason}: {count}")
    if rejection_samples:
        print("Sample latestAction texts that did not match any outcome rule:")
        for sample in rejection_samples:
            print(f"  · {sample}")
    errors.print_summary(label="fetch_bills")

    existing = load_manifest(congress)
    merged, stats = merge_records(existing.get("bills", []), fresh_records)
    final = save_manifest(congress, {"bills": merged})
    rebuild_index()

    print(
        f"merge: +{stats.added} added, ~{stats.updated} updated, "
        f"={stats.unchanged} unchanged (manifest now {len(final['bills'])} bills)"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
