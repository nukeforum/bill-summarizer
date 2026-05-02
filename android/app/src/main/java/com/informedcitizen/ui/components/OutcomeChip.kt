package com.informedcitizen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.informedcitizen.data.model.Outcome
import com.informedcitizen.ui.util.displayName

@Composable
fun OutcomeChip(outcome: Outcome, modifier: Modifier = Modifier) {
    val (container, content) = outcomeColors(outcome)
    Text(
        text = outcome.displayName(),
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun outcomeColors(outcome: Outcome): Pair<Color, Color> = when (outcome) {
    Outcome.PASSED_HOUSE,
    Outcome.PASSED_SENATE -> MaterialTheme.colorScheme.primaryContainer to
        MaterialTheme.colorScheme.onPrimaryContainer
    Outcome.ENACTED -> MaterialTheme.colorScheme.tertiaryContainer to
        MaterialTheme.colorScheme.onTertiaryContainer
    Outcome.VETOED -> MaterialTheme.colorScheme.errorContainer to
        MaterialTheme.colorScheme.onErrorContainer
    Outcome.FAILED -> MaterialTheme.colorScheme.surfaceVariant to
        MaterialTheme.colorScheme.onSurfaceVariant
}
