# Project agent memory

This file is the project's committed home for project-intrinsic agent knowledge: build, test, release, architecture, and sharp-edge notes that should travel with the code.

- Add durable project-specific notes here as they are discovered through real work.

## Build

### Foojay toolchain-resolver version (settings plugin)

The `org.gradle.toolchains.foojay-resolver-convention` settings plugin is
applied in three settings files but its version is centralized in the
`foojayResolverVersion` Gradle property. **To bump it, edit two lines:**

- `android/gradle.properties` — covers `android/settings.gradle.kts` **and**
  `android/build-logic/settings.gradle.kts`.
- `pipeline/gradle.properties` — covers `pipeline/settings.gradle.kts`.

Mechanism: each settings file declares the version in
`pluginManagement { plugins { ... } }` and applies the plugin version-less in
its `plugins {}` block. `android/settings.gradle.kts` and
`pipeline/settings.gradle.kts` read the property via `val
foojayResolverVersion: String by settings`; `build-logic/settings.gradle.kts`
loads it explicitly instead (first sharp edge below). Version catalogs
(`libs.versions.toml`) cannot supply settings-plugin versions, which is why a
Gradle property is used instead.

Two sharp edges, verified on Gradle 9.5:

- Included builds do **not** inherit the root build's `gradle.properties`, so
  `build-logic/settings.gradle.kts` loads `../gradle.properties` (the owning
  android build's file) explicitly via `java.util.Properties` to stay
  single-sourced.
- `pipeline/` is a separate standalone Gradle build (own `gradlew`; also
  consumed by `android/` via `includeBuild("../pipeline")`), so it cannot
  share android's property file idiomatically — its `gradle.properties`
  carries a second copy of the property. Keep the two values in sync.

## Test

### `boundsInRoot()` on a clipped-out node returns `Rect.Zero`, not its real off-screen position

In a Robolectric-hosted Compose UI test, a `SemanticsNode` positioned outside
its scrollable ancestor's current viewport (e.g. below the fold of a
`Modifier.verticalScroll` `Column`) reports `boundsInRoot() == Rect.Zero`, not
its actual (larger) layout coordinates. This is because the scrollable
clips its content to its own bounds, and a fully-clipped node's window
bounds resolve to empty. Robolectric's default compose-test viewport is also
small and fixed (320×470dp) unless the content is wrapped in an explicitly
sized `Box`, making this easy to hit incidentally.

Two consequences when writing render tests for tall/scrollable screens:

- **Don't assert section order via `boundsInRoot()` comparisons** on content
  that may be off-screen — a below-the-fold node can silently compare as
  `(0, 0)` and produce a false pass or a confusing failure. Compare
  semantics-tree traversal order instead (first-encountered index of each
  node's merged text in a depth-first walk from `onRoot().fetchSemanticsNode()`),
  which reflects layout order regardless of clipping.
- **`performScrollTo()` scrolls only the minimum distance** needed to bring a
  node fully into view — it does not scroll to the end of the scrollable
  range. A geometry-based occlusion assertion (e.g. "does this floating
  button ever cover the last line?") needs the *true* max scroll position:
  drive the scrollable's own `SemanticsActions.ScrollBy` with a
  large delta instead (`onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy))
  .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 1_000_000f) }`).

Also: `ExtendedFloatingActionButton` merges its descendants' semantics (it is
a `Button` under the hood), so finding its label text requires
`onNodeWithText(..., useUnmergedTree = true)`.

## Data pipeline (Python canonical ↔ Kotlin shadow)

### One shared choke point decides the published bills-manifest bytes

Every writer that rewrites `docs/data/congressNNN_bills.json` — the bills
fetcher, the backfiller, the **votes** writer (it re-serializes every bill to
attach vote refs) and the shard builder — goes through
`FileBillsManifestStore.save` → `ManifestJson.encodeToString(BillsManifest…)`.
`BillsManifest.bills` is typed as
`List<@Serializable(with = BillManifestWriteSerializer::class) Bill>`, so that
one transform is the only place the published per-bill JSON shape is decided.
Fix a shape divergence there and every writer agrees; fix it anywhere narrower
and the votes path silently stamps the old shape back on (that is exactly how
issue #116 arose after #73 made `update-votes.yml` Kotlin-canonical).

**The write config keeps nulls explicit** (`explicitNulls = true`) because
Python genuinely emits `"short_title": null` and friends. The exceptions live in
`BillManifestWriteSerializer.OMIT_WHEN_NULL` and must be kept in lockstep with
the `del record[...]` loop at the end of Python's `_common.build_bill_record`.
Adding a nullable field to `Bill` without adding it to both lists reintroduces
the #74 / #116 class of divergence for every carried-forward bill.

### A green parity job proves nothing about parity

The `Compare canonical vs Kotlin shadow output` step in `update-bills.yml` /
`backfill-bills.yml` runs under `set +e`, and the Kotlin shadow step is
`continue-on-error: true`. The verdict exists only as text in the job summary.
Read the diff, never the job's colour — issue #116 hid behind ~27 consecutive
green runs.

To check parity **offline**, replay the carry-forward tail of a run (empty fresh
batch) on both sides over the published manifests and diff them:

- Python: `merge_records(strip_vote_refs(existing["bills"]), [])` →
  `attach_vote_refs` → `save_manifest`, with `_common.OUTPUT_DIR` and
  `_common.now_iso` monkeypatched to a temp dir and a fixed timestamp.
- Kotlin: `FileBillsManifestStore.load` → `mergeBillRecords(stripVoteRefs(…), [])`
  → `attachVoteRefs` → `save` with the same fixed `nowIso`, driven from a
  throwaway `src/jvmTest` class (`jvm()` picks the source set up automatically).
- Compare with CI's own normalisation: `jq -S '.bills'` then `diff -u`.

Both sides must run the **same** merge; skipping `mergeBillRecords` on the
Kotlin side reorders same-date bills and produces a large phantom diff.

### Known latent divergence: `subjects: []` on congresses 115–118

`Bill.subjects` defaults to `emptyList()` and `encodeDefaults = true`, so Kotlin
writes `"subjects": []` for a carried-forward bill that predates issue #28,
while Python's carry-forward dict simply has no key. It affects congresses
115–118 only; 113, 114 and 119 carry the key on every bill. It is invisible to
CI today because backfill's `active_congress` is 113 and `update-bills` only
rewrites 119 — but it will surface the moment one of those Congresses is
rewritten. The fix is to normalise those four files, **not** to drop empty
`subjects` on write: a freshly built record legitimately carries `[]` on both
sides.
