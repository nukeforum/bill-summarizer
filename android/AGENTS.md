# Project agent memory

This file is the project's committed home for project-intrinsic agent knowledge: build, test, release, architecture, and sharp-edge notes that should travel with the code.

- Add durable project-specific notes here as they are discovered through real work.

## Release automation (fastlane)

fastlane lives here under `android/` (next to `gradlew`): `android/Gemfile`,
`android/fastlane/{Appfile,Fastfile,README.md}`. Run lanes from `android/`
with `bundle exec fastlane <lane>`. See `android/fastlane/README.md` for the
full lane table and secret setup.

- Lanes: `test` (`:app:testDebugUnitTest`), `build` (`:app:bundleRelease` →
  signed release **AAB**), and `deploy_internal` / `deploy_beta` /
  `deploy_production` (each `build`s then `upload_to_play_store` to that
  track). `deploy track:<track>` is the shared parameterized lane.
- Release AAB output: `app/build/outputs/bundle/release/app-release.aab`.
- Signing is owned by `app/build.gradle.kts`, NOT fastlane. It reads
  `INFORMEDCITIZEN_KEYSTORE_PATH` / `_KEYSTORE_PASSWORD` / `_KEY_ALIAS` /
  `_KEY_PASSWORD` from Gradle properties (`~/.gradle/gradle.properties`) or
  env vars. Missing values → release bundling fails, debug still works.
- Play uploads need a Google Play service-account JSON key. It is a secret,
  **not committed**, and not yet in the repo. Supply its path via
  `PLAY_STORE_JSON_KEY` (the `Appfile` reads it); fallback path
  `android/fastlane/play-store-service-account.json` is gitignored.
- `Gemfile.lock` is not committed yet (no Ruby toolchain when set up); run
  `bundle install` on a Ruby host and commit `android/Gemfile.lock` to pin.
