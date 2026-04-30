# Bill Summarizer

A Kotlin / Jetpack Compose Android app that lists recently voted-on U.S.
Congressional bills and lets the user hand any bill off to **ChatGPT, Claude,
or Gemini** for a plain-English summary. The app brings no LLM of its own —
it builds a structured prompt and shares it via Android's `ACTION_SEND`
system to whichever AI app the user prefers.

<p align="center"><img src="screenshots/list.png" alt="Bills list screen" width="320"></p>

The data is published as a static JSON manifest hosted on **GitHub Pages** —
no Congress.gov API key ships in the APK, no per-user backend is required,
and hosting costs $0/month.

---

## Architecture

```
┌─────────────────────┐     ┌──────────────────┐     ┌──────────────┐
│ GitHub Action       │     │ Static JSON      │     │ Android app  │
│ (daily cron)        │ ──▶ │ on GitHub Pages  │ ──▶ │ fetches JSON │
│ hits Congress.gov   │     │ (free hosting)   │     │              │
└─────────────────────┘     └──────────────────┘     └──────────────┘
```

- **`data-pipeline/`** — Python script + GitHub Action that calls the
  [Congress.gov v3 API](https://api.congress.gov/), filters for bills with
  passage-type actions in the last 60 days, enriches each one with sponsor /
  CRS summary / full-text URLs, and writes
  [`docs/data/bills.json`](docs/data/bills.json).
- **`docs/`** — served by GitHub Pages at
  `https://nukeforum.github.io/bill-summarizer/`.
- **`android/`** — Kotlin + Compose app, package `com.billsummarizer`.

The Android app does **not** call the Congress.gov API directly. Every user
pulls the same static JSON, which gives us natural CDN caching and keeps
secrets out of the APK.

---

## Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.3.21 |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM (single repository) |
| DI | Hilt (KSP, no kapt) |
| Networking | Retrofit 3 + OkHttp 5 + kotlinx-serialization-json |
| Async | Coroutines + Flow |
| Persistence | DataStore (Preferences) — no Room |
| Build | Gradle Kotlin DSL with `libs.versions.toml`, AGP 9.2 |
| Min SDK | 26 (Android 8) |
| Target SDK | 36 |
| CI | GitHub Actions (Node 24 native runners) |
| Strictness | `-Werror` on Kotlin + Java, `lint.warningsAsErrors = true` |

---

## Project layout

```
bill-summarizer/
├── android/                       # Android Studio project
│   ├── app/src/main/java/com/billsummarizer/
│   │   ├── data/
│   │   │   ├── api/               # Retrofit BillsApi + BillTextFetcher
│   │   │   ├── model/             # Bill, Sponsor, Action, Outcome…
│   │   │   └── repository/        # BillRepository (cache + DataStore)
│   │   ├── ui/
│   │   │   ├── billslist/         # BillsListScreen + ViewModel
│   │   │   ├── billdetail/        # BillDetailScreen + ViewModel + sheet
│   │   │   ├── components/        # BillCard, OutcomeChip
│   │   │   ├── theme/             # Material 3 + party colors
│   │   │   └── util/              # Format helpers, Custom Tabs
│   │   ├── share/                 # LlmTarget + LlmShareHelper
│   │   ├── di/                    # Hilt modules
│   │   └── MainActivity.kt
│   ├── app/build.gradle.kts
│   └── gradle/libs.versions.toml
├── data-pipeline/
│   ├── scripts/fetch_bills.py     # Pulls Congress.gov, writes bills.json
│   └── requirements.txt
├── docs/                          # served by GitHub Pages
│   ├── data/bills.json            # the file the app fetches
│   └── index.html
├── .github/workflows/
│   └── update-bills.yml           # daily cron
└── README.md
```

---

## Running the data pipeline

The pipeline is deployed; you don't normally need to run it locally. To do
so anyway:

```bash
cd data-pipeline
python -m venv .venv
source .venv/bin/activate    # or .venv/Scripts/activate on Windows
pip install -r requirements.txt
export CONGRESS_API_KEY=<your-key>     # https://api.congress.gov/sign-up/
python scripts/fetch_bills.py          # writes ../docs/data/bills.json
```

In CI, `update-bills.yml` runs daily at 06:00 UTC and on
`workflow_dispatch`. It commits a new `bills.json` only when the content
actually changed.

---

## Building the Android app

Open `android/` in Android Studio Hedgehog or newer, or from the command
line:

```bash
cd android
./gradlew :app:assembleDebug
./gradlew :app:installDebug    # with a device or emulator attached
```

The first build downloads Gradle 9.4.1 and the AGP 9.2.0 toolchain. The
build is strict — Kotlin warnings, Java warnings, and lint warnings are all
errors. Adding new dependencies will surface deprecation warnings as build
failures; either upgrade or `@Suppress` deliberately.

The app's manifest base URL points at
`https://nukeforum.github.io/bill-summarizer/`. To point a fork at your own
Pages site, edit `BILLS_BASE_URL` in
[`NetworkModule.kt`](android/app/src/main/java/com/billsummarizer/di/NetworkModule.kt).

---

## Milestones

1. **Data pipeline + GitHub Pages** — Python script, GitHub Action, static
   JSON manifest at `nukeforum.github.io/bill-summarizer/data/bills.json`.
2. **Android skeleton** — Hilt + Retrofit + DataStore, end-to-end fetch
   from MainActivity.
3. **Bills list screen** — Material 3 cards, filter chips, pull-to-refresh,
   loading/empty/error states.
4. **Bill detail screen** — collapsing top bar, status / sponsor / summary
   (HTML rendered via `AnnotatedString.fromHtml`) / full-text link via
   Custom Tabs.
5. **Summarize-with-AI** — FAB + bottom sheet, three LLM share buttons,
   "Include full text" toggle, generic "Other app…" chooser, browser
   fallback when the LLM app isn't installed.
6. **Polish** — Android 12+ splash screen, adaptive launcher icon,
   this README.

---

## Future work

- **Crash reporting.** Firebase Crashlytics would slot in here; deferred
  for MVP because it requires a Firebase project + `google-services.json`
  to enroll in the user's account.
- **Search across all bills.** The MVP intentionally only shows recent
  voted-on bills; a search index would need a different pipeline.
- **Member / vote-by-member data.** Out of scope for MVP per the original
  plan.
- **Tighter LLM prompt.** Real-world summaries can read as vague; the
  prompt template in
  [`LlmShareHelper`](android/app/src/main/java/com/billsummarizer/share/LlmShareHelper.kt)
  is a good lever for sharper output.

---

## Costs

Recurring spend is **$0**: Congress.gov API is free, GitHub Pages hosting
is free, GitHub Actions usage stays inside the public-repo free tier, and
the LLM call happens inside the user's chosen AI app. The only spend is a
one-time $25 Google Play Developer account fee at publish time.

---

## License

TBD — placeholder for project owner. Code in this repo is otherwise
unconditionally permissive.
