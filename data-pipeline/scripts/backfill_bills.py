"""Incrementally backfill historical Congress.gov passage-action bills.

Run on a daily schedule (separate from the fresh-pull workflow). Each
invocation processes a bounded number of list pages for the active Congress
in the backfill queue, merges the results into that Congress's per-Congress
manifest, advances state, and rewrites the index. Failures leave the cursor
where it was so the next run retries the same offset.
"""

from __future__ import annotations

import os
import sys
from collections import Counter
from datetime import datetime, timezone
from typing import Any

from _common import (
    BACKFILL_PAGES_PER_RUN,
    CongressClient,
    LIST_PAGE_LIMIT,
    advance_state,
    build_bill_record,
    evaluate_bill,
    load_manifest,
    load_state,
    merge_records,
    rebuild_index,
    save_manifest,
    save_state,
)

# Effectively no date floor — backfill keeps every passage-action bill we
# find, regardless of how long ago the action was.
NO_DATE_CUTOFF = datetime(1970, 1, 1, tzinfo=timezone.utc)


def list_congress_page(
    client: CongressClient, congress: int, offset: int
) -> list[dict[str, Any]]:
    body = client.get(
        f"/bill/{congress}",
        limit=LIST_PAGE_LIMIT,
        offset=offset,
        sort="updateDate asc",
    )
    return body.get("bills") or []


def main() -> int:
    api_key = os.environ.get("CONGRESS_API_KEY")
    if not api_key:
        print("CONGRESS_API_KEY is not set in the environment.", file=sys.stderr)
        return 2

    state = load_state()
    active = state.get("active_congress")
    if active is None:
        print("Backfill complete: queue is empty.")
        rebuild_index()
        return 0

    offset = state.get("active_offset", 0)
    print(
        f"Backfilling Congress {active} starting at offset {offset} "
        f"(pages_per_run={BACKFILL_PAGES_PER_RUN})"
    )

    client = CongressClient(api_key)
    fresh_records: list[dict[str, Any]] = []
    seen_ids: set[str] = set()
    reject_counts: Counter[str] = Counter()
    total_evaluated = 0
    last_page_size = LIST_PAGE_LIMIT
    pages_consumed = 0
    had_non_empty_page = False

    for page in range(BACKFILL_PAGES_PER_RUN):
        page_offset = offset + page * LIST_PAGE_LIMIT
        bills = list_congress_page(client, active, page_offset)
        last_page_size = len(bills)
        pages_consumed += 1
        if last_page_size > 0:
            had_non_empty_page = True

        for summary in bills:
            total_evaluated += 1
            outcome, reason = evaluate_bill(summary, NO_DATE_CUTOFF)
            if outcome is None:
                reject_counts[reason] += 1
                continue
            try:
                record = build_bill_record(client, active, summary, outcome)
            except Exception as exc:  # noqa: BLE001 - one bad bill must not kill the run
                ref = f"{summary.get('type')}{summary.get('number')}"
                print(
                    f"  ! skipping {ref}: {type(exc).__name__}: {exc}",
                    file=sys.stderr,
                )
                reject_counts["build_error"] += 1
                continue
            if record["id"] in seen_ids:
                reject_counts["duplicate"] += 1
                continue
            seen_ids.add(record["id"])
            fresh_records.append(record)

        if last_page_size < LIST_PAGE_LIMIT:
            break

    print(
        f"Evaluated {total_evaluated} bills across {pages_consumed} page(s); "
        f"kept {len(fresh_records)}."
    )
    if reject_counts:
        print("Rejections:")
        for reason, count in reject_counts.most_common():
            print(f"  - {reason}: {count}")

    existing = load_manifest(active)
    merged, stats = merge_records(existing.get("bills", []), fresh_records)
    final = save_manifest(active, {"bills": merged})

    new_state = advance_state(
        state, last_page_size, pages_consumed, had_non_empty_page
    )
    save_state(new_state)
    rebuild_index()

    print(
        f"merge: +{stats.added} added, ~{stats.updated} updated, "
        f"={stats.unchanged} unchanged (manifest now {len(final['bills'])} bills)"
    )
    held_cursor = (
        new_state.get("active_congress") == active
        and new_state.get("active_offset") == offset
    )
    if last_page_size < LIST_PAGE_LIMIT and not held_cursor:
        print(
            f"Congress {active} complete; "
            f"next: {new_state.get('active_congress')}"
        )
    elif held_cursor and last_page_size < LIST_PAGE_LIMIT:
        print(
            f"Empty page at offset {offset} with no prior progress; "
            f"treating as transient and holding cursor on Congress {active}."
        )
    else:
        print(
            f"Next run resumes at Congress {active} "
            f"offset {new_state['active_offset']}"
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
