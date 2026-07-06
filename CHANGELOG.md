# Changelog

All notable user-facing changes to the Informed Citizen Android app are documented
in this file. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project aims to follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

This changelog begins at 1.1.0; earlier releases (through 1.0.1) predate it and are
recorded only in git history and the Play Store release track.

## [1.1.0] - 2026-07-06

### Added

- **Social media handles for representatives.** Each representative can now surface
  their official social media accounts as a dedicated contact channel, opened
  straight from the member card.
- **Contact-help dialog.** A help affordance in the title bar explains the available
  ways to reach a representative, including what the new social channel is for.
- **Bring your own key (BYOK).** You can now supply your own Congress.gov API key and
  have the app fetch legislative data directly from the source instead of relying on
  the bundled data feed.
- **About section in Settings.** A new About section lists the app's data sources and
  states its non-affiliation with any government body or campaign.
- **ZIP-match confirmation.** After looking up your district from a ZIP code, the app
  now asks you to confirm the matched district before applying it.

### Changed

- **Consolidated representative contact options.** Contact methods are now shown as
  one segment per available method on each representative, with contact help
  consolidated into the title bar for a cleaner, more consistent layout.
- **Scrollable Settings screen.** The Settings screen now scrolls so all options
  remain reachable on smaller screens.

### Fixed

- **Manual district selection after ZIP lookup.** You can now pick your district by
  hand after an automatic ZIP lookup, so an imperfect ZIP-to-district match no longer
  leaves you stuck.
- **Clear indication when no contact methods exist.** Representatives with no
  published contact methods now show an explicit indicator instead of an empty area.
- **No crash reporting in debug builds.** Crashlytics is disabled in debug builds so
  development activity no longer reaches crash reporting; a dedicated `.debug` build
  type also lets debug and release installs coexist on the same device.

## [1.0.1]

- Predates this changelog. See git history for details.
