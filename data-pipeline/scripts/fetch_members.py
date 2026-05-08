"""Fetch members of the current Congress and their sponsored/cosponsored bills.

Reads CONGRESS_API_KEY from the environment, runs in two sequential phases:

  Phase 1 (fast, ~7 min): walk /member/congress/{c} + per-member detail and
  publish docs/data/members_{NNN}.json. The Reps tab works as soon as this
  lands, so this phase is unconditional — it always runs to completion and
  the time budget does not gate it.

  Phase 2 (slow, paginated): walk each member's sponsored-legislation and
  cosponsored-legislation endpoints and write
  docs/data/members/{bioguideId}_{kind}.json. Senior senators have thousands
  of cosponsorships; this phase is gated by --time-budget-minutes and is
  self-resumable via per-member file existence (skips members whose two
  legislation files already exist).

The output `id` field on each bill matches the {type}{number}-{congress}
shape used by the existing bills manifest, so the Android app can do an
O(1) "is this bill in our cache?" lookup.
"""

from __future__ import annotations

import argparse
import os
import sys
import time
from datetime import datetime, timezone
from typing import Any, Iterable

from _common import (
    CongressClient,
    ErrorCollector,
    LIST_PAGE_LIMIT,
    current_congress,
    load_members_index,
    member_legislation_path,
    now_iso,
    parse_member_legislation_item,
    parse_member_summary,
    save_member_legislation,
    save_members_index,
)

# Soft caps to bound a runaway pagination loop. Current Congress has ~535
# voting members; the top cosponsorship counts in recent Congresses are
# under 5,000 per (member, kind). Both ceilings have ~5x headroom — bump
# them only if real-world runs hit the limit.
LIST_PAGES_MAX = 10
LEG_PAGES_MAX = 5  # 5 pages × 250 = 1250 items max per (member, kind).
                   # Senior senators have thousands of cosponsorships; capping
                   # at 1250 keeps runtime bounded without hurting app UX
                   # (we display at most a couple hundred at a time).


def list_members(client: CongressClient, congress: int) -> Iterable[dict[str, Any]]:
    """Yield member summaries from /member/congress/{congress}."""
    for page in range(LIST_PAGES_MAX):
        offset = page * LIST_PAGE_LIMIT
        body = client.get(
            f"/member/congress/{congress}",
            limit=LIST_PAGE_LIMIT,
            offset=offset,
            currentMember="true",
        )
        members = body.get("members") or []
        if not members:
            return
        yield from members
        # Heuristic termination: Congress.gov returns a short final page when
        # the list is exhausted. The `currentMember=true` filter doesn't
        # currently break this in observed responses, but verify if behavior
        # changes in a future API version.
        if len(members) < LIST_PAGE_LIMIT:
            return


