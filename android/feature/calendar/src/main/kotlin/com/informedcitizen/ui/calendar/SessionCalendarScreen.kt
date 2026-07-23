package com.informedcitizen.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.informedcitizen.pipeline.model.ElectionCalendar
import com.informedcitizen.pipeline.model.Member
import com.informedcitizen.pipeline.model.SessionCalendar
import com.informedcitizen.domain.session.Chamber
import com.informedcitizen.domain.session.ChamberStatus
import com.informedcitizen.domain.session.statusOn
import com.informedcitizen.ui.util.openInCustomTab
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FULL_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.US)
private val DOW_MON_DAY: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
private val MONTH_LABEL: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
private val MON_DAY: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val ELECTION_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionCalendarScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onMemberClick: (String) -> Unit = {},
    viewModel: SessionCalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val electionCalendar by viewModel.electionCalendar.collectAsStateWithLifecycle()
    val savedReps by viewModel.savedReps.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Congress calendar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        SessionCalendarContent(
            state = state,
            innerPadding = innerPadding,
            onRetry = viewModel::retry,
            electionCalendar = electionCalendar,
            savedReps = savedReps,
            onMemberClick = onMemberClick,
            onOpenSource = { url -> openInCustomTab(context, url) },
        )
    }
}

@Composable
internal fun SessionCalendarContent(
    state: SessionCalendarUiState,
    innerPadding: PaddingValues,
    onRetry: () -> Unit,
    today: LocalDate = LocalDate.now(),
    electionCalendar: ElectionCalendar? = null,
    savedReps: List<Member> = emptyList(),
    onMemberClick: (String) -> Unit = {},
    onOpenSource: (String) -> Unit,
) {
    when (state) {
        SessionCalendarUiState.Loading -> Centered(innerPadding) { CircularProgressIndicator() }
        is SessionCalendarUiState.Error -> ErrorBlock(innerPadding, state.message, onRetry = onRetry)
        is SessionCalendarUiState.Success ->
            SuccessBody(
                innerPadding = innerPadding,
                calendar = state.calendar,
                today = today,
                electionCalendar = electionCalendar,
                savedReps = savedReps,
                onMemberClick = onMemberClick,
                onOpenSource = onOpenSource,
            )
    }
}

@Composable
private fun Centered(innerPadding: PaddingValues, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}

@Composable
private fun ErrorBlock(innerPadding: PaddingValues, message: String, onRetry: () -> Unit) {
    Centered(innerPadding) {
        Text(
            text = "Couldn't load calendar:\n$message",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Text("Retry")
        }
    }
}

@Composable
private fun SuccessBody(
    innerPadding: PaddingValues,
    calendar: SessionCalendar,
    today: LocalDate,
    electionCalendar: ElectionCalendar?,
    savedReps: List<Member>,
    onMemberClick: (String) -> Unit,
    onOpenSource: (String) -> Unit,
) {
    val statuses = calendar.statusOn(today)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TodaySummaryCard(today = today, statuses = statuses)
        if (electionCalendar != null) {
            UpcomingElectionsSection(
                calendar = electionCalendar,
                today = today,
                savedReps = savedReps,
            )
            OnYourBallotSection(
                savedReps = savedReps,
                calendar = electionCalendar,
                today = today,
                onMemberClick = onMemberClick,
            )
        }
        CalendarGridSection(calendar = calendar, today = today)
        ComingUpSection(calendar = calendar, today = today)
        SourceFooter(calendar, onOpenSource)
    }
}

/**
 * Issue #33's pillar-2 bridge on the elections surface: the user's saved
 * representatives who appear on an upcoming general-election ballot, each row
 * tapping through to that member's detail (and, via #22, their voting record).
 * The pairing is computed by [savedRepsOnBallot] from already-cached artifacts,
 * so a rep with no dated upcoming general on record is simply omitted; the
 * whole section hides when no saved rep matches — never a guessed date, never
 * an empty box.
 */
