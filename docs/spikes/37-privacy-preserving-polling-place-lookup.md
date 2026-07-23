# Spike #37 — Privacy-preserving polling-place lookup options

- **Issue:** [#37 — Spike: privacy-preserving polling-place lookup options](https://github.com/nukeforum/bill-summarizer/issues/37)
- **Epic:** [#16 — Election logistics — dates, registration, polling info](https://github.com/nukeforum/bill-summarizer/issues/16)
- **Pillar:** 3 — *cast their vote* (the "where do I vote?" half)
- **Status:** Recommendation — *deep-links win*; no captain-gated scope requested.
- **Deliverable note:** This is the in-repo form of the spike's written recommendation. The
  issue asks for it as a comment on epic #16; posting to GitHub is an outward action left to the
  operator/captain. This document is the reviewable artifact behind that post.

---

## TL;DR / recommendation

**Keep the app PII-free and stay with deep-links. Do not publish, bundle, or query
address-level polling-place data of any kind.** Epic #16's prose deferral was the right call — this
spike now records it as an *evaluated* decision rather than an assumption.

The one concrete, low-risk upgrade worth folding into the existing #25 link surface:

> Add a **curated per-state official "find my polling place" URL** alongside the per-state
> registration URL, sourced from the authoritative
> [NASS *Can I Vote* directory](https://www.nass.org/can-i-vote/find-your-polling-place), with the
> same **omit-rather-than-guess** discipline #25 already uses. This is a zero-PII, zero-correctness-risk
> string table — the user still types their address into the *official state site*, never into ours.

Everything beyond that (static polling feeds, address-based APIs, bundled on-device data) is either
disqualified by the privacy posture or cannot be made *correct*, and correctness failures here damage
pillar 3 at full strength (wrong polling place = disenfranchised voter). None of the recommendation
requires captain review, because the winning option collects and transmits **no addresses**.

---

## Why "where do I vote?" is uniquely hazardous

Two structural facts make polling-place data the hardest election-logistics surface to serve
correctly, and both cut against any pipeline-published or bundled artifact:

1. **It is address-level, not state-level.** Unlike an election *date* (one nationwide row) or a
   registration *deadline* (one row per state, ~56 rows), a polling assignment is keyed on the
   voter's specific residential address — millions of precinct/split-precinct mappings that change
   between elections (consolidations, vote-center conversions, redistricting).
2. **It is finalized late.** Assignments are frequently not locked until days-to-weeks before an
   election. Any artifact we publish on a pipeline cadence is stale by construction near the moment
   it matters most.

The project's own **wrong-data corollary** (a confirmed wrong-or-stale-data defect jumps to backlog
#1; never ship one) therefore applies with full force: a wrong polling place is worse than no polling
place. That single constraint is what eliminates options 2–4 below.

---

## Options evaluated (the four spike questions)

### Q1 — Curated per-state official lookup links ✅ *recommended*

**Verdict: viable, and a real-if-modest upgrade over the generic vote.gov hand-off.**

- Nearly every state hosts an official "Find My Polling Place" page (e.g. CA SoS, WI `myvote`,
  MD voter services, RI SoS). The **NASS *Can I Vote*** directory aggregates these authoritative
  per-state links and is the natural curation source — the polling-place analogue of the vote.gov
  registration pages #25 already uses.
- **Privacy model is identical to #25:** we ship a static `state → URL` string map; the user opens
  the official state page and enters their address *there*, on the government site. No address ever
  touches our app, our pipeline, or any backend we control. Zero PII, no `<queries>`/manifest change
  (reuses `openInCustomTab`).
- **Correctness risk is near-zero and self-limiting:** a curated URL is either right or 404s; it can
  never assert a *wrong polling place*. Unverified/absent states simply omit the link (same
  omit-rather-than-guess rule as the registration SEED), degrading to the existing vote.gov hand-off.
- **How much does it beat vote.gov?** Marginally but genuinely. #25's `vote.gov/register/{state}`
  pages center on *registration* (and, per iter-23, check-registration + polling links *where
  official*), while USA.gov/vote.gov route "where do I vote" back to state/local offices. A direct
  per-state *polling-lookup* deep-link is one fewer hop to the exact tool for the exact question, and
  is the honest, in-scope way to serve the *where* half of pillar 3.

**Recommended implementation shape (follow-up ticket, not this spike):** a
`pollingPlaceUrl(stateCode)` helper beside `VoteGovLinks.registrationUrl` in `feature:calendar`,
backed by an operator-verified NASS-sourced map (populated incrementally like the registration SEED),
surfaced next to the existing "Register & vote" affordance on the elections surface. This is
**auto-approved scope** — no address handling.

### Q2 — Static data feeds (Voting Information Project / civic-data programs) ❌

**Verdict: cannot be made correct as a pipeline-published static artifact; reject.**

- The **Voting Information Project (VIP)** is the most credible feed: government-sourced, VIP-Spec
  v6.0, historical polling data released **CC BY 4.0** (licensing is *not* the blocker). VIP-approved
  data is published through the Google Civic Information API.
- But VIP is *address-level and election-cycle-scoped*: it is collected, reviewed, and published in
  the run-up to a specific election and reflects assignments finalized shortly before it. Publishing
  it as a static `docs/data/*.json` artifact on our cadence reintroduces exactly the staleness the
  wrong-data corollary forbids — and the *volume* (per-precinct across all states) dwarfs anything in
  the current data contract.
- The historical VIP/CC-BY datasets are *past* election locations — useful for research, actively
  misleading if shown as "where you vote *now*."
- **Conclusion:** even with clean licensing and a real source, no pipeline-published static polling
  artifact can satisfy correctness. Rejected on correctness + volume, not licensing.

### Q3 — Address-based lookup APIs ❌ (disqualified for default posture; opt-in would be captain scope)

**Verdict: conflicts head-on with the no-backend / no-PII posture. Disqualified as a default; an
opt-in variant is a captain decision, and this spike does not request one.**

- The canonical option was Google's **Civic Information API `voterInfoQuery`** (VIP-backed polling
  locations by address). Note the API is contracting: Google **turned down the Representatives
  endpoint on 30 April 2025**, leaving the elections/`voterInfoQuery` surface as the remaining civic
  endpoint — but it is exactly the class in question: it takes the user's **residential address** and
  sends it to a third party.
- Vote.org's polling-place locator is the same shape (nonprofit-hosted, address-in).
- **Any** networked address→polling query — plain, BYOK-keyed, or proxied — transmits the user's home
  address off-device. BYOK does not fix this: the privacy issue is the *destination of the address*,
  not who owns the key. A fully on-device query is impossible because the assignment logic lives in
  the remote dataset (see Q4).
- This is the branch the issue flags as reserved: *if the recommendation were anything beyond curated
  deep-links, park for captain review.* The recommendation deliberately is **not** this, so nothing
  is escalated. If the captain ever wants a "where do I vote" one-tap experience, the only
  posture-consistent framing is an explicit, off-by-default, clearly-labeled opt-in hand-off to an
  official state lookup — which is functionally Q1 with a pre-filled address and strictly worse on
  privacy than letting the user type it into the state site themselves.

### Q4 — Bundled on-device data ❌ (confirmed infeasible, as expected)

**Verdict: infeasible at address granularity. Confirmed and documented.**

- Correct polling-place resolution requires an address→precinct→location mapping for the whole
  country. That is a multi-hundred-MB-to-GB dataset (precinct geometries + split-precinct rules +
  location tables), incompatible with a lightweight privacy app that ships a single quarterly ZIP
  crosswalk asset.
- Even if size were acceptable, it is stale within weeks of publish (Q2's late-finalization problem),
  so it would routinely assert wrong locations — the worst pillar-3 outcome.
- Matches epic #16's prose expectation. Confirmed infeasible.

---

## Decision summary

| Option | PII off-device? | Can be *correct*? | Verdict |
|---|---|---|---|
| Q1 Curated per-state official deep-links | **No** (user→official site) | Yes (or omit) | ✅ **Adopt** — fold into #25's link table |
| Q2 Static VIP / civic feed artifact | No (build-time only) | **No** (address-level, late-finalized, stale) | ❌ Reject on correctness + volume |
| Q3 Address-based API (Google Civic, Vote.org, BYOK) | **Yes** (address leaves device) | Yes | ❌ Disqualified by privacy posture; opt-in = captain scope (not requested) |
| Q4 Bundled on-device dataset | Yes | **No** (size + staleness) | ❌ Infeasible (confirmed) |

**Net:** epic #16's deferral stands, now *evaluated*. The actionable, in-scope win is a curated
per-state official polling-lookup deep-link folded into #25's surface — zero PII, zero correctness
risk, omit-on-unverified.

## Recommended next steps

1. **Record the decision on epic #16** (operator/captain action): replace the prose "deferred"
   note with "evaluated — deep-links only; see this spike," linking here. Close #37 as *resolved:
   recommendation accepted*.
2. **Optional follow-up ticket (auto-approvable):** add `pollingPlaceUrl(stateCode)` beside
   `VoteGovLinks.registrationUrl` in `feature:calendar`, backed by an operator-verified,
   NASS-sourced per-state map (populated incrementally like the registration SEED, omit-when-unverified),
   surfaced next to the "Register & vote" affordance. No address handling → no captain gate.
3. **Nothing parked for captain.** The recommendation collects and transmits no addresses, so it
   stays inside the autonomous-pipeline privacy envelope.

## Sources

- [Civic Information API — Google for Developers](https://developers.google.com/civic-information)
- [Notice of Turndown of the Representatives API (30 Apr 2025) — Google Civic Info API group](https://groups.google.com/g/google-civicinfo-api/c/9fwFn-dhktA)
- [Elections: voterInfoQuery — Civic Information API](https://developers.google.com/civic-information/docs/v2/elections/voterInfoQuery)
- [The Voting Information Project — About / Spec / FAQ](https://www.votinginfoproject.org/)
- [PublicI/us-polling-places — historical polling places, CC BY 4.0](https://github.com/PublicI/us-polling-places)
- [NASS — Find Your Polling Place (per-state directory)](https://www.nass.org/can-i-vote/find-your-polling-place)
- [USA.gov — Find your polling place](https://www.usa.gov/find-polling-place)
- [Vote.org — Polling Place Locator (third-party, address-in)](https://www.vote.org/polling-place-locator/)
