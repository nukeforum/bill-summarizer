package com.informedcitizen.ui.reps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.informedcitizen.pipeline.model.Member
import com.informedcitizen.ui.components.MemberRowWithHelp
import com.informedcitizen.ui.components.availableContactMethods
import com.informedcitizen.ui.util.dialPhone
import com.informedcitizen.ui.util.openInCustomTab

@Composable
fun RepsListScreen(
    onMemberClick: (String) -> Unit,
    onChangeLocation: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RepsListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    RepsListContent(
        state = state,
        modifier = modifier,
        onMemberClick = onMemberClick,
        onChangeLocation = onChangeLocation,
        onDeleteSavedReps = viewModel::deleteSavedReps,
        onCallPhone = { phone -> dialPhone(context, phone) },
        onOpenUrl = { url -> openInCustomTab(context, url) },
    )
}

@Composable
internal fun RepsListContent(
    state: RepsListUiState,
    onMemberClick: (String) -> Unit,
    onChangeLocation: () -> Unit,
    onDeleteSavedReps: () -> Unit,
    onCallPhone: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    var helpDialogMember by remember { mutableStateOf<Member?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = "Your representatives",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Start,
            )

            if (state is RepsListUiState.Loaded) {
                IconButton(onClick = onDeleteSavedReps) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            }
        }

        when (state) {
            RepsListUiState.Loading -> CircularProgressIndicator()
            RepsListUiState.NoLocation ->
                Text("Set your location to see your representatives.")

            RepsListUiState.StaleSavedReps -> {
                Text(
                    "Your saved representatives aren't in the current Congress.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "This usually means a new election cycle or redistricting. Pick again to refresh them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onChangeLocation) { Text("Update my location") }
            }

            is RepsListUiState.Loaded -> {
                Text(
                    "Senators",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (state.senators.isEmpty()) {
                    Text(
                        "Your senators' data can't be located",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    state.senators.forEach { m ->
                        MemberRowWithHelp(
                            member = m,
                            onClick = { onMemberClick(m.bioguideId) },
                            onCallPhone = onCallPhone,
                            onOpenContactForm = onOpenUrl,
                            onOpenWebsite = onOpenUrl,
                            onShowHelp = { helpDialogMember = m },
                        )
                    }
                }

                Text(
                    "House Representatives",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (state.house.isEmpty()) {
                    Text(
                        "Your House representatives' data can't be located",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    state.house.forEach { m ->
                        MemberRowWithHelp(
                            member = m,
                            onClick = { onMemberClick(m.bioguideId) },
                            onCallPhone = onCallPhone,
                            onOpenContactForm = onOpenUrl,
                            onOpenWebsite = onOpenUrl,
                            onShowHelp = { helpDialogMember = m },
                        )
                    }
                }
            }

            is RepsListUiState.Error ->
                Text("Couldn't load: ${state.message}", color = MaterialTheme.colorScheme.error)
        }

        helpDialogMember?.let { member ->
            ContactHelpDialog(
                member = member,
                methods = member.availableContactMethods(),
                onDismiss = { helpDialogMember = null },
            )
        }
    }
}
