# Settings screen + theme picker — design

Status: design approved 2026-05-01.
Implements TODO #5. Builds on the Solarized theme work
(`2026-05-01-solarized-theme-design.md`); requires the
`ThemePreferenceRepository` + `ThemePreference` enum that landed in
commit `55da545`.

## Goal

Add a "Settings" screen reachable from a gear icon in the bills-list
top app bar. The only section today is "Theme", with a segmented
family selector (Material / Solarized) plus three radio rows (Follow
system / Light / Dark). Future settings will live as additional
sections on the same screen.

## Decisions

| | |
|---|---|
| Screen title | Settings |
| Sections today | Theme (only) |
| Picker shape | Segmented button (family) + three radios (mode) |
| Mode order | Follow system, Light, Dark |
| Default selected family on first open | Bound to current preference (Solarized on first launch) |
| Entry point | Gear icon in `BillsListScreen` TopAppBar `actions` slot |
| Storage | Existing `ThemePreferenceRepository` (single flat enum write per gesture) |

## Architecture

The flat `ThemePreference` enum stays the single source of truth in
DataStore. The picker UI presents two perpendicular axes (family,
mode); pure mapping helpers convert in both directions. A standard
Hilt `ViewModel` collects the repository's flow as `StateFlow` and
exposes one setter that writes back the composed enum value.

## Components

### `theme/ThemePreference.kt` — additions

```kotlin
enum class ThemeMode { SYSTEM, LIGHT, DARK }

val ThemePreference.family: ThemeFamily
    get() = when (this) {
        ThemePreference.MATERIAL_SYSTEM,
        ThemePreference.MATERIAL_LIGHT,
        ThemePreference.MATERIAL_DARK -> ThemeFamily.MATERIAL
        ThemePreference.SOLARIZED_SYSTEM,
        ThemePreference.SOLARIZED_LIGHT,
        ThemePreference.SOLARIZED_DARK -> ThemeFamily.SOLARIZED
    }

val ThemePreference.mode: ThemeMode
    get() = when (this) {
        ThemePreference.MATERIAL_SYSTEM,
        ThemePreference.SOLARIZED_SYSTEM -> ThemeMode.SYSTEM
        ThemePreference.MATERIAL_LIGHT,
        ThemePreference.SOLARIZED_LIGHT -> ThemeMode.LIGHT
        ThemePreference.MATERIAL_DARK,
        ThemePreference.SOLARIZED_DARK -> ThemeMode.DARK
    }

fun ThemePreference.withFamily(newFamily: ThemeFamily): ThemePreference =
    ThemePreference.from(newFamily, mode)

fun ThemePreference.withMode(newMode: ThemeMode): ThemePreference =
    ThemePreference.from(family, newMode)

fun ThemePreference.Companion.from(family: ThemeFamily, mode: ThemeMode): ThemePreference =
    when (family) {
        ThemeFamily.MATERIAL -> when (mode) {
            ThemeMode.SYSTEM -> ThemePreference.MATERIAL_SYSTEM
            ThemeMode.LIGHT  -> ThemePreference.MATERIAL_LIGHT
            ThemeMode.DARK   -> ThemePreference.MATERIAL_DARK
        }
        ThemeFamily.SOLARIZED -> when (mode) {
            ThemeMode.SYSTEM -> ThemePreference.SOLARIZED_SYSTEM
            ThemeMode.LIGHT  -> ThemePreference.SOLARIZED_LIGHT
            ThemeMode.DARK   -> ThemePreference.SOLARIZED_DARK
        }
    }
```

`ThemeMode` is a UI-only enum — it does not need to be persisted; the
storage layer continues to round-trip the flat `ThemePreference`. The
`from(...)` and `withFamily/withMode` helpers are total over the
6-value cross product and contain no error paths.

### `NavigationKeys.kt` — addition

```kotlin
@Serializable data object Settings : NavKey
```

### `ui/settings/SettingsViewModel.kt` — new file

```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePrefs: ThemePreferenceRepository,
) : ViewModel() {
    val preference: StateFlow<ThemePreference> = themePrefs.preference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemePreference.DEFAULT)

    fun setPreference(pref: ThemePreference) {
        viewModelScope.launch { themePrefs.set(pref) }
    }
}
```

`stateIn` with `WhileSubscribed(5_000)` matches Android architecture
guidance for UI-bound flows; the 5 s grace period prevents redundant
DataStore reads on configuration changes. Initial value is `DEFAULT`,
which renders correctly on first frame.

### `ui/settings/SettingsScreen.kt` — new file

`Scaffold` with a `TopAppBar` titled "Settings" and a back-arrow
navigation icon. Body is a `LazyColumn` (or `Column` — single section
makes either fine; we use `Column` for now and switch to `LazyColumn`
when sections proliferate). Layout:

