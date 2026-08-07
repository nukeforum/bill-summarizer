package com.informedcitizen.ui.billdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Full-screen in-app reader for a bill's source text.
 *
 * The persona blocker in issue #98 was that the only path to a bill's full text
 * was an outbound hand-off to congress.gov's *landing page*
 * ([Bill.congressGovUrl][com.informedcitizen.pipeline.model.Bill.congressGovUrl]),
 * which is Cloudflare-guarded (HTTP 403 / interstitial). The direct GPO text
 * file ([Bill.textUrlHtml][com.informedcitizen.pipeline.model.Bill.textUrlHtml])
 * is *not* guarded and is already fetched, stripped to plain text and held in
 * [FullTextState] by [BillDetailViewModel.fetchFullText]. This reader renders
 * that state, so a citizen can read the source language without ever leaving the
 * app — independent of the external site's verification screens. "Open on
 * congress.gov" remains as a secondary escape hatch, not the only route.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullTextReader(
    title: String,
    state: FullTextState,
    onRetry: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(text = READER_TITLE, style = MaterialTheme.typography.titleMedium)
                            if (title.isNotBlank()) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = CLOSE_LABEL)
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenInBrowser) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = OPEN_IN_BROWSER_LABEL,
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            FullTextReaderContent(
                state = state,
                onRetry = onRetry,
                onOpenInBrowser = onOpenInBrowser,
                contentPadding = innerPadding,
            )
        }
    }
}

@Composable
internal fun FullTextReaderContent(
    state: FullTextState,
    onRetry: () -> Unit,
    onOpenInBrowser: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    when (state) {
        FullTextState.Idle, FullTextState.Loading -> Box(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator()
                Text(
                    text = LOADING_LABEL,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is FullTextState.Loaded -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 24.dp + contentPadding.calculateBottomPadding(),
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = SOURCE_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))
            // GPO bill text is whitespace-formatted (centered headings, indented
            // clauses); a monospace family keeps that alignment readable.
            Text(
                text = state.text,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
        }

        is FullTextState.Error -> Box(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "$ERROR_LABEL\n${state.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                FilledTonalButton(onClick = onRetry) { Text(RETRY_LABEL) }
                OutlinedButton(onClick = onOpenInBrowser) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(OPEN_IN_BROWSER_LABEL)
                }
            }
        }
    }
}

internal const val READER_TITLE = "Full text"
internal const val CLOSE_LABEL = "Close"
internal const val OPEN_IN_BROWSER_LABEL = "Open on congress.gov"
internal const val LOADING_LABEL = "Loading bill text…"
internal const val SOURCE_NOTE =
    "Official source text from the U.S. Government Publishing Office — no AI account needed."
internal const val ERROR_LABEL = "Couldn't load the bill text."
internal const val RETRY_LABEL = "Try again"
