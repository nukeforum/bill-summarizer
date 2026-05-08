package com.informedcitizen.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.informedcitizen.data.ai.BillTopic
import com.informedcitizen.data.model.Bill
import com.informedcitizen.theme.PartyColors
import com.informedcitizen.ui.util.formatBillRef
import com.informedcitizen.ui.util.formatDate

/**
 * AI summary attached to a BillCard at render time.
 * - null  → feature off, device incapable, or no entry yet (show official title).
 * - non-null with [generatedTitle]  → success.
 * - non-null with [generatedTitle] = null  → tombstone (show official title; no topic chip).
 */
data class BillCardSummary(
    val generatedTitle: String?,
    val topic: BillTopic?,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BillCard(
    bill: Bill,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: BillCardSummary? = null,
    onResummarize: (() -> Unit)? = null,
    aiTitlesEnabled: Boolean = false,
    deviceCapable: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuOpen = true },
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(
                        modifier = Modifier
                            .size(8.dp)
                            .background(PartyColors.forParty(bill.sponsor.party), CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatBillRef(bill.type, bill.number),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                OutcomeChip(outcome = bill.outcome)
            }

            Text(
                text = summary?.generatedTitle ?: bill.shortTitle ?: bill.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = "${bill.sponsor.name} (${bill.sponsor.party}-${bill.sponsor.state}) · ${formatDate(bill.latestAction.date)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            summary?.topic?.let { topic ->
                AssistChip(
                    onClick = { /* card-local chip is decorative; the row's filter chip drives filtering */ },
                    label = { Text(topic.displayName) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            val itemEnabled = onResummarize != null && aiTitlesEnabled && deviceCapable
            val helper = when {
                !deviceCapable -> "Not supported on this device"
                !aiTitlesEnabled -> "AI summarization is off"
                else -> null
            }
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Re-summarize")
                        helper?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                enabled = itemEnabled,
                onClick = {
                    menuOpen = false
                    onResummarize?.invoke()
                },
            )
        }
    }
}
