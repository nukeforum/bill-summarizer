package com.informedcitizen.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SettingsSectionHost(
    sections: Set<SettingsSection>,
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val sorted = sections.sortedBy { it.order }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        sorted.forEach { section -> section.Content() }
    }
}
