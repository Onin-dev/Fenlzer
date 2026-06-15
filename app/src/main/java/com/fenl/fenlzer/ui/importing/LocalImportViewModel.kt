package com.fenl.fenlzer.ui.importing

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fenl.fenlzer.importing.local.LocalImportBatchResult
import com.fenl.fenlzer.importing.local.LocalImportProgress
import com.fenl.fenlzer.importing.ImportQueueCoordinator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LocalImportViewModel(
    private val importQueueCoordinator: ImportQueueCoordinator?
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(LocalImportUiState())
    val uiState: StateFlow<LocalImportUiState> = mutableUiState.asStateFlow()

    private var importJob: Job? = null

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty() || mutableUiState.value.isRunning) return
        val coordinator = importQueueCoordinator ?: return

        importJob = viewModelScope.launch {
            mutableUiState.value = LocalImportUiState(
                isRunning = true,
                total = uris.size
            )

            runCatching { coordinator.enqueueLocalImports(uris) }
                .onSuccess { jobIds ->
                    mutableUiState.update { current ->
                        current.copy(
                            isRunning = false,
                            progress = null,
                            currentPercent = null,
                            queuedCount = jobIds.size,
                            message = if (jobIds.size == 1) {
                                "Import queued."
                            } else {
                                "${jobIds.size} imports queued."
                            }
                        )
                    }
                }
                .onFailure { throwable ->
                    mutableUiState.update { current ->
                        current.copy(
                            isRunning = false,
                            message = throwable.localizedMessage
                                ?: "Fenlzer could not queue these files."
                        )
                    }
                }
        }
    }

    fun clearResult() {
        if (mutableUiState.value.isRunning) return
        mutableUiState.value = LocalImportUiState()
    }

    override fun onCleared() {
        importJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(importQueueCoordinator: ImportQueueCoordinator?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LocalImportViewModel(importQueueCoordinator) as T
                }
            }
    }
}

data class LocalImportUiState(
    val isRunning: Boolean = false,
    val progress: LocalImportProgress? = null,
    val currentIndex: Int = 0,
    val total: Int = 0,
    val currentFilename: String? = null,
    val currentPercent: Int? = null,
    val queuedCount: Int = 0,
    val message: String? = null,
    val result: LocalImportBatchResult? = null
)
