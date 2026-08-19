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

## Security

### The Congress.gov API key goes in the `X-Api-Key` header — never the query string

Congress.gov accepts an `api_key` query parameter too, but neither the
app nor `pipeline/` may use it: Ktor's timeout exceptions interpolate the
request URL into their message, and on the BYOK path those messages reach
both Crashlytics and the Data sources screen. `CongressClient` (shared
with the CLI) and `ByokKeyValidator` authenticate with
`CongressClient.API_KEY_HEADER`, and `SecretRedaction.kt` scrubs the
egress paths as a second line of defence — the reasoning for each piece
lives in those files' KDoc. `ApiKeyLeakTest` and
`CongressClientTest.get_never_puts_the_api_key_in_the_url` lock it down.

The Python pipeline (`data-pipeline/scripts/_common.py`) still uses the
query parameter: that path is CI-only, runs with a repo secret rather
than a user's key, and has no crash reporting. Don't copy its shape into
app or `pipeline/` code.
