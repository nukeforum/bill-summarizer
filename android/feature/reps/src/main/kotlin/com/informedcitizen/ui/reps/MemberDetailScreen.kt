package com.informedcitizen.ui.reps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.informedcitizen.pipeline.model.MemberLegislationItem
import com.informedcitizen.pipeline.model.MemberVoteRow
import com.informedcitizen.pipeline.model.VotePosition
import com.informedcitizen.data.util.congressGovUrlFor
import com.informedcitizen.ui.components.BillSearchField
import com.informedcitizen.ui.components.MemberCard
import com.informedcitizen.ui.components.MemberLegislationRow
import com.informedcitizen.ui.util.dialPhone
import com.informedcitizen.ui.util.openInCustomTab
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberDetailScreen(
    bioguideId: String,
    onBack: () -> Unit,
    onBillClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MemberDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(bioguideId) { viewModel.load(bioguideId) }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showHelp by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.member?.name ?: "Member") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showHelp = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "Contact options help",
                        )
                    }
                },
            )
        },
    ) { padding ->
        MemberDetailContent(
            state = state,
            innerPadding = padding,
            onLegislationClick = { item ->
                if (viewModel.isInLocalCache(item.id)) {
                    onBillClick(item.id)
                } else {
                    val url = congressGovUrlFor(item.type, item.number, item.congress)
                    openInCustomTab(context, url)
                }
            },
            onVoteClick = { row ->
                val billId = row.billId
                val type = row.type
                val number = row.number
                if (billId != null && type != null && number != null) {
                    if (viewModel.isInLocalCache(billId)) {
                        onBillClick(billId)
                    } else {
                        openInCustomTab(context, congressGovUrlFor(type, number, row.congress))
                    }
                }
            },
            onCallPhone = { phone -> dialPhone(context, phone) },
            onOpenUrl = { url -> openInCustomTab(context, url) },
            onSearchQueryChange = viewModel::setSearchQuery,
        )
    }

    if (showHelp) {
        ContactHelpDialog(onDismiss = { showHelp = false })
    }
}

