package com.informedcitizen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.informedcitizen.pipeline.model.LifecycleStatus
import com.informedcitizen.ui.util.displayName

/**
 * Pre-floor lifecycle-status chip (issue #42) — the counterpart to [OutcomeChip]
 * for a bill that has not (yet) reached a terminal floor [com.informedcitizen.pipeline.model.Outcome].
 *
 * A bill carries EITHER a floor outcome OR a lifecycle status, never both
 * (`classifyBillStatus` lets a real outcome win and leaves `lifecycleStatus`
 * null), so [BillCard] shows exactly one of the two chips. The neutral
 * `secondaryContainer` styling deliberately reads as "in progress" — distinct
 * from the primary/tertiary/error outcome chips that denote a decided result —
 * so a mixed or filtered list is self-explanatory once #39's pre-floor bills
 * publish.
 */
@Composable
fun LifecycleStatusChip(status: LifecycleStatus, modifier: Modifier = Modifier) {
    // A status this app generation can't interpret shouldn't show a misleading
    // chip — omit it rather than render "Unknown" (mirrors OutcomeChip).
    if (status == LifecycleStatus.UNKNOWN) return
    Text(
        text = status.displayName(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
