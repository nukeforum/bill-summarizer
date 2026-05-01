# Solarized theme — design

Status: design approved 2026-05-01. Ready for implementation plan.
Implements TODO #4 (eye-strain-reducing theme). Lays the persistence
groundwork for TODO #5 (settings screen) but does not implement the picker.

## Goal

Add the Solarized palette (light + dark) as a second theme family alongside
the existing Material schemes, persist the user's selection, and default the
app to "Solarized, follow system." No user-facing picker ships in this work.

## Decisions

| | |
|---|---|
| Palette | Solarized — https://ethanschoonover.com/solarized/ |
| Themes shipped | Material light/dark/system **and** Solarized light/dark/system (six effective combinations) |
| Default | `SOLARIZED_SYSTEM` |
| Dynamic color | Enabled for Material (light, dark, and system), disabled for Solarized |
| Settings UI | Out of scope — deferred to TODO #5 |
| Splash screen color | Out of scope — `res/values/themes.xml` left as-is |

## Architecture

Six effective themes are modeled as a flat enum persisted as a single
`stringPreferencesKey`. A pure resolver function maps
`(ThemePreference, isSystemDark) → (ThemeFamily, isDark)`. `MyApplicationTheme`
uses the resolver output to pick between dynamic Material schemes and the
fixed Solarized schemes.

The two-enum-split and sealed-class alternatives were considered and
rejected as over-engineered for six values.

## Components

### `theme/Color.kt` — additions

Add the 16 Solarized constants, named after the upstream palette:

```kotlin
// Solarized — https://ethanschoonover.com/solarized/
val SolarizedBase03 = Color(0xFF002B36) // darkest bg
val SolarizedBase02 = Color(0xFF073642) // dark bg highlight
val SolarizedBase01 = Color(0xFF586E75) // dark fg / light emph
val SolarizedBase00 = Color(0xFF657B83) // light fg
val SolarizedBase0  = Color(0xFF839496) // dark body
val SolarizedBase1  = Color(0xFF93A1A1) // dark emph / light secondary
val SolarizedBase2  = Color(0xFFEEE8D5) // light bg highlight
val SolarizedBase3  = Color(0xFFFDF6E3) // lightest bg
val SolarizedYellow = Color(0xFFB58900)
val SolarizedOrange = Color(0xFFCB4B16)
val SolarizedRed    = Color(0xFFDC322F)
val SolarizedMagenta= Color(0xFFD33682)
val SolarizedViolet = Color(0xFF6C71C4)
val SolarizedBlue   = Color(0xFF268BD2)
val SolarizedCyan   = Color(0xFF2AA198)
val SolarizedGreen  = Color(0xFF859900)
```

`PartyColors` is left untouched — those constants are semantic, not theme-tied.

### `theme/Theme.kt` — Solarized `ColorScheme` mappings

Two new `private val` schemes built with `lightColorScheme(...)` /
`darkColorScheme(...)` using the slot mapping below. Blue is primary
(matches upstream Solarized link/highlight convention); Cyan is secondary;
Violet is tertiary. The user has flagged that these slot choices are the
most opinionated decision and may want adjustment after seeing real screens.

| M3 slot | Solarized Light | Solarized Dark |
|---|---|---|
| `primary` | `SolarizedBlue` | `SolarizedBlue` |
| `onPrimary` | `SolarizedBase3` | `SolarizedBase03` |
| `primaryContainer` | `SolarizedBase2` | `SolarizedBase02` |
| `onPrimaryContainer` | `SolarizedBase01` | `SolarizedBase1` |
| `secondary` | `SolarizedCyan` | `SolarizedCyan` |
| `onSecondary` | `SolarizedBase3` | `SolarizedBase03` |
| `tertiary` | `SolarizedViolet` | `SolarizedViolet` |
| `onTertiary` | `SolarizedBase3` | `SolarizedBase03` |
| `background` | `SolarizedBase3` | `SolarizedBase03` |
| `onBackground` | `SolarizedBase00` | `SolarizedBase0` |
| `surface` | `SolarizedBase3` | `SolarizedBase03` |
| `onSurface` | `SolarizedBase00` | `SolarizedBase0` |
| `surfaceVariant` | `SolarizedBase2` | `SolarizedBase02` |
| `onSurfaceVariant` | `SolarizedBase01` | `SolarizedBase1` |
| `outline` | `SolarizedBase1` | `SolarizedBase01` |
| `outlineVariant` | `SolarizedBase2` | `SolarizedBase02` |
| `error` | `SolarizedRed` | `SolarizedRed` |
| `onError` | `SolarizedBase3` | `SolarizedBase03` |
| `errorContainer` | `SolarizedBase2` | `SolarizedBase02` |
| `onErrorContainer` | `SolarizedRed` | `SolarizedRed` |

### `theme/ThemePreference.kt` — new file

```kotlin
enum class ThemeFamily { MATERIAL, SOLARIZED }

enum class ThemePreference {
    MATERIAL_SYSTEM, MATERIAL_LIGHT, MATERIAL_DARK,
    SOLARIZED_SYSTEM, SOLARIZED_LIGHT, SOLARIZED_DARK;

    companion object {
        val DEFAULT = SOLARIZED_SYSTEM

        fun fromStored(name: String?): ThemePreference =
            name?.let { runCatching { valueOf(it) }.getOrNull() } ?: DEFAULT
    }
}

fun resolve(pref: ThemePreference, systemDark: Boolean): Pair<ThemeFamily, Boolean> = when (pref) {
    ThemePreference.MATERIAL_SYSTEM   -> ThemeFamily.MATERIAL  to systemDark
    ThemePreference.MATERIAL_LIGHT    -> ThemeFamily.MATERIAL  to false
    ThemePreference.MATERIAL_DARK     -> ThemeFamily.MATERIAL  to true
    ThemePreference.SOLARIZED_SYSTEM  -> ThemeFamily.SOLARIZED to systemDark
    ThemePreference.SOLARIZED_LIGHT   -> ThemeFamily.SOLARIZED to false
    ThemePreference.SOLARIZED_DARK    -> ThemeFamily.SOLARIZED to true
}
```

