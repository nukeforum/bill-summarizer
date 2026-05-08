package com.informedcitizen.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.informedcitizen.data.model.Outcome
import com.informedcitizen.ui.preview.PreviewWrap

@PreviewLightDark
@Composable
private fun PreviewOutcomeChipAll() = PreviewWrap(modifier = Modifier) {
    Row(
        modifier = Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Outcome.entries.forEach { outcome ->
            OutcomeChip(outcome = outcome)
        }
    }
}