def fetch_legislation(
    client: CongressClient, bioguide_id: str, kind: str
) -> list[dict[str, Any]]:
    """Fetch all pages of sponsored or cosponsored legislation for a member."""
    if kind == "sponsored":
        endpoint_path = f"/member/{bioguide_id}/sponsored-legislation"
        body_key = "sponsoredLegislation"
    elif kind == "cosponsored":
        endpoint_path = f"/member/{bioguide_id}/cosponsored-legislation"
        body_key = "cosponsoredLegislation"
    else:
        raise ValueError(f"unknown kind: {kind!r}")

    items: list[dict[str, Any]] = []
    for page in range(LEG_PAGES_MAX):
        offset = page * LIST_PAGE_LIMIT
        body = client.get(endpoint_path, limit=LIST_PAGE_LIMIT, offset=offset)
        page_items = body.get(body_key) or []
        if not page_items:
            break
        items.extend(page_items)
        if len(page_items) < LIST_PAGE_LIMIT:
            break
    return items


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--time-budget-minutes", type=int, default=300,
        help="Soft time budget for Phase 2 (default 300 = 5 hours). Phase 1 "
             "always runs to completion (it's fast). When the budget elapses "
             "during Phase 2, the script writes outputs and exits 0. GitHub "
             "Actions has a 6h hard cap; the default leaves 1h headroom.",
    )
    parser.add_argument(
        "--phase1-only", action="store_true",
        help="Run only Phase 1 (publish the members index) and exit. Use this "
             "when the workflow commits Phase 1 output before invoking the "
             "script a second time for Phase 2 — that pattern keeps the index "
             "on origin within ~10 minutes regardless of Phase 2 duration.",
    )
    parser.add_argument(
        "--phase2-only", action="store_true",
        help="Run only Phase 2 (backfill sponsored/cosponsored legislation). "
             "Phase 2 reads the existing index from disk, so a prior Phase 1 "
             "(or a committed members_NNN.json) must be in place.",
    )
    args = parser.parse_args(argv)
    if args.phase1_only and args.phase2_only:
        print("--phase1-only and --phase2-only are mutually exclusive.", file=sys.stderr)
        return 2
    start_time = time.time()
    deadline = start_time + args.time_budget_minutes * 60

    api_key = os.environ.get("CONGRESS_API_KEY")
    if not api_key:
        print("CONGRESS_API_KEY is not set in the environment.", file=sys.stderr)
        return 2

    congress = current_congress(datetime.now(timezone.utc))
    client = CongressClient(api_key)

    run_phase1 = not args.phase2_only
    run_phase2 = not args.phase1_only

    existing_index = load_members_index(congress) or {"members": []}
    existing_by_bid: dict[str, dict[str, Any]] = {
        m["bioguide_id"]: m for m in existing_index.get("members", [])
    }
    members_out: list[dict[str, Any]] = list(existing_index.get("members", []))

    phase1_errors = ErrorCollector()
    phase2_errors = ErrorCollector()

    # ---------- Phase 1: fast — member roster + detail only ----------
    if not run_phase1:
        print(
            f"Skipping Phase 1; using cached index with {len(members_out)} members."
        )
        if not members_out:
            print(
                "ERROR: --phase2-only requested but no existing index was found.",
                file=sys.stderr,
            )
            return 2
    else:
        print(f"Phase 1: fetching member index for the {congress}th Congress")
        members_out = []
        for summary in list_members(client, congress):
            bioguide_id = summary.get("bioguideId") or ""
            if not bioguide_id:
                continue
            try:
                detail_body = client.get(f"/member/{bioguide_id}")
                detail = detail_body.get("member") or {}
                # Detail body wins on every overlapping key; list summary supplies
                # fields the detail endpoint sometimes omits (e.g. depiction, terms).
                # The order matters — Congress.gov detail responses are richer for
                # most fields but can drop these specific ones intermittently.
                merged: dict[str, Any] = {**summary, **detail}
                parsed = parse_member_summary(merged)
            except Exception as exc:  # noqa: BLE001
                # Fall back to cached record from previous run if we have one;
                # otherwise drop the member silently. The failure goes into
                # the collector either way so the end-of-run summary surfaces
                # systematic upstream issues regardless of cache hit/miss.
                if bioguide_id in existing_by_bid:
                    parsed = existing_by_bid[bioguide_id]
                    phase1_errors.record("member_detail_reused_cache", bioguide_id, exc)
                else:
                    phase1_errors.record("member_detail_dropped", bioguide_id, exc)
                    continue
            members_out.append(parsed)
            print(f"  + {bioguide_id} {parsed['name']} ({parsed['party']}-{parsed['state']})")

        # Publish the index. The Reps tab works as soon as this lands.
        save_members_index(congress, {
            "congress": congress,
            "generated_at": now_iso(),
            "members": members_out,
        })
        print(f"Phase 1 done: index has {len(members_out)} members")
        phase1_errors.print_summary(label="Phase 1")

    if not run_phase2:
        print("Skipping Phase 2 (--phase1-only).")
        return 0

    # ---------- Phase 2: slow — per-member legislation backfill ----------
    print("\nPhase 2: backfilling sponsored/cosponsored legislation")
    backfilled = 0
    skipped_cached = 0
    for parsed in members_out:
        if time.time() > deadline:
            print(
                f"\n[time-budget] Stopping Phase 2 early; processed "
                f"{backfilled} new + skipped {skipped_cached} cached. "
                f"Re-run to continue.",
                file=sys.stderr,
            )
            break
        bioguide_id = parsed["bioguide_id"]
        if (
            member_legislation_path(bioguide_id, "sponsored").exists()
            and member_legislation_path(bioguide_id, "cosponsored").exists()
        ):
            skipped_cached += 1
            continue
        for kind in ("sponsored", "cosponsored"):
            try:
                raw_items = fetch_legislation(client, bioguide_id, kind)
            except Exception as exc:  # noqa: BLE001
                phase2_errors.record(f"{kind}_legislation", bioguide_id, exc)
                raw_items = []
            bills = [parse_member_legislation_item(it) for it in raw_items]
            save_member_legislation(bioguide_id, kind, {
                "bioguide_id": bioguide_id,
                "congress": congress,
                "kind": kind,
                "generated_at": now_iso(),
                "bills": bills,
            })
        backfilled += 1
        print(f"  + {bioguide_id}: legislation backfilled")

    print(
        f"\nPhase 2 done: backfilled {backfilled} members, "
        f"skipped {skipped_cached} already-cached. "
        f"Index has {len(members_out)} total."
    )
    phase2_errors.print_summary(label="Phase 2")
    return 0


if __name__ == "__main__":
    sys.exit(main())