`fromStored` returns `DEFAULT` for null and for any unknown string — this
covers a future app-version downgrade where a newer enum value was persisted
and the older app no longer recognizes it.

### `data/repository/ThemePreferenceRepository.kt` — new file

```kotlin
@Singleton
class ThemePreferenceRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val preference: Flow<ThemePreference> = dataStore.data
        .map { ThemePreference.fromStored(it[KEY]) }
        .catch { emit(ThemePreference.DEFAULT) }

    suspend fun set(pref: ThemePreference) {
        dataStore.edit { it[KEY] = pref.name }
    }

    private companion object {
        val KEY = stringPreferencesKey("theme_preference")
    }
}
```

Shares the existing app-wide `DataStore<Preferences>` provided by
`DataStoreModule`. Coexists with `BillRepository`'s
`last_fetched_at_millis` key — no collision.

### `theme/Theme.kt` — refactored `MyApplicationTheme`

```kotlin
@Composable
fun MyApplicationTheme(
    preference: ThemePreference = ThemePreference.DEFAULT,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val (family, isDark) = resolve(preference, systemDark)
    val colorScheme = when (family) {
        ThemeFamily.MATERIAL  -> materialScheme(LocalContext.current, isDark)
        ThemeFamily.SOLARIZED -> if (isDark) SolarizedDarkScheme else SolarizedLightScheme
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

@SuppressLint("NewApi")
private fun materialScheme(context: Context, isDark: Boolean): ColorScheme =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (isDark) FallbackDarkScheme else FallbackLightScheme
    }
```

`FallbackDarkScheme` / `FallbackLightScheme` are the existing Purple-based
schemes (renamed from `DarkColorScheme` / `LightColorScheme` to make the
"only used when dynamic isn't available" intent explicit). The
`dynamicColor: Boolean` parameter on `MyApplicationTheme` is removed —
dynamic is implicit for Material on supported devices, never used for
Solarized. `MainActivity` is the sole call site and currently relies on
the default value, so removing the parameter is non-breaking. The
`darkTheme: Boolean` parameter is also removed for the same reason —
it was previously the only knob the theme exposed, and is now subsumed
by `preference` plus the internal `isSystemInDarkTheme()` lookup.

### `MainActivity.kt` — collect and pass through

```kotlin
@Inject lateinit var themePrefs: ThemePreferenceRepository
// ...
setContent {
    val preference by themePrefs.preference.collectAsState(initial = ThemePreference.DEFAULT)
    MyApplicationTheme(preference) {
        // existing nav host
    }
}
```

Initial-emission flicker is a non-issue: `collectAsState`'s initial value
is `DEFAULT = SOLARIZED_SYSTEM`, which matches what first-run users will
see anyway. Returning users see the same scheme on the first frame because
DataStore reads are fast and the resolver is pure.

## Data flow

1. App start → `MainActivity` injected with `ThemePreferenceRepository`.
2. `setContent` → `themePrefs.preference.collectAsState(initial = DEFAULT)`.
3. Recomposition → `MyApplicationTheme(preference) { ... }`.
4. Inside theme: `resolve(preference, systemDark)` → pick `ColorScheme`.
5. (Future) Settings screen calls `themePrefs.set(...)` → DataStore writes
   → flow re-emits → recomposition → new scheme applied.

## Error handling

The only failure mode worth handling is "stored value can't be parsed."
`ThemePreference.fromStored` swallows `IllegalArgumentException` from
`valueOf` and returns `DEFAULT`. DataStore I/O failures are absorbed by the `.catch { emit(DEFAULT) }`
operator on the repository flow, so the collector keeps a live theme
even if DataStore throws after the first successful emission. The
`collectAsState(initial = DEFAULT)` in `MainActivity` covers the
not-yet-emitted frame.

## Testing

- **`ThemePreferenceTest`** — pure JVM unit test:
  - `fromStored` round-trips all six enum names.
  - `fromStored(null)` returns `SOLARIZED_SYSTEM`.
  - `fromStored("garbage")` returns `SOLARIZED_SYSTEM`.
- **`ThemeResolverTest`** — pure JVM unit test, table-driven:
  - All 6 prefs × {`systemDark=true`, `systemDark=false`} = 12 rows.
  - Asserts the resolved `(ThemeFamily, isDark)` pair.
- **Manual smoke (release build, real device):**
  - Launch with no prior preference → Solarized scheme appears, matching
    system light/dark setting.
  - Toggle system dark mode → app follows.
  - Inspect bills list, detail screen, FAB, chips, top bar, HTML body
    text — verify legibility and no obviously-wrong contrast.

No Compose / instrumentation tests on the schemes themselves — the M3
machinery applying a `ColorScheme` is library-trusted, and pixel-value
assertions would be brittle.

## Out of scope

- Settings UI / theme picker (TODO #5).
- Splash screen color (`res/values/themes.xml`).
- `PartyColors` audit — values stay as they are.
- Per-screen theme overrides (e.g., forcing Solarized on the detail
  screen only). Whole-app only.

## Open items for follow-up

- Slot-mapping tuning: user will play with primary/secondary/tertiary
  choices once the work lands and give feedback.
- TODO #5 (settings picker) becomes the natural follow-up; it can render
  `ThemePreference` values as a single radio group, calling
  `ThemePreferenceRepository.set(...)`.
