package com.informedcitizen.ui.billdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.informedcitizen.data.repository.RepPosition
import com.informedcitizen.data.repository.SavedRep
import com.informedcitizen.pipeline.model.Chamber
import com.informedcitizen.pipeline.model.VotePosition
import com.informedcitizen.pipeline.model.VoteRef
import com.informedcitizen.ui.util.formatDate

/**
 * One recorded roll call in the bill detail "Votes" section: chamber,
 * date, question, result chip, yea/nay totals, and the published party
 * split. Numbers render as published, never recomputed — if per-party
 * counts don't sum to a position total (unknown-party members), that
 * is a pipeline concern, not a UI one. Party identity stays text-only;
 * the app's palette is deliberately non-partisan — positions render as
 * text, never color.
 *
 * [repPositions] are the saved reps' recorded positions in this roll
 * call specifically (already joined by vote id upstream, which makes
 * chamber matching automatic); a saved rep absent from the roll call's
 * rows gets no row — no "did not vote" guess.
 */
@Composable
internal fun RollCallCard(
    vote: VoteRef,
    modifier: Modifier = Modifier,
    repPositions: List<RepPosition> = emptyList(),
) {
    val description = rollCallDescription(vote, repPositions)
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
        if (repPositions.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = "Your reps",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            repPositions.forEach { repPosition ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = repLabel(repPosition.rep),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = positionLabel(repPosition.position),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
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

private fun repLabel(rep: SavedRep): String {
    val prefix = if (rep.chamber == "senate") "Sen." else "Rep."
    return "$prefix ${rep.name} (${rep.party}-${rep.state})"
}

private fun positionLabel(position: VotePosition): String = when (position) {
    VotePosition.YEA -> "Yea"
    VotePosition.NAY -> "Nay"
    VotePosition.PRESENT -> "Present"
    VotePosition.NOT_VOTING -> "Not voting"
}

/**
 * One merged screen-reader description per roll call, abbreviations
 * expanded (R-OH → Republican, Ohio) as elsewhere in the section.
 */
private fun rollCallDescription(vote: VoteRef, repPositions: List<RepPosition>): String = buildString {
    append("${vote.chamber.displayName()}, ${formatDate(vote.date)}, ${vote.question}, ${vote.result}.")
    append(" ${vote.totals.yea} yea, ${vote.totals.nay} nay.")
    partySplitDescription("Yea", vote.partySplit["yea"])?.let { append(" $it") }
    partySplitDescription("Nay", vote.partySplit["nay"])?.let { append(" $it") }
    if (vote.totals.present > 0) append(" ${vote.totals.present} present.")
    if (vote.totals.notVoting > 0) append(" ${vote.totals.notVoting} not voting.")
    if (repPositions.isNotEmpty()) {
        append(" Your representatives: ")
        append(
            repPositions.joinToString("; ") { repPosition ->
                val rep = repPosition.rep
                val role = if (rep.chamber == "senate") "Senator" else "Representative"
                "$role ${rep.name}, ${partyName(rep.party, 1)}, ${stateName(rep.state)} — " +
                    positionLabel(repPosition.position).lowercase()
            },
        )
        append(".")
    }
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

private fun stateName(code: String): String = STATE_NAMES[code] ?: code

// Postal codes appearing in members data: 50 states plus the
// districts/territories with congressional delegates.
private val STATE_NAMES = mapOf(
    "AL" to "Alabama", "AK" to "Alaska", "AZ" to "Arizona", "AR" to "Arkansas",
    "CA" to "California", "CO" to "Colorado", "CT" to "Connecticut", "DE" to "Delaware",
    "FL" to "Florida", "GA" to "Georgia", "HI" to "Hawaii", "ID" to "Idaho",
    "IL" to "Illinois", "IN" to "Indiana", "IA" to "Iowa", "KS" to "Kansas",
    "KY" to "Kentucky", "LA" to "Louisiana", "ME" to "Maine", "MD" to "Maryland",
    "MA" to "Massachusetts", "MI" to "Michigan", "MN" to "Minnesota", "MS" to "Mississippi",
    "MO" to "Missouri", "MT" to "Montana", "NE" to "Nebraska", "NV" to "Nevada",
    "NH" to "New Hampshire", "NJ" to "New Jersey", "NM" to "New Mexico", "NY" to "New York",
    "NC" to "North Carolina", "ND" to "North Dakota", "OH" to "Ohio", "OK" to "Oklahoma",
    "OR" to "Oregon", "PA" to "Pennsylvania", "RI" to "Rhode Island", "SC" to "South Carolina",
    "SD" to "South Dakota", "TN" to "Tennessee", "TX" to "Texas", "UT" to "Utah",
    "VT" to "Vermont", "VA" to "Virginia", "WA" to "Washington", "WV" to "West Virginia",
    "WI" to "Wisconsin", "WY" to "Wyoming",
    "DC" to "District of Columbia", "AS" to "American Samoa", "GU" to "Guam",
    "MP" to "Northern Mariana Islands", "PR" to "Puerto Rico", "VI" to "U.S. Virgin Islands",
)
