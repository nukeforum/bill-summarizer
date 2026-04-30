package com.billsummarizer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.billsummarizer.data.model.Outcome

@Composable
fun OutcomeChip(outcome: Outcome, modifier: Modifier = Modifier) {
    val (label, container, content) = outcomeStyle(outcome)
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

private data class OutcomeStyle(val label: String, val container: Color, val content: Color)

@Composable
private fun outcomeStyle(outcome: Outcome): OutcomeStyle = when (outcome) {
    Outcome.PASSED_HOUSE -> OutcomeStyle(
        label = "Passed House",
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    Outcome.PASSED_SENATE -> OutcomeStyle(
        label = "Passed Senate",
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    Outcome.ENACTED -> OutcomeStyle(
        label = "Enacted",
        container = MaterialTheme.colorScheme.tertiaryContainer,
        content = MaterialTheme.colorScheme.onTertiaryContainer,
    )
    Outcome.VETOED -> OutcomeStyle(
        label = "Vetoed",
        container = MaterialTheme.colorScheme.errorContainer,
        content = MaterialTheme.colorScheme.onErrorContainer,
    )
    Outcome.FAILED -> OutcomeStyle(
        label = "Failed",
        container = MaterialTheme.colorScheme.surfaceVariant,
        content = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
