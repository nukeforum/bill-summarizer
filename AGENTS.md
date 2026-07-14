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
`pluginManagement { plugins { ... } }` (read via `val foojayResolverVersion:
String by settings`) and applies the plugin version-less in its `plugins {}`
block. Version catalogs (`libs.versions.toml`) cannot supply settings-plugin
versions, which is why a Gradle property is used instead.

Two sharp edges, verified on Gradle 9.5:

- Included builds do **not** inherit the root build's `gradle.properties`, so
  `build-logic/settings.gradle.kts` loads `../gradle.properties` (the owning
  android build's file) explicitly via `java.util.Properties` to stay
  single-sourced.
- `pipeline/` is a separate standalone Gradle build (own `gradlew`; also
  consumed by `android/` via `includeBuild("../pipeline")`), so it cannot
  share android's property file idiomatically — its `gradle.properties`
  carries a second copy of the property. Keep the two values in sync.
