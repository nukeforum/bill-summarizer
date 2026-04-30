package com.billsummarizer.ui.billdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.billsummarizer.share.LlmTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummarizeBottomSheet(
    sheetState: SheetState,
    fullTextState: FullTextState,
    hasHtmlText: Boolean,
    onDismiss: () -> Unit,
    onIncludeFullTextChange: (Boolean) -> Unit,
    onShareToTarget: (target: LlmTarget, useFullText: Boolean) -> Unit,
    onShareToOther: (useFullText: Boolean) -> Unit,
) {
    var includeFullText by remember { mutableStateOf(false) }
    val isFetching = fullTextState is FullTextState.Loading
    val fullTextReady = fullTextState is FullTextState.Loaded
    val canShare = !includeFullText || fullTextReady

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Summarize this bill", style = MaterialTheme.typography.headlineSmall)

            LlmTarget.entries.forEach { target ->
                Button(
                    onClick = { onShareToTarget(target, includeFullText) },
                    enabled = canShare,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(target.displayName)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(
                    checked = includeFullText,
                    enabled = hasHtmlText,
                    onCheckedChange = { checked ->
                        includeFullText = checked
                        onIncludeFullTextChange(checked)
                    },
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Include full text (longer)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!hasHtmlText) {
                        Text(
                            text = "No full-text version published yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            when (val s = fullTextState) {
                FullTextState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Fetching full text…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is FullTextState.Error -> Text(
                    text = "Couldn't fetch full text: ${s.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                FullTextState.Idle, is FullTextState.Loaded -> {
                    // Nothing extra to show — buttons handle the loaded case.
                }
            }

            OutlinedButton(
                onClick = { onShareToOther(includeFullText) },
                enabled = canShare,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Other app…")
            }

            Text(
                text = "Opens in your AI app. The bill text and a summary prompt will be ready to paste, or sent automatically if your app supports it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Subtle indicator that the share button is gated on the full-text fetch.
            if (includeFullText && isFetching) {
                Text(
                    text = "Buttons activate once the full text finishes loading.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
