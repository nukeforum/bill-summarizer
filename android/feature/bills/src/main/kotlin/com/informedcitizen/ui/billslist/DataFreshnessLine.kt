package com.informedcitizen.ui.billslist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.Instant

/**
 * A lightweight "Updated N hours/days ago" line for the bills list,
 * derived from the manifest's `generated_at` — the artifact's generation
 * time, not the local fetch time. Past [STALE_AFTER] it switches to a
 * caution state (error color + warning icon) so staleness is visible
 * rather than silent.
 *
 * When the list merges multiple Congress manifests the caller should pass
 * the timestamp of the max-`congress` (current) manifest: older archived
 * Congresses legitimately never regenerate, so only the manifest the
 * updater workflow still touches should drive the freshness claim.
 *
 * Accessibility: the row is a single semantic text node; in the caution
 * state it prepends "Warning:" so TalkBack conveys severity without
 * relying on color. Not clickable in this base version.
 */
@Composable
fun DataFreshnessLine(
    generatedAt: Instant,
    now: Instant,
    modifier: Modifier = Modifier,
) {
    val stale = isDataStale(generatedAt, now)
    val label = formatDataFreshness(generatedAt, now)
    val color = if (stale) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val semanticText = if (stale) "Warning: $label" else label

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { contentDescription = semanticText },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (stale) Icons.Outlined.WarningAmber else Icons.Outlined.Schedule,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}
