package com.informedcitizen.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * The Settings screen is intentionally state-less at the host level —
 * each section pulls its own state via injected repositories. This
 * ViewModel exists only to hand the Hilt-aggregated `Set<SettingsSection>`
 * into the screen Composable; everything else is owned by sections.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    val sections: Set<@JvmSuppressWildcards SettingsSection>,
) : ViewModel()
