package com.informedcitizen.ui.datasources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.informedcitizen.data.byok.ByokArtifact
import com.informedcitizen.data.byok.ByokFetchOrchestrator
import com.informedcitizen.data.byok.ByokFetchScheduler
import com.informedcitizen.data.byok.ByokFetchTracker
import com.informedcitizen.data.byok.ByokKeyStore
import com.informedcitizen.data.byok.ByokKeyValidator
import com.informedcitizen.data.byok.KeyValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

/** Save-button / key state machine for the Data sources screen. */
sealed interface KeyUiState {
    /** No key stored, input empty or unvalidated. */
    data object Absent : KeyUiState

    /** A key is stored and was valid when last checked. */
    data object Stored : KeyUiState

    data object Checking : KeyUiState

    data class Rejected(val message: String) : KeyUiState

    data class Unreachable(val message: String) : KeyUiState
}

data class ArtifactStatusUi(
    val artifact: ByokArtifact,
    val label: String,
    val lastSuccessText: String?,
)

data class DataSourcesUiState(
    val keyInput: String = "",
    val keyState: KeyUiState = KeyUiState.Absent,
    val fetching: Boolean = false,
    val fetchMessage: String? = null,
    val artifacts: List<ArtifactStatusUi> = emptyList(),
)

@HiltViewModel
class DataSourcesViewModel @Inject constructor(
    private val keyStore: ByokKeyStore,
    private val validator: ByokKeyValidator,
    private val orchestrator: ByokFetchOrchestrator,
    private val tracker: ByokFetchTracker,
    private val scheduler: ByokFetchScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(DataSourcesUiState())
    val state: StateFlow<DataSourcesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val hasKey = keyStore.currentCongressApiKey() != null
            _state.update {
                it.copy(keyState = if (hasKey) KeyUiState.Stored else KeyUiState.Absent)
            }
            refreshArtifactStatus()
        }
    }

    fun onKeyInputChange(value: String) {
        _state.update { it.copy(keyInput = value) }
    }

    /** Validate the entered key live, then store it and start the daily tick. */
    fun onSaveKey() {
        val key = _state.value.keyInput.trim()
        if (key.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(keyState = KeyUiState.Checking) }
            when (val result = validator.validateCongressKey(key)) {
                is KeyValidationResult.Valid -> {
                    keyStore.setCongressApiKey(key)
                    scheduler.ensureScheduled()
                    _state.update { it.copy(keyState = KeyUiState.Stored, keyInput = "") }
                }
                is KeyValidationResult.Invalid -> _state.update {
                    it.copy(keyState = KeyUiState.Rejected("Congress.gov rejected this key (HTTP ${result.httpStatus})."))
                }
                is KeyValidationResult.Unreachable -> _state.update {
                    it.copy(keyState = KeyUiState.Unreachable("Couldn't verify the key: ${result.message}"))
                }
            }
        }
    }

    fun onClearKey() {
        viewModelScope.launch {
            keyStore.setCongressApiKey(null)
            scheduler.cancel()
            _state.update { it.copy(keyState = KeyUiState.Absent, fetchMessage = null) }
        }
    }

    /** Manual refresh of all three artifacts, regardless of cadence. */
    fun onFetchNow() {
        if (_state.value.fetching) return
        viewModelScope.launch {
            _state.update { it.copy(fetching = true, fetchMessage = null) }
            val now = System.currentTimeMillis()
            val outcomes = buildList {
                orchestrator.fetchBills()
                    .onSuccess { tracker.recordSuccess(ByokArtifact.BILLS, now) }
                    .fold(
                        onSuccess = { add("Bills: $it") },
                        onFailure = { add("Bills failed: ${it.message}") },
                    )
                orchestrator.fetchMembersIndex()
                    .onSuccess { tracker.recordSuccess(ByokArtifact.MEMBERS, now) }
                    .fold(
                        onSuccess = { add("Members: $it") },
                        onFailure = { add("Members failed: ${it.message}") },
                    )
                orchestrator.fetchCalendar()
                    .onSuccess { tracker.recordSuccess(ByokArtifact.CALENDAR, now) }
                    .fold(
                        onSuccess = { add("Calendar: $it chambers") },
                        onFailure = { add("Calendar failed: ${it.message}") },
                    )
            }
            refreshArtifactStatus()
            _state.update {
                it.copy(fetching = false, fetchMessage = outcomes.joinToString("  ·  "))
            }
        }
    }

    private suspend fun refreshArtifactStatus() {
        val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        val statuses = ByokArtifact.entries.map { artifact ->
            val last = tracker.lastSuccessMillis(artifact).firstOrNull()
            ArtifactStatusUi(
                artifact = artifact,
                label = when (artifact) {
                    ByokArtifact.BILLS -> "Bills (refreshed daily)"
                    ByokArtifact.MEMBERS -> "Representatives (refreshed weekly)"
                    ByokArtifact.CALENDAR -> "Session calendar (refreshed weekly)"
                },
                lastSuccessText = last?.let { format.format(Date(it)) },
            )
        }
        _state.update { it.copy(artifacts = statuses) }
    }
}
