package com.billsummarizer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.billsummarizer.data.model.Bill
import com.billsummarizer.theme.PartyColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val displayDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

@Composable
fun BillCard(bill: Bill, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                text = bill.shortTitle ?: bill.title,
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
        }
    }
}

private fun formatBillRef(type: String, number: String): String = when (type.lowercase()) {
    "hr" -> "H.R. $number"
    "s" -> "S. $number"
    "hjres" -> "H.J.Res. $number"
    "sjres" -> "S.J.Res. $number"
    "hconres" -> "H.Con.Res. $number"
    "sconres" -> "S.Con.Res. $number"
    "hres" -> "H.Res. $number"
    "sres" -> "S.Res. $number"
    else -> "${type.uppercase()} $number"
}

private fun formatDate(iso: String): String = try {
    LocalDate.parse(iso).format(displayDateFormatter)
} catch (_: DateTimeParseException) {
    iso
}
