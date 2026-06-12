package com.informedcitizen.ui.datasources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.informedcitizen.ui.util.openInCustomTab

/**
 * Where users plug in their own Congress.gov API key so the app
 * fetches data directly from the source instead of (only) the
 * project's published JSON. See TODO "BYOK".
 */
const val API_KEY_GUIDE_URL: String =
    "https://github.com/nukeforum/bill-summarizer/blob/main/pipeline/docs/api-keys.md"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSourcesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: DataSourcesViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Data sources") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        DataSourcesContent(
            state = state,
            innerPadding = innerPadding,
            onKeyInputChange = viewModel::onKeyInputChange,
            onSaveKey = viewModel::onSaveKey,
            onClearKey = viewModel::onClearKey,
            onFetchNow = viewModel::onFetchNow,
            onOpenGuide = { openInCustomTab(context, API_KEY_GUIDE_URL) },
        )
    }
}

@Composable
private fun DataSourcesContent(
    state: DataSourcesUiState,
    innerPadding: PaddingValues,
    onKeyInputChange: (String) -> Unit,
    onSaveKey: () -> Unit,
    onClearKey: () -> Unit,
    onFetchNow: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "By default this app reads data the project publishes for everyone. " +
                "With your own free Congress.gov API key, the app fetches bills, " +
                "representatives, and the session calendar directly from the source " +
                "on your schedule.",
            style = MaterialTheme.typography.bodyMedium,
        )

        TextButton(onClick = onOpenGuide) {
            Text("How to get an API key")
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.width(16.dp),
            )
        }

        when (state.keyState) {
            KeyUiState.Stored -> StoredKeyBlock(onClearKey)
            else -> KeyEntryBlock(state, onKeyInputChange, onSaveKey)
        }

        if (state.keyState == KeyUiState.Stored) {
            HorizontalDivider()
            FetchBlock(state, onFetchNow)
        }
    }
}

@Composable
private fun StoredKeyBlock(onClearKey: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Congress.gov key saved ✓",
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedButton(onClick = onClearKey) { Text("Remove key") }
    }
}

@Composable
private fun KeyEntryBlock(
    state: DataSourcesUiState,
    onKeyInputChange: (String) -> Unit,
    onSaveKey: () -> Unit,
) {
    OutlinedTextField(
        value = state.keyInput,
        onValueChange = onKeyInputChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Congress.gov API key") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        isError = state.keyState is KeyUiState.Rejected,
        supportingText = {
            when (val keyState = state.keyState) {
                is KeyUiState.Rejected -> Text(keyState.message)
                is KeyUiState.Unreachable -> Text(keyState.message)
                else -> Text("Verified with a test request before saving; stored encrypted on this device.")
            }
        },
    )
    Button(
        onClick = onSaveKey,
        enabled = state.keyInput.isNotBlank() && state.keyState != KeyUiState.Checking,
    ) {
        if (state.keyState == KeyUiState.Checking) {
            CircularProgressIndicator(modifier = Modifier.width(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Checking…")
        } else {
            Text("Verify and save")
        }
    }
}

@Composable
private fun FetchBlock(
    state: DataSourcesUiState,
    onFetchNow: () -> Unit,
) {
    Text("Your data", style = MaterialTheme.typography.titleMedium)
    for (artifact in state.artifacts) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(artifact.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                artifact.lastSuccessText ?: "never fetched",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Button(onClick = onFetchNow, enabled = !state.fetching) {
        if (state.fetching) {
            CircularProgressIndicator(modifier = Modifier.width(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Fetching…")
        } else {
            Text("Fetch now")
        }
    }
    state.fetchMessage?.let {
        Text(it, style = MaterialTheme.typography.bodySmall)
    }
    Text(
        "Background refresh runs daily for bills and weekly for representatives " +
            "and the calendar, on Wi-Fi or mobile data. Historical bill archives " +
            "are not fetched on-device.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
