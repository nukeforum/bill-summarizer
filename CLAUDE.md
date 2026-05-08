# Project rules

## Compose screens — always extract a stateless `Content` composable

Every navigated screen in `android/app/src/main/java/com/informedcitizen/ui/`
is split into two layers:

- **`XxxScreen`** — the entrypoint wired into navigation. Holds
  `hiltViewModel()` resolution, `LaunchedEffect`s, `LocalContext`
  consumers, side effects on the host `Activity` window, and the
  `Scaffold` shell.
- **`XxxContent`** — a stateless composable that takes the UiState
  (and any callbacks / inner padding) as parameters. Has no Hilt
  dependency, no `LaunchedEffect`, no `(view.context as Activity)`
  cast, no direct ViewModel reference. Renders identically in
  production and in `@Preview`.

**Why:** previews driven by `hiltViewModel()` either fail to render or
need a fake DI graph; previews that touch `Activity` window APIs crash
in the IDE. Splitting the rendering surface from the wiring makes every
screen previewable from day one and keeps the `@Preview` setup trivial
(pass a hand-built UiState into `XxxContent`).

**How to apply:**

- When adding a new screen, write the `Content` composable first as a
  pure function of state + callbacks. Wire it up in `XxxScreen` second.
- When modifying an existing screen, do not collapse the split. If you
  find yourself reaching for `hiltViewModel()` or `LaunchedEffect`
  inside `XxxContent`, lift it to `XxxScreen` and pass the result as a
  parameter.
- The `XxxContent` composable is normally `private` and lives in the
  same file as `XxxScreen`. Make it `internal` only if a sibling
  `XxxPreviews.kt` or test needs to call it.
- Side effects belong in `XxxScreen`. Rendering belongs in
  `XxxContent`. If a state needs to trigger a side effect (e.g.
  navigation on a one-shot event), surface it as a callback the
  `Screen` passes in.

Examples already on `main`: `BillsListScreen` /
`BillsListContent`, `SettingsScreen` / `SettingsContent`.
