# Informed Citizen

A Kotlin / Jetpack Compose Android app that lists recently voted-on U.S.
Congressional bills and lets you hand any bill off to **ChatGPT, Claude,
or Gemini** for a plain-English summary. The app brings no cloud LLM of
its own — it builds a structured prompt and shares it via Android's
`ACTION_SEND` system to whichever AI app you prefer.

## Features

- **Bills feed.** Recently voted-on House and Senate bills with party,
  sponsor, latest action, and outcome chips.
- **Bill detail + LLM share.** One tap composes a prompt with the bill
  text (when available) and shares to ChatGPT, Claude, or Gemini.
- **Reps lookup.** Find your senators and representative by ZIP code,
  with a detail page that surfaces their recent bill activity.
- **Session calendar.** When is the House or Senate next in session?
  Tells you at a glance whether today is a likely floor-activity day.
- **On-device AI titles.** Optional Gemini Nano (Google AI Edge) bill
  title summaries that run entirely on-device. **Currently suppressed**
  by `FeatureFlags.AI_TITLES = false` in `core/model` — the full feature
  (state machine, Settings section, worker, UI) is built and tested but
  unreachable until the `com.informedcitizen` package is on Google's AI
  Edge allowlist. See `FeatureFlags.kt` for the flip-on procedure.

## Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.3.21 (Java 17) |
| UI | Jetpack Compose + Material 3 |
| Navigation | AndroidX Navigation 3 |
| Architecture | Multi-module (`:core:*` + `:feature:*`), MVVM per screen |
| DI | Hilt (KSP, no kapt) |
| Networking | Retrofit 3 + OkHttp 5 + kotlinx-serialization-json |
| Async | Coroutines + Flow |
| Persistence | SQLDelight + DataStore (Preferences) |
| Background work | WorkManager + Hilt-Work † |
| On-device AI | Google AI Edge AICore (Gemini Nano) † |
| Crash reporting | Firebase Crashlytics |
| Testing | JUnit 4, Robolectric, kotlinx-coroutines-test |
| Build | Gradle 9.5 Kotlin DSL with `libs.versions.toml`, AGP 9.2, convention plugins |
| Min SDK | 26 (Android 8) |
| Target / Compile SDK | 36 |
| Strictness | `-Werror` on Kotlin + Java, `lint.warningsAsErrors = true` |

† **Dormant behind `FeatureFlags.AI_TITLES`.** AICore is only invoked by
the on-device titles feature; WorkManager + Hilt-Work currently have no
other consumers in the app, so with the flag off neither is exercised
at runtime even though the wiring (HiltWorkerFactory, AICore SDK) is
compiled into the APK.

## Repo layout

```
android/              # Multi-module Gradle build (see below)
pipeline/             # Shared KMP pipeline + JVM CLI (in-progress port of data-pipeline/)
data-pipeline/        # Python pipeline that fetches Congress data — kept until KMP port replaces it
docs/                 # GitHub Pages landing site
play-listing/         # Play Store assets and copy
```

## Shared pipeline (`pipeline/`)

The data pipeline is being migrated from Python (`data-pipeline/`) to a
Kotlin Multiplatform module so the same fetch / parse / transform code
runs in **three** places: today's GitHub Actions data pipeline (via a
JVM CLI), the Android app (so users can run the pipeline with their own
API keys), and the future iOS app.

```
pipeline/
├── shared/           # KMP module — commonMain + jvmMain + iosArm64/SimulatorArm64/X64
└── cli/              # JVM-only fat-JAR entrypoint, used by GH Actions workflows
```

`pipeline/` is its own Gradle build. Android consumes `pipeline:shared`
via Gradle composite build (`includeBuild("../pipeline")` in
`android/settings.gradle.kts`); iOS will consume via XCFramework / SPM.
The Python pipeline in `data-pipeline/` stays operational until every
workflow has been migrated to the JAR.

## Android module layout

```
android/
├── app/                          # Entrypoint: MainActivity, MainNavigation, shell, settings host
├── build-logic/convention/       # Convention plugins (compile SDK, lint, Kotlin opts)
├── core/
│   ├── model/                    # Bill, Sponsor, Action, Outcome…
│   ├── network/                  # Retrofit + OkHttp wiring
│   ├── database/                 # SQLDelight schema + driver
│   ├── datastore/                # DataStore Preferences module
│   ├── crash/                    # Crashlytics wrapper
│   ├── ui/                       # Shared theme, components (BillCard, OutcomeChip…)
│   └── testing/                  # Shared test fixtures
└── feature/
    ├── bills/                    # BillsList + BillDetail + LlmShareHelper / LlmTarget
    ├── calendar/                 # House/Senate session calendar
    ├── reps/                     # Reps lookup + ZIP crosswalk
    └── ai-titles/                # Gemini Nano on-device title generation
```

## Building

Open `android/` in a current Android Studio (AGP 9.2 requires a recent
canary/beta), or build from the command line:

```bash
cd android
./gradlew :app:assembleDebug
./gradlew :app:installDebug    # with a device or emulator attached
```

The first build downloads Gradle 9.5.0 and the AGP 9.2.0 toolchain. The
build is strict — Kotlin warnings, Java warnings, and lint warnings are
all errors. New dependencies that surface deprecation warnings will fail
the build until you upgrade or `@Suppress` deliberately.

To run the shared pipeline tests or the CLI directly:

```bash
cd pipeline
./gradlew :shared:jvmTest      # KMP shared module unit tests
./gradlew :cli:run             # runs the placeholder CLI
```

Release builds expect signing credentials in Gradle properties
(`INFORMEDCITIZEN_KEYSTORE_PATH`, `_PASSWORD`, `_KEY_ALIAS`,
`_KEY_PASSWORD`) or the matching environment variables. Without them,
debug builds still work; only release bundling fails.

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for
the full text.