@Composable
internal fun MemberDetailContent(
    state: MemberDetailUiState,
    innerPadding: PaddingValues,
    onLegislationClick: (MemberLegislationItem) -> Unit,
    onCallPhone: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onVoteClick: (MemberVoteRow) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
) {
    // Reset to the member's default tab when a different member loads
    // (bioguideId changes); keep the user's tab choice within one member.
    var tab by remember(state.member?.bioguideId) { mutableIntStateOf(defaultMemberDetailTab(state)) }

    Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator()
            state.member != null -> {
                MemberCard(
                    member = state.member,
                    onClick = {},
                    onCallPhone = onCallPhone,
                    onOpenContactForm = onOpenUrl,
                    onOpenWebsite = onOpenUrl,
                    onOpenSocial = onOpenUrl,
                    upForElection = state.ballotBadge,
                )
                // The search field only filters the legislation tabs, so
                // hide it on the votes tab where it would be a no-op.
                if (tab != TAB_VOTES) {
                    BillSearchField(
                        query = state.searchQuery,
                        onQueryChange = onSearchQueryChange,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                PrimaryTabRow(selectedTabIndex = tab) {
                    // Voting record leads: a citizen opens a rep's profile to
                    // see how they actually voted, not just what they sponsored
                    // (issue #55).
                    Tab(
                        selected = tab == TAB_VOTES,
                        onClick = { tab = TAB_VOTES },
                        text = { Text("Voting record (${state.recentVotes.size})") },
                    )
                    Tab(
                        selected = tab == TAB_SPONSORED,
                        onClick = { tab = TAB_SPONSORED },
                        text = { Text("Sponsored (${state.filteredSponsored.size})") },
                    )
                    Tab(
                        selected = tab == TAB_COSPONSORED,
                        onClick = { tab = TAB_COSPONSORED },
                        text = { Text("Cosponsored (${state.filteredCosponsored.size})") },
                    )
                }
                if (tab == TAB_VOTES) {
                    VotesTab(votes = state.recentVotes, onVoteClick = onVoteClick)
                } else {
                    val items = if (tab == TAB_SPONSORED) state.filteredSponsored else state.filteredCosponsored
                    if (items.isEmpty()) {
                        val rawItems = if (tab == TAB_SPONSORED) state.sponsored else state.cosponsored
                        Text(
                            if (rawItems.isEmpty()) {
                                "No data yet for this representative."
                            } else {
                                "No bills match \"${state.searchQuery.trim()}\""
                            },
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        LazyColumn {
                            items(items, key = { it.id }) { item ->
                                MemberLegislationRow(item = item, onClick = { onLegislationClick(item) })
                            }
                        }
                    }
                }
            }
            state.errorMessage != null -> Text("Error: ${state.errorMessage}")
        }
    }
}

internal const val TAB_VOTES = 0
internal const val TAB_SPONSORED = 1
internal const val TAB_COSPONSORED = 2

/**
 * The tab a freshly-loaded member profile opens on. Lands on the voting record
 * whenever the member has recorded votes (issue #55 — the see-the-record pillar:
 * a citizen wants their rep's actual Yea/Nay, which the profile previously buried
 * behind the sponsorship tabs), and falls back to Sponsored only when there are
 * no votes so the profile never opens on an empty tab.
 */
internal fun defaultMemberDetailTab(state: MemberDetailUiState): Int =
    if (state.recentVotes.isNotEmpty()) TAB_VOTES else TAB_SPONSORED

@Composable
private fun VotesTab(votes: List<MemberVoteRow>, onVoteClick: (MemberVoteRow) -> Unit) {
    if (votes.isEmpty()) {
        Text(
            "No recorded votes yet for this representative.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        LazyColumn {
            items(votes, key = { it.voteId }) { row ->
                VoteHistoryRow(
                    row = row,
                    // Only bill-tied roll calls navigate; nominations and
                    // quorum calls carry no billId and stay inert.
                    onClick = if (row.billId != null) { { onVoteClick(row) } } else null,
                )
            }
        }
    }
}

@Composable
private fun VoteHistoryRow(
    row: MemberVoteRow,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val base = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Column(
        modifier = base
            .fillMaxWidth()
            .semantics { contentDescription = voteRowDescription(row) }
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(voteBillLabel(row), style = MaterialTheme.typography.labelMedium)
            PositionChip(row.position)
        }
        row.shortTitle?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        }
        Text(
            "${formatVoteDate(row.date)} · ${row.question} · ${row.result}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun PositionChip(position: VotePosition, modifier: Modifier = Modifier) {
    val container = when (position) {
        VotePosition.YEA -> MaterialTheme.colorScheme.primaryContainer
        VotePosition.NAY -> MaterialTheme.colorScheme.errorContainer
        VotePosition.PRESENT, VotePosition.NOT_VOTING -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when (position) {
        VotePosition.YEA -> MaterialTheme.colorScheme.onPrimaryContainer
        VotePosition.NAY -> MaterialTheme.colorScheme.onErrorContainer
        VotePosition.PRESENT, VotePosition.NOT_VOTING -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = positionLabel(position),
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

internal fun positionLabel(position: VotePosition): String = when (position) {
    VotePosition.YEA -> "Yea"
    VotePosition.NAY -> "Nay"
    VotePosition.PRESENT -> "Present"
    VotePosition.NOT_VOTING -> "Not voting"
}

/** The bill's short label (e.g. "HR 7008") or, absent a bill, the roll-call question. */
internal fun voteBillLabel(row: MemberVoteRow): String {
    val type = row.type
    val number = row.number
    return if (!type.isNullOrBlank() && !number.isNullOrBlank()) {
        "${type.uppercase()} $number"
    } else {
        row.question
    }
}

private val VOTE_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy")

/** "2026-07-22" → "Jul 22, 2026"; passes the raw string through if unparseable. */
internal fun formatVoteDate(date: String): String =
    runCatching { LocalDate.parse(date).format(VOTE_DATE_FORMAT) }.getOrDefault(date)

/** Merged, abbreviation-free screen-reader description for one vote row. */
internal fun voteRowDescription(row: MemberVoteRow): String = buildString {
    append(voteBillLabel(row))
    row.shortTitle?.takeIf { it.isNotBlank() }?.let { append(", $it") }
    append(". ${formatVoteDate(row.date)}. ${row.question}. Result: ${row.result}.")
    append(" Voted ${positionLabel(row.position).lowercase()}.")
}
