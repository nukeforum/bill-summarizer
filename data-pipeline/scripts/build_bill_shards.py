"""Build the sharded per-Congress bill manifests (issue #40, epic #38).

Splits each on-disk ``congress<N>_bills.json`` into recency-first pages
(``congress<N>_bills_p<NNN>.json``) plus an index (``congress<N>_bills_index``),
then rebuilds ``congresses.json`` so each sharded Congress's ``shard_index_path``
discovery hook points at its index. The single manifest is left untouched
(dual-publish) so current app versions keep working until app-side paging (#41)
ships and bakes.

This is a pure re-shape of already-fetched data — no API key, no network. Run:
    python data-pipeline/scripts/build_bill_shards.py            # all congresses
    python data-pipeline/scripts/build_bill_shards.py --congress 119

A scheduled GitHub Action wraps this (alongside fetch-bills) and commits the
shard set; check_freshness.py guards against a torn shard set (to come).
"""
from __future__ import annotations

import argparse
import re
import sys

import _common

_MANIFEST_RE = re.compile(r"^congress(\d+)_bills\.json$")


def _congresses_on_disk() -> list[int]:
    found = []
    for path in sorted(_common.OUTPUT_DIR.glob("congress*_bills.json")):
        match = _MANIFEST_RE.match(path.name)
        if match:
            found.append(int(match.group(1)))
    return sorted(found, reverse=True)


def main() -> int:
    parser = argparse.ArgumentParser(description="Build sharded per-Congress bill manifests.")
    parser.add_argument("--congress", type=int, default=None, help="shard only this Congress (default: all on disk)")
    parser.add_argument("--page-size", type=int, default=_common.SHARD_PAGE_SIZE, help="max bills per shard")
    args = parser.parse_args()

    if args.page_size < 1:
        print("ERROR: --page-size must be >= 1", file=sys.stderr)
        return 1

    congresses = [args.congress] if args.congress is not None else _congresses_on_disk()
    if not congresses:
        print("no per-Congress manifests found; nothing to shard", file=sys.stderr)
        return 0

    for congress in congresses:
        index = _common.save_bill_shards(congress, page_size=args.page_size)
        print(
            f"OK: sharded congress {congress} "
            f"({index['total_bills']} bills -> {len(index['shards'])} shard(s) of <= {index['page_size']})"
        )

    _common.rebuild_index()
    print("OK: rebuilt congresses.json (shard_index_path populated for sharded congresses)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
