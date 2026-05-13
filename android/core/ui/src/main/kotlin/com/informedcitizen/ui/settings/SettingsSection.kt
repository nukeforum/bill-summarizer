package com.informedcitizen.ui.settings

import androidx.compose.runtime.Composable

/**
 * One vertical block on the Settings screen. Each feature module
 * contributes a `SettingsSection` via Hilt `@IntoSet` multibindings,
 * and `SettingsSectionHost` renders them sorted by `order` ascending.
 *
 * Sections render themselves end-to-end (header + body). Use small
 * gaps in `order` values (e.g. 10, 20, 30) so new sections can be
 * inserted between existing ones without touching every contributor.
 */
interface SettingsSection {
    val order: Int

    @Composable
    fun Content()
}
