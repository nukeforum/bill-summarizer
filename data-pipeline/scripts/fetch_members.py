"""Fetch members of the current Congress and their sponsored/cosponsored bills.

Reads CONGRESS_API_KEY from the environment, walks /member/congress/{c} to
enumerate members, fetches each member's detail + paged sponsored-legislation
and cosponsored-legislation, and writes:

  docs/data/members_{NNN}.json
  docs/data/members/{bioguideId}_sponsored.json
  docs/data/members/{bioguideId}_cosponsored.json

The output `id` field on each bill matches the {type}{number}-{congress}
shape used by the existing bills manifest, so the Android app can do an
O(1) "is this bill in our cache?" lookup.
"""

from __future__ import annotations

import os
import sys
from datetime import datetime, timezone
from typing import Any, Iterable  # datetime and timezone imported by main() for current_congress()

from _common import (
    CongressClient,
    LIST_PAGE_LIMIT,
    current_congress,
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
LEG_PAGES_MAX = 50


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


def main() -> int:
    api_key = os.environ.get("CONGRESS_API_KEY")
    if not api_key:
        print("CONGRESS_API_KEY is not set in the environment.", file=sys.stderr)
        return 2

    congress = current_congress(datetime.now(timezone.utc))
    print(f"Fetching members for the {congress}th Congress")

    client = CongressClient(api_key)
    members_out: list[dict[str, Any]] = []

    for summary in list_members(client, congress):
        bioguide_id = summary.get("bioguideId") or ""
        if not bioguide_id:
            continue
        try:
            detail_body = client.get(f"/member/{bioguide_id}")
            detail = detail_body.get("member") or {}
            # Detail body wins on every overlapping key; list summary supplies fields
            # the detail endpoint sometimes omits (e.g., depiction, terms). The order
            # matters — Congress.gov detail responses are richer for most fields but
            # can drop these specific ones intermittently.
            merged: dict[str, Any] = {**summary, **detail}
            parsed = parse_member_summary(merged)
        except Exception as exc:  # noqa: BLE001
            print(f"  ! skipping {bioguide_id}: {type(exc).__name__}: {exc}", file=sys.stderr)
            continue
        members_out.append(parsed)
        print(f"  + {bioguide_id} {parsed['name']} ({parsed['party']}-{parsed['state']})")

        for kind in ("sponsored", "cosponsored"):
            try:
                raw_items = fetch_legislation(client, bioguide_id, kind)
            except Exception as exc:  # noqa: BLE001
                print(f"    ! {kind} fetch failed for {bioguide_id}: {exc}", file=sys.stderr)
                raw_items = []
            bills = [parse_member_legislation_item(it) for it in raw_items]
            save_member_legislation(bioguide_id, kind, {
                "bioguide_id": bioguide_id,
                "congress": congress,
                "kind": kind,
                "generated_at": now_iso(),
                "bills": bills,
            })

    save_members_index(congress, {
        "congress": congress,
        "generated_at": now_iso(),
        "members": members_out,
    })
    print(f"Wrote index with {len(members_out)} members.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
