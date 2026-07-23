# fastlane — Informed Citizen Android

This directory configures [fastlane](https://fastlane.tools) for the Android
app. It lives under `android/` so it sits next to the Gradle wrapper
(`android/gradlew`); every lane invokes that committed wrapper.

## Install

Requires Ruby + Bundler. From `android/`:

```sh
bundle install          # installs the pinned fastlane (see ../Gemfile)
```

Run all lanes with `bundle exec` so the pinned version is used:

```sh
bundle exec fastlane <lane>
```

## Lanes

| Lane                | What it does                                                        | Gradle task                |
| ------------------- | ------------------------------------------------------------------- | -------------------------- |
| `test`              | Run the app's unit tests                                             | `:app:testDebugUnitTest`   |
| `build`             | Assemble a signed release **AAB** (App Bundle)                       | `:app:bundleRelease`       |
| `deploy_internal`   | `build` then upload the AAB to the Play Store **internal** track     | `:app:bundleRelease` + supply |
| `deploy_beta`       | `build` then upload the AAB to the Play Store **beta** track         | `:app:bundleRelease` + supply |
| `deploy_production` | `build` then upload the AAB to the Play Store **production** track   | `:app:bundleRelease` + supply |
| `deploy`            | Parameterized: `fastlane deploy track:<internal\|beta\|production>`  | `:app:bundleRelease` + supply |

The release AAB is produced at
`app/build/outputs/bundle/release/app-release.aab`.

Non-production uploads use `release_status: "draft"`; production uses
`"completed"`. Adjust in the `Fastfile` if you prefer staged rollouts.

## Required secrets

fastlane does **not** re-implement signing — Gradle signs the release build
using the config already in `app/build.gradle.kts`. You must supply:

### 1. Release signing keystore (for `build` / `deploy_*`)

`app/build.gradle.kts` reads these from Gradle properties
(`~/.gradle/gradle.properties`) with an environment-variable fallback:

| Variable                          | Meaning                          |
| --------------------------------- | -------------------------------- |
| `INFORMEDCITIZEN_KEYSTORE_PATH`     | Path to the `.jks`/`.keystore` file |
| `INFORMEDCITIZEN_KEYSTORE_PASSWORD` | Keystore (store) password        |
| `INFORMEDCITIZEN_KEY_ALIAS`         | Signing key alias                |
| `INFORMEDCITIZEN_KEY_PASSWORD`      | Key password                     |

If any is missing, the release `signingConfig` is left unconfigured and
`bundleRelease` fails — debug builds still work. Provide them via env vars
in CI, e.g.:

```sh
export INFORMEDCITIZEN_KEYSTORE_PATH=/secure/upload-keystore.jks
export INFORMEDCITIZEN_KEYSTORE_PASSWORD=…
export INFORMEDCITIZEN_KEY_ALIAS=upload
export INFORMEDCITIZEN_KEY_PASSWORD=…
```

### 2. Play Store service-account JSON (for `deploy_*` only)

`upload_to_play_store` / `supply` authenticate with a Google Play
service-account key. **This key is not committed and is not yet available in
this repo.** When you have it, point `PLAY_STORE_JSON_KEY` at the file:

```sh
export PLAY_STORE_JSON_KEY=/secure/play-service-account.json
```

The `Appfile` reads `PLAY_STORE_JSON_KEY`, falling back to
`android/fastlane/play-store-service-account.json` (gitignored). To create
the key: Google Play Console → Setup → API access → create/link a service
account with **Release manager** permissions and download its JSON key.

Verify credentials once configured:

```sh
bundle exec fastlane run validate_play_store_json_key
```

## Notes

- This README is hand-written, not fastlane-generated. The `Fastfile`
  calls `skip_docs` so lane runs never regenerate/overwrite it — keep
  maintaining it by hand.
- Generated `report.xml`, build artifacts (`*.aab`/`*.apk`), keystores, and
  the service-account JSON are all gitignored — see `android/.gitignore`.
- `android/Gemfile.lock` is committed and pins fastlane `2.237.0` (plus its
  transitive gems) so `bundle exec fastlane` runs reproducibly. After changing
  `Gemfile`, re-run `bundle install` (or `bundle lock`) and commit the updated
  lockfile.
- **Play Store "What's new" notes**: each release adds a
  `metadata/android/en-US/changelogs/<versionCode>.txt` file (matching the new
  `versionCode` in `app/build.gradle.kts`) alongside its `CHANGELOG.md` entry.
  supply reads this file and uploads it as the release notes automatically on
  every `deploy_*` — the lanes skip store-listing metadata, images, and
  screenshots but not changelogs, so keep each file user-facing and under Play's
  500-character limit.