@Composable
private fun OnYourBallotSection(
    savedReps: List<Member>,
    calendar: ElectionCalendar,
    today: LocalDate,
    onMemberClick: (String) -> Unit,
) {
    val ballotReps = savedRepsOnBallot(savedReps, calendar, today)
    if (ballotReps.isEmpty()) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("On your ballot", style = MaterialTheme.typography.titleMedium)
            ballotReps.forEach { BallotRepRow(it, onMemberClick) }
        }
    }
}

@Composable
private fun BallotRepRow(ballotRep: BallotRep, onMemberClick: (String) -> Unit) {
    val member = ballotRep.member
    val formattedDate = ballotRep.election.date.format(ELECTION_DATE)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onMemberClick(member.bioguideId) }
            .padding(vertical = 4.dp)
            .clearAndSetSemantics {
                contentDescription = ballotRepRowDescription(ballotRep, formattedDate)
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = member.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = ballotRepSubtitle(member),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = ballotRep.election.countdownText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Issue #24's "when will I vote" surface: the soonest upcoming elections
 * with a days-remaining countdown. The scope is the user's state when a single
 * one can be derived from [savedReps] (the same [VoteGovLinks.registrationStateCode]
 * rule the "Register & vote" button uses), falling back to the nationwide
 * federal general when zero or multiple states are known — so a single-state
 * user also sees their state's primary rows, which is where issue #36's
 * registration-deadline block (attached per state) can appear. An explicit
 * [stateCode] overrides the derivation (previews/tests). Issue #25 adds the
 * always-actionable "Register & vote" affordance below the rows: registration
 * is useful year-round, so the card renders even when nothing is upcoming,
 * replacing the rows with a quiet "no upcoming elections" line but keeping the
 * button. The app collects no address or PII.
 */
@Composable
private fun UpcomingElectionsSection(
    calendar: ElectionCalendar,
    today: LocalDate,
    savedReps: List<Member>,
    stateCode: String? = null,
) {
    val effectiveState = stateCode ?: VoteGovLinks.registrationStateCode(savedReps)
    val elections = upcomingElections(calendar, today, effectiveState)
    val registrationUrl = VoteGovLinks.registrationUrl(effectiveState)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Upcoming elections", style = MaterialTheme.typography.titleMedium)
            if (elections.isEmpty()) {
                Text(
                    text = "No upcoming elections in the calendar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                elections.forEach { ElectionRow(it, today) }
                Text(
                    text = calendar.source,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RegisterAndVoteAffordance(registrationUrl = registrationUrl)
        }
    }
}

/**
 * Issue #25's "Register & vote" deep-link: a full-width button that hands
 * [registrationUrl] (a vote.gov page) to the browser, with an always-visible
 * caption disclosing the external handoff. Button + caption merge into one
 * semantic node so a screen reader announces the disclosure with the action.
 */
@Composable
private fun RegisterAndVoteAffordance(registrationUrl: String) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = VoteGovLinks.ACTION_SEMANTICS },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FilledTonalButton(
            onClick = { openInCustomTab(context, registrationUrl) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Register & vote")
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = VoteGovLinks.HANDOFF_CAPTION,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ElectionRow(election: UpcomingElection, today: LocalDate) {
    val formattedDate = election.date.format(ELECTION_DATE)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    contentDescription = electionRowDescription(election, formattedDate)
                },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = electionRowLabel(election),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = election.countdownText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        registrationDeadlineDisplay(election.event.registration, today)?.let { display ->
            RegistrationDeadlineBlock(display)
        }
    }
}

/**
 * Issue #36's registration-deadline block under an election row: the countdown
 * to the state's registration deadline (or a "closed" note once it has passed),
 * with an urgent (≤ 7 days) caution treatment mirroring #27's data-freshness
 * line — an amber warning icon and error color so urgency reads without relying
 * on color alone. Only ever rendered when [registrationDeadlineDisplay] returns
 * a non-null display (a published, parseable deadline), so an omitted or
 * unverified deadline shows nothing rather than a guessed state.
 *
 * The static headline + notes carry one merged TalkBack description
 * ([registrationDeadlineDescription]); the optional per-method "Show details"
 * expander sits outside that semantics group so it stays an actionable node.
 */
@Composable
private fun RegistrationDeadlineBlock(display: RegistrationDeadlineDisplay) {
    val urgent = display is RegistrationDeadlineDisplay.Open && display.urgent
    val headlineColor =
        if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    contentDescription = registrationDeadlineDescription(display)
                },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (urgent) Icons.Outlined.WarningAmber else Icons.Outlined.HowToVote,
                    contentDescription = null,
                    tint = headlineColor,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = when (display) {
                        is RegistrationDeadlineDisplay.Open -> registrationOpenHeadline(display)
                        is RegistrationDeadlineDisplay.Closed -> registrationClosedText(display)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = headlineColor,
                )
            }
            if (display is RegistrationDeadlineDisplay.Open) {
                registrationPartialClosedText(display)?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (display.sameDay) {
                Text(
                    text = SAME_DAY_REGISTRATION_NOTE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (display is RegistrationDeadlineDisplay.Open && display.methods.size > 1) {
            var expanded by remember { mutableStateOf(false) }
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(if (expanded) "Hide deadlines" else "Show all deadlines")
            }
            if (expanded) {
                display.methods.forEach { method ->
                    Text(
                        text = registrationDetailLine(method),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TodaySummaryCard(today: LocalDate, statuses: List<ChamberStatus>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = today.format(FULL_DATE),
                style = MaterialTheme.typography.titleMedium,
            )
            statuses.forEach { status ->
                Text(
                    text = chamberLine(status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun chamberLine(status: ChamberStatus): String {
    val name = when (status.chamber) {
        Chamber.HOUSE -> "House"
        Chamber.SENATE -> "Senate"
    }
    val next = status.nextSessionDay
    return when {
        status.inSessionToday -> "$name: in session today"
        next != null ->
            "$name: on recess — returns ${next.format(DOW_MON_DAY)}"
        else -> "$name: session has ended"
    }
}

@Composable
private fun CalendarGridSection(calendar: SessionCalendar, today: LocalDate) {
    var selected by remember { mutableStateOf(today) }
    val houseDays = calendar.chambers["house"]?.sessionDays.orEmpty()
        .map(LocalDate::parse).toSet()
    val senateDays = calendar.chambers["senate"]?.sessionDays.orEmpty()
        .map(LocalDate::parse).toSet()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Legend()
        listOf(YearMonth.from(today), YearMonth.from(today).plusMonths(1)).forEach { ym ->
            MonthGrid(
                yearMonth = ym,
                today = today,
                selected = selected,
                onSelect = { selected = it },
                houseDays = houseDays,
                senateDays = senateDays,
            )
        }
        SelectedDayLine(
            date = selected,
            inHouse = selected in houseDays,
            inSenate = selected in senateDays,
        )
    }
}

@Composable
private fun Legend() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendDot(MaterialTheme.colorScheme.primary, "House")
        LegendDot(MaterialTheme.colorScheme.tertiary, "Senate")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .height(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
                .padding(horizontal = 8.dp),
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MonthGrid(
    yearMonth: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    houseDays: Set<LocalDate>,
    senateDays: Set<LocalDate>,
) {
    val firstOfMonth = yearMonth.atDay(1)
    val daysInMonth = yearMonth.lengthOfMonth()
    val leadingBlanks = firstOfMonth.dayOfWeek.value % 7  // Sunday = 0
    val totalCells = leadingBlanks + daysInMonth
    val rowCount = (totalCells + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = yearMonth.format(MONTH_LABEL),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        repeat(rowCount) { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(7) { col ->
                    val cellIndex = row * 7 + col
                    val dayNumber = cellIndex - leadingBlanks + 1
                    if (dayNumber in 1..daysInMonth) {
                        val date = yearMonth.atDay(dayNumber)
                        DayCell(
                            modifier = Modifier.weight(1f),
                            date = date,
                            isToday = date == today,
                            isSelected = date == selected,
                            inHouse = date in houseDays,
                            inSenate = date in senateDays,
                            onClick = { onSelect(date) },
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    modifier: Modifier,
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    inHouse: Boolean,
    inSenate: Boolean,
    onClick: () -> Unit,
) {
    val houseColor = if (inHouse) MaterialTheme.colorScheme.primary else Color.Transparent
    val senateColor = if (inSenate) MaterialTheme.colorScheme.tertiary else Color.Transparent
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .aspectRatio(0.85f)
            .clip(RoundedCornerShape(6.dp))
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else outlineColor,
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .border(
                        width = if (isToday) 1.dp else 0.dp,
                        color = if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = CircleShape,
                    )
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Box(
                modifier = Modifier
                    .height(3.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(houseColor),
            )
            Box(
                modifier = Modifier
                    .height(3.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(senateColor),
            )
        }
    }
}

@Composable
private fun SelectedDayLine(date: LocalDate, inHouse: Boolean, inSenate: Boolean) {
    val house = if (inHouse) "House in session" else "House on recess"
    val senate = if (inSenate) "Senate in session" else "Senate on recess"
    Text(
        text = "${date.format(DOW_MON_DAY)}: $house, $senate.",
        style = MaterialTheme.typography.bodyMedium,
    )
}

data class SessionBlock(val start: LocalDate, val end: LocalDate, val dayCount: Int) {
    val rangeText: String
        get() {
            val days = if (dayCount == 1) "1 session day" else "$dayCount session days"
            return if (start == end) "${start.format(MON_DAY)} ($days)"
            else "${start.format(MON_DAY)} – ${end.format(MON_DAY)} ($days)"
        }
}

internal fun upcomingBlocks(
    sessionDays: List<String>,
    today: LocalDate,
    limit: Int = 3,
): List<SessionBlock> {
    val parsed = sessionDays.map(LocalDate::parse).filter { it >= today }
    if (parsed.isEmpty()) return emptyList()
    val blocks = mutableListOf<SessionBlock>()
    var blockStart = parsed.first()
    var blockEnd = parsed.first()
    var count = 1
    for (i in 1 until parsed.size) {
        val prev = parsed[i - 1]
        val cur = parsed[i]
        if (cur.toEpochDay() - prev.toEpochDay() <= 3) {
            blockEnd = cur
            count += 1
        } else {
            blocks += SessionBlock(blockStart, blockEnd, count)
            if (blocks.size >= limit) return blocks
            blockStart = cur
            blockEnd = cur
            count = 1
        }
    }
    blocks += SessionBlock(blockStart, blockEnd, count)
    return blocks.take(limit)
}

@Composable
private fun ComingUpSection(calendar: SessionCalendar, today: LocalDate) {
    val houseBlocks = upcomingBlocks(calendar.chambers["house"]?.sessionDays.orEmpty(), today)
    val senateBlocks = upcomingBlocks(calendar.chambers["senate"]?.sessionDays.orEmpty(), today)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Coming up", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ChamberBlocks(modifier = Modifier.weight(1f), title = "House", blocks = houseBlocks)
                ChamberBlocks(modifier = Modifier.weight(1f), title = "Senate", blocks = senateBlocks)
            }
        }
    }
}

@Composable
private fun ChamberBlocks(modifier: Modifier, title: String, blocks: List<SessionBlock>) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        if (blocks.isEmpty()) {
            Text(
                "No scheduled session days",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            blocks.forEach { Text(it.rangeText, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun SourceFooter(calendar: SessionCalendar, onOpenSource: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Calendar published by the Office of the House Majority Leader and the U.S. Senate.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { onOpenSource(calendar.source.house) }) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
            Text("  House calendar")
        }
        TextButton(onClick = { onOpenSource(calendar.source.senate) }) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
            Text("  Senate calendar")
        }
    }
}
