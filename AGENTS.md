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

`CongressClient` and `ByokKeyValidator` authenticate with the
`X-Api-Key` request header. Congress.gov accepts an `api_key` query
parameter too, and that is how this started — but **do not move it
back**. Ktor's timeout exceptions interpolate
`request.url.buildString()` into their message
(`Request timeout has expired [url=…, request_timeout=… ms]`, same for
connect and socket timeouts), and on the BYOK path those messages go
two places the key must never reach:

- `ByokFetchOrchestrator.runReported` → `CrashReporter.recordNonFatal`
  → `FirebaseCrashlytics.recordException`, which uploads the message.
  The daily `ByokFetchWorker` runs this in the background, so the user
  never sees the failure that leaked their credential.
- `DataSourcesViewModel` (`fetchMessage`) and
  `KeyValidationResult.Unreachable`, both rendered verbatim by
  `DataSourcesScreen` — the second one directly beneath the
  password-masked field that hides the key.

It also falsifies `docs/privacy.html`: *"Your key is sent only to
Congress.gov … never to us or any third party."*

`SecretRedaction.kt` (`redactSecret` / `redactThrowable`) is the second
line of defence, applied on all three egress paths. It scrubs the
stored key and, as a backstop, any `api_key=`/`key=` query value
regardless of whose it is. `redactThrowable` returns the *original*
instance when there is nothing to scrub, so ordinary failures keep
their real type and Crashlytics grouping; when it does fire it rebuilds
the chain as `RedactedThrowable` and deliberately does not attach the
original as a cause (that would re-upload the message just scrubbed).

`ApiKeyLeakTest` and `CongressClientTest.get_never_puts_the_api_key_in_the_url`
lock this down. Verified against the live API: a request with no key
returns `API_KEY_MISSING`, one with a bogus key in the header returns
`API_KEY_INVALID` — proof the header is read and parsed.

Note the Python pipeline (`data-pipeline/scripts/_common.py`) still uses
the query parameter. That path is CI-only, runs with a repo secret
rather than a user's key, and has no crash reporting — so it is not part
of this leak, but keep the distinction in mind before copying its shape.
