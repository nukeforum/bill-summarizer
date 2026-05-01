package com.billsummarizer.ui.billdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.billsummarizer.data.model.Bill
import com.billsummarizer.share.LlmShareHelper
import com.billsummarizer.share.LlmTarget
import com.billsummarizer.ui.components.OutcomeChip
import com.billsummarizer.ui.util.formatBillRef
import com.billsummarizer.ui.util.formatDate
import com.billsummarizer.ui.util.openInCustomTab
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillDetailScreen(
    billId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BillDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val fullTextState by viewModel.fullTextState.collectAsStateWithLifecycle()

    LaunchedEffect(billId) { viewModel.load(billId) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by remember { mutableStateOf(false) }

    val successBill = (uiState as? BillDetailUiState.Success)?.bill

    fun resolveBody(useFullText: Boolean): String? = when {
        useFullText -> (fullTextState as? FullTextState.Loaded)?.text
        else -> successBill?.summaryCrs
    }

    fun closeSheet(after: () -> Unit = {}) {
        scope.launch {
            sheetState.hide()
            showSheet = false
            viewModel.resetFullText()
            after()
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { DetailTopBar(state = uiState, onBack = onBack, scrollBehavior = scrollBehavior) },
        floatingActionButton = {
            if (successBill != null) {
                ExtendedFloatingActionButton(
                    text = { Text("Summarize with AI") },
                    icon = {},
                    onClick = { showSheet = true },
                )
            }
        },
    ) { innerPadding ->
        when (val state = uiState) {
            BillDetailUiState.Loading -> CenteredMessage(
                innerPadding = innerPadding,
                text = "Loading bill…",
                showSpinner = true,
            )
            is BillDetailUiState.Error -> CenteredMessage(
                innerPadding = innerPadding,
                text = "Couldn't load bill:\n${state.message}",
            )
            is BillDetailUiState.Success -> BillDetailContent(
                bill = state.bill,
                innerPadding = innerPadding,
            )
        }
    }

    if (showSheet && successBill != null) {
        SummarizeBottomSheet(
            sheetState = sheetState,
            fullTextState = fullTextState,
            hasHtmlText = !successBill.textUrlHtml.isNullOrBlank(),
            onDismiss = { closeSheet() },
            onIncludeFullTextChange = { include ->
                if (include) viewModel.fetchFullText() else viewModel.resetFullText()
            },
            onShareToTarget = { target, useFullText ->
                val payload = LlmShareHelper.buildPrompt(successBill, resolveBody(useFullText))
                closeSheet { LlmShareHelper.shareTo(context, target, payload) }
            },
            onShareToOther = { useFullText ->
                val payload = LlmShareHelper.buildPrompt(successBill, resolveBody(useFullText))
                closeSheet { LlmShareHelper.shareToOther(context, payload) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(
    state: BillDetailUiState,
    onBack: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (title, subtitle) = when (state) {
        is BillDetailUiState.Success -> {
            val bill = state.bill
            formatBillRef(bill.type, bill.number) to (bill.shortTitle ?: bill.title)
        }
        else -> "Bill" to null
    }
    LargeTopAppBar(
        title = {
            Column {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun BillDetailContent(bill: Bill, innerPadding: PaddingValues) {
    val context = LocalContext.current
    val fullTextUrl = bill.textUrlHtml ?: bill.congressGovUrl

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Section(title = "Status") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutcomeChip(outcome = bill.outcome)
                Text(
                    text = formatDate(bill.latestAction.date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(text = bill.latestAction.text, style = MaterialTheme.typography.bodyMedium)
        }

        Section(title = "Sponsor") {
            Text(
                text = "${bill.sponsor.name} (${bill.sponsor.party}-${bill.sponsor.state})",
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Section(title = "Summary") {
            val summary = bill.summaryCrs
            if (summary.isNullOrBlank()) {
                Text(
                    text = "No official summary available yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val annotated = remember(summary) { AnnotatedString.fromHtml(summary) }
                Text(text = annotated, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Section(title = "Full text") {
            FilledTonalButton(onClick = { openInCustomTab(context, fullTextUrl) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(text = "Open full text")
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

@Composable
private fun CenteredMessage(innerPadding: PaddingValues, text: String, showSpinner: Boolean = false) {
    Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (showSpinner) CircularProgressIndicator()
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
