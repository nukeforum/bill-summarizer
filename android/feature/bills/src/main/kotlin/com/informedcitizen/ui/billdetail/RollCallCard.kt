package com.informedcitizen.ui.billdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.informedcitizen.pipeline.model.Chamber
import com.informedcitizen.pipeline.model.VoteRef
import com.informedcitizen.ui.util.formatDate

/**
 * One recorded roll call in the bill detail "Votes" section: chamber,
 * date, question, result chip, yea/nay totals, and the published party
 * split. Numbers render as published, never recomputed — if per-party
 * counts don't sum to a position total (unknown-party members), that
 * is a pipeline concern, not a UI one. Party identity stays text-only;
 * the app's palette is deliberately non-partisan.
 */
@Composable
internal fun RollCallCard(vote: VoteRef, modifier: Modifier = Modifier) {
    val description = rollCallDescription(vote)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description }
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${vote.chamber.displayName()} · ${formatDate(vote.date)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            VoteResultChip(result = vote.result)
        }
        Text(text = vote.question, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "Yea ${vote.totals.yea} · Nay ${vote.totals.nay}",
            style = MaterialTheme.typography.titleMedium,
        )
        partySplitLine("Yea", vote.partySplit["yea"])?.let { DetailLine(it) }
        partySplitLine("Nay", vote.partySplit["nay"])?.let { DetailLine(it) }
        if (vote.totals.present > 0) DetailLine("Present ${vote.totals.present}")
        if (vote.totals.notVoting > 0) DetailLine("Not voting ${vote.totals.notVoting}")
    }
}

/**
 * Result pill following the OutcomeChip recipe. Kept in feature:bills
 * rather than core:ui while the detail screen is its only consumer.
 */
@Composable
internal fun VoteResultChip(result: String, modifier: Modifier = Modifier) {
    val passed = result.startsWith("passed", ignoreCase = true) ||
        result.startsWith("agreed", ignoreCase = true)
    val container = if (passed) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (passed) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = result,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun DetailLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal fun Chamber.displayName(): String = when (this) {
    Chamber.HOUSE -> "House"
    Chamber.SENATE -> "Senate"
}

private fun partySplitLine(label: String, counts: Map<String, Int>?): String? =
    counts?.takeIf { it.isNotEmpty() }?.let { split ->
        "$label — " + split.entries.joinToString(" · ") { (party, count) -> "$party $count" }
    }

/** One merged screen-reader description per roll call, parties expanded. */
private fun rollCallDescription(vote: VoteRef): String = buildString {
    append("${vote.chamber.displayName()}, ${formatDate(vote.date)}, ${vote.question}, ${vote.result}.")
    append(" ${vote.totals.yea} yea, ${vote.totals.nay} nay.")
    partySplitDescription("Yea", vote.partySplit["yea"])?.let { append(" $it") }
    partySplitDescription("Nay", vote.partySplit["nay"])?.let { append(" $it") }
    if (vote.totals.present > 0) append(" ${vote.totals.present} present.")
    if (vote.totals.notVoting > 0) append(" ${vote.totals.notVoting} not voting.")
}

private fun partySplitDescription(label: String, counts: Map<String, Int>?): String? =
    counts?.takeIf { it.isNotEmpty() }?.let { split ->
        "$label: " + split.entries.joinToString(", ") { (party, count) ->
            "$count ${partyName(party, count)}"
        } + "."
    }

private fun partyName(code: String, count: Int): String = when (code) {
    "D" -> if (count == 1) "Democrat" else "Democrats"
    "R" -> if (count == 1) "Republican" else "Republicans"
    "I" -> if (count == 1) "Independent" else "Independents"
    else -> code
}