```
[Section header: "Theme"]
[SingleChoiceSegmentedButtonRow: (Material) (Solarized)]
[RadioButton row]: (•) Follow system
[RadioButton row]: ( ) Light
[RadioButton row]: ( ) Dark
```

The segmented control uses Material 3 `SingleChoiceSegmentedButtonRow`
+ two `SegmentedButton` children. Selected segment = `preference.family`.

The three radio rows are individual rows wrapping a `RadioButton` and a
text label, with `Modifier.selectable(...)` on the row so taps on the
label also select the option (Material accessibility pattern).

Click handlers:
- Segment tap: `vm.setPreference(preference.withFamily(newFamily))`
- Radio tap: `vm.setPreference(preference.withMode(newMode))`

The screen takes one parameter: `onBack: () -> Unit`. The ViewModel is
obtained via `hiltViewModel()` (matches the `BillsListScreen` pattern).

### `Navigation.kt` — additions

```kotlin
entry<Settings> {
    SettingsScreen(onBack = { backStack.removeLastOrNull() })
}
```

The existing `BillsList` entry gains an `onSettingsClick` argument:

```kotlin
entry<BillsList> {
    BillsListScreen(
        onBillClick = { bill -> backStack.add(BillDetail(bill.id)) },
        onSettingsClick = { backStack.add(Settings) },
        modifier = Modifier,
    )
}
```

### `ui/billslist/BillsListScreen.kt` — modifications

`BillsListScreen` gains an `onSettingsClick: () -> Unit` parameter. The
`TopAppBar` gains an `actions = { ... }` slot containing one
`IconButton` with `Icons.Default.Settings`:

```kotlin
TopAppBar(
    title = { Text("Recently Voted On") },
    actions = {
        IconButton(onClick = onSettingsClick) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
    },
)
```

`Icons.Default.Settings` is already available — `material-icons-extended`
is on the classpath (added in commit `94ec5fd`).

## Data flow

1. Bills list → user taps gear → `backStack.add(Settings)`.
2. `SettingsScreen` composes → `hiltViewModel<SettingsViewModel>()`.
3. ViewModel collects `themePrefs.preference` as `StateFlow` (initial = `DEFAULT`).
4. UI renders: segment from `preference.family`, radio from `preference.mode`.
5. User taps segment or radio → `vm.setPreference(composed)`.
6. ViewModel calls `themePrefs.set(...)` → DataStore writes →
   repository flow re-emits → ViewModel state updates → UI recomposes.
7. `MainActivity`'s top-level collector also re-emits → `MyApplicationTheme`
   recomposes the whole app with the new scheme.
8. User taps back → `backStack.removeLastOrNull()` → bills list,
   already wearing the new theme.

## Error handling

No new error paths. The mapping helpers are total over the type system
(no `error("unreachable")` strings or `else -> throw ...` cases). The
existing `.catch { emit(DEFAULT) }` on the repository flow continues
to absorb any DataStore I/O failures.

## Testing

- **`ThemePreferenceMappingTest`** — pure JVM unit test:
  - `ThemePreference.entries.forEach { assertEquals(it, ThemePreference.from(it.family, it.mode)) }` — round-trip every value through split-and-recompose.
  - `family` / `mode` return expected values for all six prefs (table-driven).
  - `withFamily(other).family == other` for all (pref, family) pairs.
  - `withMode(other).mode == other` for all (pref, mode) pairs.
  - `withFamily(this.family) == this` and `withMode(this.mode) == this` — idempotence.
- **Manual smoke (real device):**
  - Tap gear → Settings appears with Solarized + Follow system pre-selected (matching active prefs).
  - Toggle segment to Material → app re-themes immediately to dynamic Material light/dark.
  - Toggle radio to Light → app stays Material, switches to light scheme regardless of system.
  - Back → bills list reflects the new scheme.
  - Force-quit + relaunch → preference persists.

No Compose UI tests on the picker — `SegmentedButton` and `RadioButton`
state is library-trusted.

## Out of scope

- Additional settings sections (refresh interval, notifications, About,
  app-name override). Structure is in place to add them.
- Animated transitions on theme change. Material's color recompose is
  fine by default.
- Theme-preview swatches inside the picker. The segmented control's
  selected-segment color already reflects the active primary, acting as
  a passive preview.
- Splash screen color (still on `res/values/themes.xml` static colors).

## Open follow-ups

- TODO #4 noted that primary/secondary/tertiary slot choices in the
  Solarized schemes may want adjustment after seeing real screens. The
  picker doesn't surface that — it's a code-level tuning task that
  remains a separate follow-up.
