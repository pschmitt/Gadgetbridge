package nodomain.freeyourgadget.gadgetbridge.activities.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.activities.debug.data.DatabaseDebugRepository
import javax.inject.Inject

data class DatabaseDebugUiState(
    val version: Int? = null,
    val tables: List<String> = emptyList(),
    val showDeleteOldDatabase: Boolean = false,
    val deleteInProgress: Boolean = false,
)

sealed interface DatabaseDebugEvent {
    data class DatabaseDeleted(val success: Boolean) : DatabaseDebugEvent
    data class OldDatabaseDeleted(val success: Boolean) : DatabaseDebugEvent
}

@HiltViewModel
class DatabaseDebugViewModel @Inject constructor(
    private val repository: DatabaseDebugRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DatabaseDebugUiState())
    val uiState: StateFlow<DatabaseDebugUiState> = _uiState.asStateFlow()

    private val _events = Channel<DatabaseDebugEvent>(Channel.BUFFERED)
    val events: Flow<DatabaseDebugEvent> = _events.receiveAsFlow()

    private var refreshJob: Job? = null

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val overview = repository.getOverview()
            _uiState.update {
                it.copy(
                    version = overview.version,
                    tables = overview.tables,
                    showDeleteOldDatabase = overview.hasOldActivityDatabase,
                )
            }
        }
    }

    fun deleteDatabase() {
        launchDelete {
            _events.send(DatabaseDebugEvent.DatabaseDeleted(repository.deleteDatabase()))
        }
    }

    fun deleteOldDatabase() {
        launchDelete {
            _events.send(DatabaseDebugEvent.OldDatabaseDeleted(repository.deleteOldDatabase()))
            refresh()
        }
    }

    /**
     * Runs [delete], unless another delete is still in progress. A confirmation dialog can already be open when a delete
     * starts, so disabling the preferences is not enough to prevent a second one.
     */
    private fun launchDelete(delete: suspend () -> Unit) {
        if (_uiState.value.deleteInProgress) {
            return
        }
        _uiState.update { it.copy(deleteInProgress = true) }
        viewModelScope.launch {
            try {
                delete()
            } finally {
                _uiState.update { it.copy(deleteInProgress = false) }
            }
        }
    }
}
