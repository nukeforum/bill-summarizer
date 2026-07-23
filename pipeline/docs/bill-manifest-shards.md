# Sharded per-Congress bill manifest (issue #40)

Part of epic #38. This is the data contract that lets the published record grow
from ~256 bills per Congress to the full ~10,000+ (once #39's pre-floor bills are
published) **without** the app downloading and parsing a tens-of-megabytes single
manifest on every refresh.

Status: **contract-first slice landed** — the wire models and the discovery hook
exist and are byte-parity-tested. The Python-canonical + KMP-parity *shard
builders*, the app-side paging read path (#41), and the shard-staleness freshness
check are still to come. Until a builder populates it, `shard_index_path` is
always `null` and no shard files are published, so nothing about today's delivery
changes.

## Discovery

The sharded index is reached the same way the single manifest is today. Each row
in `docs/data/congresses.json` (`CongressEntry`) already carries `manifest_path`
pointing at the whole `congress<N>_bills.json`. It now *also* carries an optional
`shard_index_path`:

```jsonc
{
  "congress": 119,
  "bill_count": 900,
  "manifest_path": "congress119_bills.json",       // unchanged, keeps working
  "shard_index_path": "congress119_bills_index.json", // null until sharded
  "is_current": true,
  ...
}
```

- `shard_index_path` is `null` for a Congress that has not been sharded (every
  Congress today). It is always *emitted* (not omitted) so the Python-canonical
  and KMP-shadow `congresses.json` stay byte-identical.
- Old app versions ignore the unknown key (`ignoreUnknownKeys = true`), so adding
  it is safe on every install.

## Dual-publish during transition

While app-side paging (#41) is not yet shipped and baked:

- The existing single `congress<N>_bills.json` **keeps being written unchanged**
  (current app versions keep working; its bytes stay identical for the
  floor-outcome subset).
- The shard set (`congress<N>_bills_index.json` + `congress<N>_bills_pNNN.json`)
  is published **alongside** it.
- The single manifest is retired only after paging ships and bakes.

## Shard index schema (`congress<N>_bills_index.json`)

`BillShardIndex` in `pipeline:shared` is the single source of truth:

```jsonc
{
  "generated_at": "2026-07-23T00:00:00Z",
  "congress": 119,
  "page_size": 500,          // stable max bills per shard
  "total_bills": 900,
  "votes_coverage": true,    // mirrors BillsManifest's vote-surface rollout gate
  "shards": [
    { "page": 1, "path": "congress119_bills_p001.json", "count": 500,
      "first_action_date": "2026-04-01", "last_action_date": "2026-07-22" },
    { "page": 2, "path": "congress119_bills_p002.json", "count": 400,
      "first_action_date": "2025-01-03", "last_action_date": "2026-03-31" }
  ]
}
```

## Ordering, paging, and naming

- **Ordering:** `shards` is **most-recent-first** — page 1 holds the newest bills
  by latest-action date, matching the app's recency-first bills list. A client can
  render the first page without fetching the rest.
- **Page size:** each shard holds up to `page_size` bills; only the last (oldest)
  shard may hold fewer. `page` is the stable 1-based ordinal.
- **Shard naming:** `congress<N>_bills_p<NNN>.json`, zero-padded to 3 digits
  (`p001`, `p002`, …), resolved relative to the same `data/` directory as
  `manifest_path`.
- **Shard content:** each shard file has the **same wire shape as `BillsManifest`**
  (`generated_at` / `congress` / `votes_coverage` / `bills`), so a shard parses
  with the existing model and cache path — no new per-shard model is needed. A
  shard carries only its own page of bills.
- **Date windows:** `first_action_date` / `last_action_date` bound each shard's
  recency window so a client can seek a page by date without opening it.

## Freshness (to come)

`check-freshness` will be extended so a half-written shard set (index references a
shard that is missing, or a shard's `count` disagrees with the index) fails CI
rather than serving a torn record. Wrong/stale data outranks everything.
