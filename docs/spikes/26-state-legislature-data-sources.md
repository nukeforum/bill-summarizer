# Spike #26 — Data sources for state-legislature coverage

- **Issue:** [#26 — Spike: evaluate data sources for state-legislature coverage](https://github.com/nukeforum/bill-summarizer/issues/26)
- **Epic:** [#17 — State-level accountability (see the record / decide their vote)](https://github.com/nukeforum/bill-summarizer/issues/17)
- **Pillars:** 1 *see the record* + 2 *decide their vote*, at the **state** level
- **Status:** Recommendation — *adopt OpenStates; phase it in pilot-state-first, API-fed, not bulk-fed*. Captain-gated scope (large expansion): the follow-up implementation tickets are **sketched, not filed**, per the epic-first rule.
- **Deliverable note:** This is the in-repo form of the spike's written proposal. The issue asks for it as a comment on epic #17; posting to GitHub is an outward action left to the operator/captain. This document is the reviewable artifact behind that post. Follow-up tickets below are deliberately **not filed** until the captain has had visibility.

---

## TL;DR / recommendation

**Adopt [OpenStates](https://open.pluralpolicy.com/) (formerly Open States, now hosted by Plural) as the single state-legislature source.** It is the only option that clears all four of the app's hard constraints at once:

1. **License** — bulk data is **CC-0 / public-domain dedication** (attribution appreciated, not required). This is the *decisive* differentiator: it makes republishing state bills into our `docs/data/*.json` GitHub-Pages artifacts unambiguously legal. LegiScan (the main alternative aggregator) restricts redistribution and is therefore disqualified for the static-publish path.
2. **BYOK fit** — v3 is a per-user API-keyed REST API (`X-API-KEY` header or `?apikey`), architecturally identical to the Congress.gov key model the app already implements in `:feature:datasources`. A user's OpenStates key drops into the exact same BYOK plumbing.
3. **Uniform 50-state coverage** — one schema across all 50 states + DC + PR, so we integrate *one* client instead of 50 bespoke scrapers.
4. **Freshness** — the *API* is near-live (scrapes run continuously; typically a day or two behind the state site). The **bulk CSV/JSON downloads, by contrast, refresh only monthly** — too stale for the north-star wrong/stale-data rule on the bill/vote path.

**But the rollout must be phased and API-fed, not all-50 and bulk-fed.** State bill volume is 1–2 orders of magnitude larger than the federal dataset (the whole app is currently *one* Congress). A naive "publish all 50 states' bills as static JSON" both blows past a sane GitHub-Pages artifact size **and** inherits the monthly bulk-freshness staleness. The correct architecture mirrors the federal pipeline: **pilot 2–3 states first, fetch from the live API on a recency window, publish a bounded static artifact, and offer on-demand BYOK for anything deeper.**

This is a **large expansion** (new pillar surface, new data contract, new pipeline subsystem), so per the epic-first rule the implementation tickets below are sketched only. **Captain decision requested:** approve OpenStates + the pilot-state phasing before any code is filed.

---

## The four constraints, scored

| Constraint | OpenStates API v3 | OpenStates bulk data | LegiScan | Direct state APIs (2–3 pilots) |
|---|---|---|---|---|
| **License / republish** | CC-0 (public domain) ✅ | CC-0 (public domain) ✅ | Redistribution restricted ❌ | Per-state; mostly public-record ⚠️ |
| **BYOK in-app fit** | Per-user API key, mirrors Congress.gov ✅ | N/A (build-time only) | Per-user key ✅ | Heterogeneous; most have no key/API ❌ |
| **Freshness** | Day-or-two behind live ✅ | **Monthly refresh** ❌ | Near-live ✅ | Varies; a few near-live ⚠️ |
| **Coverage / integration cost** | 50 + DC + PR, one schema ✅ | Same ✅ | 50-state, one schema ✅ | One state = one bespoke parser ❌ |
| **Static-publish volume** | Bounded by *our* window choice ✅ | Full-session dumps, very large ❌ | N/A (can't republish) | Bounded per pilot ✅ |

**Net:** OpenStates wins on license + BYOK + coverage. The only trap inside OpenStates is *which OpenStates surface* you feed the pipeline from — **the live API, never the monthly bulk download**, for anything freshness-sensitive.

---

## Source-by-source detail

### OpenStates / Plural — ✅ recommended

**API v3** ([docs](https://docs.openstates.org/api-v3/)):
- Free with a registered key (`register at open.pluralpolicy.com/accounts/profile/`), passed via `X-API-KEY` header or `?apikey` query param — **byte-for-byte the same integration shape as `CONGRESS_API_KEY`**, so the existing BYOK encrypted-key-on-device flow and the CI-env-key pipeline flow both extend with near-zero new plumbing.
- **Rate-limit tiers** ([discussion #205](https://github.com/openstates/issues/discussions/205)) are `(per_minute, per_hour, per_day)`:
  - **Default: 10/min, 500/day**
  - Bronze: 40/min, 5,000/day
  - Silver: 80/min, 50,000/day
  - Unlimited: 240/min
  - Tier upgrades are **handled privately** (email `contact@openstates.org`) — there is no self-serve upgrade. **This is the operational crux:** the free default of 500 req/day is *fine for BYOK on-demand single-bill/single-state lookups*, but **cannot back a CI pipeline that refreshes even one large state's recent bills daily** (a big state introduces thousands of bills/session; enriching each costs multiple GETs). A CI pilot needs at least a Bronze/Silver operator key obtained by emailing OpenStates — an operator action, flagged for the captain.

**Bulk data** ([open.pluralpolicy.com/data](https://open.pluralpolicy.com/data/)):
- CSV **and** JSON (JSON includes full bill text), organized **per legislative session**; plus a full PostgreSQL dump and legislator YAML/CSV.
- **License: CC-0 public-domain dedication**, attribution appreciated but not required — clears the republish constraint outright.
- **Freshness: bill/vote bulk files refresh *monthly*.** (The Postgres dump and nightly legislator CSVs are fresher — a day or two behind — but the per-session bill/vote CSV/JSON is the monthly one.) **A month-stale bill status is exactly the wrong/stale-data class the north star ranks above all feature work**, so the bulk download is usable for a *backfill/seed* but **must not be the live path**.

**Data volume (the reason it can't be all-50 static):**
- Large states each introduce thousands of bills per two-year session (CA/NY/TX are in the many-thousands range); nationally, state legislatures introduce well over 100,000 bills per year combined — **1–2 orders of magnitude more than the ~10–15k federal bills the entire current pipeline handles**. Publishing full-text for all 50 states as static GitHub-Pages JSON is not viable; a **recency-windowed, pilot-state artifact** is.

### LegiScan — ❌ disqualified for static publishing (viable only as BYOK-only, not recommended)
- Comparable 50-state aggregator with a keyed API (generous free monthly quota) and near-live freshness.
- **But its terms restrict redistribution/republishing of the data**, which directly conflicts with the app's core pattern of committing fetched data into `docs/data/*.json` served by GitHub Pages. It could in principle back a *BYOK-only* path (user's own key, data never republished), but adopting a second aggregator with a worse license and no static-publish path — when OpenStates already covers BYOK *and* static — adds integration surface for no gain. **Rejected.**

### Direct state legislature APIs (2–3 pilot comparison) — ⚠️ freshness benchmark, not the integration target
- A handful of states run first-party APIs (e.g. New York's official Open Legislation API is keyed and well-documented); others expose only inconsistent feeds or nothing machine-readable — which is precisely *why OpenStates exists* (it runs and maintains scrapers for every state).
- **Value as a comparison point:** for a pilot state that has a first-party API, we can spot-check OpenStates freshness/accuracy against the authoritative source. **Value as the integration target: poor** — every state is a bespoke schema and parser, no uniform contract, and maintenance scales with state count. Use direct APIs to *validate* OpenStates on pilot states, not to *replace* it.

---

## Recommended architecture (mirrors the federal pipeline)

1. **Source = OpenStates API v3** (not bulk) for the freshness-sensitive bill/vote path; optionally seed a backfill from the CC-0 bulk dump.
2. **Pilot 2–3 states first** (suggest a large + a mid + a small state to stress-test volume, e.g. CA / CO / VT — captain to confirm). Prove the volume/freshness/cost numbers on real data before widening.
3. **Recency window**, exactly like the federal `RECENT_DAYS` cutoff: publish only recently-acted-on state bills, so the static artifact stays bounded regardless of a state's total session volume.
4. **Static publish** to `docs/data/states/<abbr>_bills.json` following the existing manifest/index contract; the app consumes it read-only via Retrofit like the federal bills. **On-demand BYOK** (`:feature:datasources`, user's own OpenStates key) covers anything outside the published window.
5. **Freshness guard** extends `check_freshness.py` / `CheckFreshness.kt` with a per-state-artifact staleness threshold — a state we can't keep fresh gets *omitted*, never shipped stale (same omit-rather-than-guess rule the election-calendar and registration surfaces already use).
6. **Wire model** — evaluate reusing the existing `Bill` shape vs. a dedicated `StateBill` (state bills carry chamber/session/jurisdiction fields the federal `Bill` lacks); decision belongs in the first implementation ticket, not this spike.

---

## Follow-up implementation tickets (sketched — **not filed**, captain-gated)

Per the epic-first rule for large expansions, these are titles + one-liners for the captain to approve before filing:

1. **Operator: obtain a Bronze/Silver OpenStates API key** — email `contact@openstates.org`; the 500/day default tier can't back CI pilot refresh. (Operator action, blocks the pipeline ticket.)
2. **Pipeline: OpenStates client + pilot-state bill fetch** — add a keyed `OpenStatesClient` (mirroring `CongressClient`) and a `fetch-state-bills --state <abbr>` step with a recency-window cutoff; Python-canonical first, KMP parity shadow after, matching the established two-pipeline pattern.
3. **Contract: `StateBill` wire model (or `Bill` extension)** — decide reuse-vs-new in `pipeline:shared`; publish `docs/data/states/<abbr>_bills.json` + a states index.
4. **BYOK: OpenStates key in `:feature:datasources`** — second BYOK provider alongside Congress.gov; encrypted on-device, on-demand state-bill fetch.
5. **Freshness: per-state-artifact staleness check** — extend both freshness shadows; omit-when-stale.
6. **App: state-bills surface** — pilot-state bills list + detail reusing the existing bill UI, gated dormant-until-populated (the same dormant-ahead-of-data pattern used throughout this run).

---

## Decision summary

- **Adopt OpenStates**; its CC-0 license is the single fact that makes state-data republishing legal and clears the constraint LegiScan fails.
- **Feed from the live API, not the monthly bulk download** — the bulk path is a month stale and violates the wrong/stale-data rule for the bill/vote surface.
- **Phase it: pilot states, recency-windowed, API-fed static publish + BYOK on-demand** — never an all-50 full-text static dump (volume is 1–2 orders of magnitude over federal).
- **Operator dependency:** a non-default OpenStates tier (email request) is required before a CI pilot refresh; the free 500/day tier only supports BYOK on-demand.
- **This is captain scope.** Nothing is filed; the captain approves the source + phasing, then tickets 1–6 get filed.

## Sources

- [Open States API v3 Overview — docs.openstates.org](https://docs.openstates.org/api-v3/)
- [Open States API v3 & 2021 Plans (tiers/keys) — blog.openstates.org](https://blog.openstates.org/open-states-api-v3/)
- [Rate limit and tiers — openstates/issues Discussion #205](https://github.com/openstates/issues/discussions/205)
- [Open States Bulk Data (CSV/JSON, CC-0, monthly) — open.pluralpolicy.com/data](https://open.pluralpolicy.com/data/)
- [Open States Legislator Bulk Data — open.pluralpolicy.com/data/legislator-csv](https://open.pluralpolicy.com/data/legislator-csv/)
- [Legislative Data Report Card (coverage) — open.pluralpolicy.com/reportcard](https://open.pluralpolicy.com/reportcard/)
- [US Legislators by Plural (public-domain dedication) — OpenSanctions](https://www.opensanctions.org/datasets/us_plural_legislators/)
