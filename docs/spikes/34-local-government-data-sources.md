# Spike #34 — Data sources for local-government (city/county) coverage

- **Issue:** [#34 — Spike: evaluate data sources for local-government (city/county) coverage](https://github.com/nukeforum/bill-summarizer/issues/34)
- **Epic:** [#17 — subnational accountability](https://github.com/nukeforum/bill-summarizer/issues/17) (local half; scoped out of epic #17's #26 work, deferred here per that ticket's "needs its own source evaluation later")
- **Pillars:** 1 *see the record* + 2 *decide their vote*, at the **local** (city/county) level
- **Status:** Recommendation — **not feasible yet as a published product surface; defer.** The only viable near-term slice is a narrow **BYOK-only, on-demand Legistar lookup** for the subset of large jurisdictions that run Granicus Legistar — and even that is optional and captain-gated. Concrete "revisit when X" triggers are given below.
- **Sequenced after #26** ([state-legislature spike](26-state-legislature-data-sources.md)), whose methodology carries over directly: the four hard constraints are **license / republish**, **BYOK fit**, **freshness**, and **coverage + static-publish volume**. The difference is that at the local level there is **no OpenStates-equivalent aggregator**, and that single fact flips the recommendation from "adopt + phase" (state) to "defer" (local).
- **Deliverable note:** This is the in-repo form of the spike's written proposal. The issue asks for it as a comment on epic #17; posting to GitHub is an outward action left to the operator/captain. This document is the reviewable artifact behind that post. Nothing is filed.

---

## TL;DR / recommendation

**Defer local-government coverage as a published surface.** Unlike the state level — where OpenStates provides one CC-0-licensed, BYOK-shaped, uniform-schema, 50-state API — the municipal level has **no aggregated source at all**, and the structural facts make a fresh, correct, comprehensive static feed infeasible today:

1. **Fragmentation is the killer.** The 2022 Census of Governments counts **90,837 local governments** — 3,031 counties, 35,705 municipal + township governments, plus ~39,555 special-purpose districts and 12,546 school districts. This is **~4 orders of magnitude more legislative bodies than the one Congress the whole app currently tracks**, and there is no single API or schema across them.
2. **The one real API (Legistar/Granicus) covers only the paying large jurisdictions.** Granicus Legistar exposes a per-client REST/OData Web API (`https://webapi.legistar.com/v1/{Client}/matters`), but only cities/counties that *buy* Granicus Legistar have it — the large-metro subset (hundreds to low-thousands), not the tens of thousands of small municipalities where most local government actually happens. Coverage is inherently top-heavy and non-comprehensive.
3. **No uniform license, and it's per-client.** Legistar read access returns only items "marked as public," and **some clients require API tokens** decided per-jurisdiction — there is no single terms-of-use grant that makes republishing all clients' data into our `docs/data/*.json` legal, the way OpenStates' CC-0 dedication does for states. This is the same republish blocker that disqualified LegiScan in #26, but worse (heterogeneous, per-client).
4. **The data isn't bill-shaped.** Legistar exposes "matters," agenda items, and events — not the clean introduced→committee→floor-vote→outcome lifecycle the app's `Bill`/`Outcome` model encodes. Local legislative process varies wildly (ordinances, resolutions, consent agendas, board actions), so even where an API exists, mapping it into a faithful, non-misleading record is bespoke per jurisdiction.

**The wrong/stale-data north star ranks correctness above all feature work.** A local surface that is comprehensive-looking but actually covers only ~1% of jurisdictions, with a data model that flattens heterogeneous local process into a federal-shaped bill card, would present a *misleadingly partial and potentially wrong* record — exactly what the north star forbids. So the responsible spike outcome (an explicitly permitted one per the AC) is **"not feasible yet, revisit when X."**

---

## The four constraints, scored

| Constraint | Legistar/Granicus API | Councilmatic / OCD scrapers | Census of Governments | Direct city/county feeds |
|---|---|---|---|---|
| **License / republish** | Per-client, some token-gated; no uniform grant ❌ | Scraped from Legistar; inherits the same per-source ambiguity ❌ | Public-domain (US Gov) ✅ | Per-jurisdiction; mostly public record ⚠️ |
| **BYOK in-app fit** | Per-client name in URL; token *sometimes* ⚠️ (BYO client name, not a personal key) | N/A (build-time scraper stack) ❌ | N/A (not an API) | Heterogeneous; most have no API ❌ |
| **Freshness** | Live for Legistar clients ✅ | As fresh as each instance's cron; many instances abandoned ❌ | 5-year census; **not legislation at all** ❌ | Varies; often none machine-readable ⚠️ |
| **Coverage** | Only Granicus-paying large jurisdictions (top-heavy, non-comprehensive) ❌ | A handful of manually-stood-up cities ❌ | *Counts/roster* of all 90k+ units, **no bills** ⚠️ | One jurisdiction = one bespoke parser ❌ |
| **Data model fit** | "Matters"/agenda items, not bill-lifecycle ⚠️ | OCD standard (bills/events/people) ✅ but per-instance ⚠️ | Government-unit directory only ❌ | Wildly heterogeneous ❌ |

**Net:** every column fails at least one hard constraint. There is **no municipal analog to OpenStates** — no source that is simultaneously aggregated, uniformly licensed for republish, BYOK-shaped, fresh, and broad. That absence, not any single fixable gap, is why the recommendation is *defer*.

---

## Source-by-source detail

### Legistar / Granicus Web API — ⚠️ the only structured option, but coverage + license disqualify it as a *published* source

- **What it is:** Granicus Legistar (the dominant legislative-management SaaS for large US local governments) exposes a per-client Web API over HTTPS/OData at `https://webapi.legistar.com/v1/{Client}/…` — endpoints for `matters` (legislation/agenda items), `events` (meetings), `votes`, and related records. NYC, for example, opened public read access to its Legistar data via this API.
- **Coverage:** only jurisdictions that license Granicus Legistar. That is the big-city / large-county tier — real but top-heavy, and a rounding error against the 90k+ total local bodies. **The app cannot claim "local coverage" from a source that structurally omits ~99% of local governments.**
- **License:** read GETs return only items "marked as public and available on InSite," and **some clients require API tokens** per their own policy. There is *no single license* under which we could republish all clients' matters into our static artifacts — republish legality would have to be assessed jurisdiction-by-jurisdiction. This is the #26 LegiScan republish blocker, multiplied per client.
- **Data model:** "matters" and agenda items, not a clean bill-lifecycle with a passage outcome. Mapping local process (ordinances, resolutions, consent calendars, board votes) into the federal-shaped `Bill`/`Outcome` model is bespoke and risks a misleading record.
- **Where it *could* fit:** a **BYOK-only, on-demand, never-republished** path — see the optional narrow slice below. That sidesteps the republish-license problem entirely (same reasoning that made LegiScan viable-only-as-BYOK in #26) but leaves coverage limited to the user's own Legistar city and does no static publish.

### Councilmatic / DataMade Open Civic Data scrapers — ❌ proves the pattern, not a maintained feed

- **What it is:** DataMade's [Councilmatic](https://github.com/datamade/councilmatic-starter-template) stack uses `python-legistar-scraper` + the `pupa` import framework to pull Legistar sites into the [Open Civic Data (OCD)](https://opencivicdata.org/) standard (bills/events/people/organizations), fronted by a `django-councilmatic` app. Instances exist for Chicago, NYC, LA Metro, Philadelphia, Oakland, Sacramento, etc.
- **Why it doesn't solve the spike:** each instance is a **manually stood-up, separately-hosted, per-city deployment**, not a single aggregated feed we can consume. It is a *pattern* (scrape Legistar → OCD), not a *source*. Many community instances are stale or abandoned (the freshness constraint fails per-instance), coverage is a handful of cities, and it inherits the same per-source Legistar license ambiguity. Its lasting value is the **OCD data model** as prior art if local work is ever undertaken — the same OCD lineage OpenStates itself grew from.

### US Census Bureau — Census of Governments — ⚠️ a roster, not legislation

- **What it is:** the authoritative, public-domain [Census of Governments](https://www.census.gov/programs-surveys/cog.html) — an every-5-years directory of all **90,837** local government units (names, types, FIPS/GEOIDs, counts by state; Illinois 6,930, Texas 5,533, etc.).
- **Why it's not a bill source:** it enumerates *which governments exist*, not *what they legislate*. It has **zero** agenda/bill/vote content, and refreshes only every 5 years.
- **Where it *could* fit:** purely as a **denominator / roster** for scoping ("how many bodies would a metro pilot cover?") or for a future jurisdiction-selection UX — never as the accountability data itself.

### Direct city/county feeds — ❌ no uniform contract

- Some large jurisdictions publish first-party open-data portals or ICS/RSS agenda feeds; most publish nothing machine-readable, or only PDFs. Every one is a bespoke schema and parser, with maintenance scaling by jurisdiction count. This is *why* even the state level needed OpenStates to run scrapers per state — and at the local level nobody plays that aggregating role. Useful only to spot-check a specific pilot city, not as an integration target.

---

## Optional narrow slice (only if the captain wants *any* local surface now): BYOK-only Legistar lookup

If a minimal local capability is desired before a real aggregator exists, the **only** slice that clears the constraints is a **BYOK-only, on-demand, never-republished** Legistar reader:

- The user enters their jurisdiction's **Legistar client name** (e.g. `nyc`) in Settings → Data sources (some clients also need a token, which the user supplies — the same encrypted-on-device model as the Congress.gov key).
- `:feature:datasources` fetches that client's recent `matters`/`events` on demand and renders them in a **clearly-labeled, jurisdiction-specific, "agenda items" surface** — *not* folded into the federal bills list, and *not* dressed up in the `Bill`/`Outcome` lifecycle it doesn't fit.
- **Nothing is published to `docs/data/`**, so the per-client republish-license problem never arises (identical to #26's BYOK-only escape hatch for LegiScan).
- **Explicitly scoped as a power-user feature for the ~large-metro Legistar subset**, with honest copy that it covers only Legistar jurisdictions — never presented as comprehensive local coverage.

This is **optional and not recommended as a priority**: it serves a small user slice, adds a heterogeneous data model, and does not advance the "and local" pillar for most users. It is documented only so the captain has the full option space.

---

## Revisit-when-X triggers (the conditions that would flip this to feasible)

Per the AC, deferral is a valid outcome *if* it's evidence-backed with revisit conditions. Reopen this spike when **any** of:

1. **An OpenStates-equivalent municipal aggregator emerges** — a single API, uniformly licensed for republish (CC-0/CC-BY), covering a meaningful breadth of jurisdictions in one schema. (None exists as of 2026-07; the closest lineage is OCD/Councilmatic, which never became a hosted aggregated feed.)
2. **The captain commits to a curated pilot-metro model** — an explicit, small, named list of large Legistar jurisdictions (e.g. the user's own top-N metros), accepting non-comprehensive coverage as a stated product decision. This turns the coverage failure into an *intentional scope*, at which point the BYOK-only slice above (or a per-pilot republish-license review) becomes actionable.
3. **Granicus/Legistar (or a successor) publishes a uniform public-republish license** across clients — removing the per-client republish blocker and enabling a static-publish path for the Legistar subset.

Until one of these holds, local coverage should stay **tracked-but-deferred** on epic #17 so the "and local" pillar is not silently dropped — which is exactly what this ticket set out to ensure.

---

## Decision summary

- **Defer.** There is no municipal analog to OpenStates: no aggregated, uniformly-licensed, BYOK-shaped, fresh, broad source exists. This is a structural absence, not a fixable gap.
- **Fragmentation + coverage is the disqualifier:** 90,837 local governments, and the one real API (Legistar) covers only the Granicus-paying large-jurisdiction subset (~1% of bodies), per-client-licensed, non-bill-shaped.
- **The north star forbids the alternative:** a comprehensive-*looking* surface built on a ~1%-coverage, model-mismatched source would present a misleading/partial-as-complete record.
- **Only viable near-term slice (optional, not prioritized):** BYOK-only, on-demand, never-republished Legistar lookup for the user's own Legistar jurisdiction — sidesteps the republish-license problem, serves a narrow power-user slice, honestly labeled.
- **Keep it tracked on epic #17** with the three explicit revisit-when-X triggers, so the "and local" pillar is deferred deliberately, not dropped.
- **This is captain scope.** Nothing is filed. The captain decides between (a) leave deferred with the revisit triggers, or (b) approve the narrow BYOK-only Legistar slice as an optional power-user feature.

## Sources

- [2022 Census of Governments, Organization Tables — census.gov](https://www.census.gov/data/tables/2022/econ/gus/2022-governments.html)
- [The Number and Types of Local Governments in the U.S. — St. Louis Fed (90,837 total; 3,031 counties; 35,705 municipal/township)](https://www.stlouisfed.org/publications/regional-economist/2024/march/local-governments-us-number-type)
- [Census of Governments program — census.gov](https://www.census.gov/programs-surveys/cog.html)
- [Legistar Web API — Granicus (webapi.legistar.com)](https://webapi.legistar.com/)
- [Legistar Web API — Granicus Support (public read = public items; per-client tokens)](https://support.granicus.com/s/article/Legistar-Web-API?language=en_US)
- [Legistar Web API Examples — matters/events endpoints](https://webapi.legistar.com/Home/Examples)
- [NYC Council Legislative API (Legistar public read access) — council.nyc.gov](https://council.nyc.gov/news/2017/11/17/api/)
- [Councilmatic starter template — DataMade (python-legistar-scraper + Open Civic Data)](https://github.com/datamade/councilmatic-starter-template)
- [Open Civic Data standard — opencivicdata.org](https://opencivicdata.org/)
