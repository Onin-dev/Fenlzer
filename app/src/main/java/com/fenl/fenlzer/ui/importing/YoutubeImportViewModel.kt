package com.fenl.fenlzer.ui.importing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fenl.fenlzer.importing.youtube.ActiveImportUiItem
import com.fenl.fenlzer.importing.youtube.ImportHistoryFilter
import com.fenl.fenlzer.importing.youtube.ImportHistoryUiItem
import com.fenl.fenlzer.importing.youtube.YoutubeImportCoordinator
import com.fenl.fenlzer.importing.youtube.YoutubeImportItemResult
import com.fenl.fenlzer.importing.youtube.YoutubeImportOutcome
import com.fenl.fenlzer.importing.youtube.YoutubeImportProgress
import com.fenl.fenlzer.importing.youtube.YoutubePlaylistPreview
import com.fenl.fenlzer.importing.youtube.YoutubeSearchResultItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class YoutubeImportViewModel(
    private val coordinator: YoutubeImportCoordinator?
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(YoutubeImportUiState())
    val uiState: StateFlow<YoutubeImportUiState> = mutableUiState.asStateFlow()

    private var searchJob: Job? = null
    private var importJob: Job? = null
    private var playlistPreviewJob: Job? = null
    private var fullHistory: List<ImportHistoryUiItem> = emptyList()

    init {
        coordinator?.let { youtubeCoordinator ->
            viewModelScope.launch {
                youtubeCoordinator.observeActiveImports().collect { activeJobs ->
                    mutableUiState.update { current -> current.copy(activeJobs = activeJobs) }
                }
            }
            viewModelScope.launch {
                youtubeCoordinator.observeImportHistory().collect { history ->
                    fullHistory = history
                    mutableUiState.update { current ->
                        current.copy(history = history.visibleFor(current.historyFilter))
                    }
                }
            }
        }
    }

    fun onQueryChanged(query: String) {
        mutableUiState.update { current ->
            current.copy(query = query, searchError = null)
        }
    }

    fun search() {
        if (mutableUiState.value.isSearching) return
        val coordinator = coordinator
        if (coordinator == null) {
            mutableUiState.update { current ->
                current.copy(searchError = "YouTube import is not available in this build.")
            }
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val query = mutableUiState.value.query
            mutableUiState.update { current ->
                current.copy(
                    isSearching = true,
                    searchError = null,
                    searchResults = emptyList()
                )
            }

            runCatching { coordinator.search(query) }
                .onSuccess { results ->
                    mutableUiState.update { current ->
                        current.copy(
                            isSearching = false,
                            searchResults = results,
                            searchError = if (results.isEmpty()) "No YouTube results found." else null
                        )
                    }
                }
                .onFailure { throwable ->
                    mutableUiState.update { current ->
                        current.copy(
                            isSearching = false,
                            searchError = throwable.localizedMessage
                                ?: "Fenlzer could not search YouTube."
                        )
                    }
                }
        }
    }

    fun onPlaylistUrlChanged(url: String) {
        mutableUiState.update { current ->
            current.copy(
                playlistUrl = url,
                playlistError = null
            )
        }
    }

    fun previewPlaylist() {
        val coordinator = coordinator
        if (coordinator == null) {
            mutableUiState.update { current ->
                current.copy(playlistError = "YouTube playlist import is not available in this build.")
            }
            return
        }

        playlistPreviewJob?.cancel()
        playlistPreviewJob = viewModelScope.launch {
            val playlistUrl = mutableUiState.value.playlistUrl
            mutableUiState.update { current ->
                current.copy(
                    playlistLoading = true,
                    playlistError = null,
                    playlistPreview = null,
                    selectedPlaylistRemoteItemIds = emptySet()
                )
            }

            runCatching {
                var preview = coordinator.createPlaylistPreview(playlistUrl)
                publishPlaylistPreview(preview)
                var attempts = 0
                while (preview.isLoading() && attempts < PLAYLIST_PREVIEW_POLL_LIMIT) {
                    delay(PLAYLIST_PREVIEW_POLL_DELAY_MS)
                    preview = coordinator.refreshPlaylistPreview(preview.previewId)
                    publishPlaylistPreview(preview)
                    attempts += 1
                }
                preview
            }.onSuccess { preview ->
                mutableUiState.update { current ->
                    current.copy(
                        playlistLoading = false,
                        playlistPreview = preview,
                        playlistError = preview.terminalPreviewError()
                    )
                }
            }.onFailure { throwable ->
                mutableUiState.update { current ->
                    current.copy(
                        playlistLoading = false,
                        playlistError = throwable.localizedMessage
                            ?: "Fenlzer could not preview this playlist."
                    )
                }
            }
        }
    }

    fun togglePlaylistItem(remoteItemId: String) {
        mutableUiState.update { current ->
            val selected = current.selectedPlaylistRemoteItemIds
            current.copy(
                selectedPlaylistRemoteItemIds = if (remoteItemId in selected) {
                    selected - remoteItemId
                } else {
                    selected + remoteItemId
                }
            )
        }
    }

    fun selectAllPlaylistItems() {
        mutableUiState.update { current ->
            val selectableIds = current.playlistPreview
                ?.items
                ?.filter { item -> item.isSelectablePlaylistItem() }
                ?.map { item -> item.remoteItemId }
                ?.toSet()
                .orEmpty()
            current.copy(selectedPlaylistRemoteItemIds = selectableIds)
        }
    }

    fun importSelectedPlaylistItems() {
        importPlaylistItems(wholePlaylist = false)
    }

    fun importWholePlaylist() {
        importPlaylistItems(wholePlaylist = true)
    }

    fun importResult(result: YoutubeSearchResultItem) {
        val coordinator = coordinator ?: return
        if (!result.canDownload || result.isUnavailable || result.isLive) return
        if (importJob?.isActive == true) return

        importJob = viewModelScope.launch {
            mutableUiState.update { current ->
                current.copy(
                    isImportRunning = true,
                    lastImportResult = null,
                    importError = null
                )
            }

            runCatching { coordinator.importSearchResult(result).await() }
                .onSuccess { itemResult ->
                    mutableUiState.update { current ->
                        current.copy(
                            isImportRunning = false,
                            lastImportResult = itemResult,
                            importError = if (itemResult.outcome == YoutubeImportOutcome.FAILED) {
                                itemResult.message
                            } else {
                                null
                            }
                        )
                    }
                }
                .onFailure { throwable ->
                    mutableUiState.update { current ->
                        current.copy(
                            isImportRunning = false,
                            importError = throwable.localizedMessage
                                ?: "Fenlzer could not import this YouTube result."
                        )
                    }
                }
        }
    }

    fun cancelImport(importJobId: String) {
        val coordinator = coordinator ?: return
        viewModelScope.launch {
            runCatching { coordinator.cancelImport(importJobId).await() }
                .onFailure { throwable ->
                    mutableUiState.update { current ->
                        current.copy(
                            importError = throwable.localizedMessage
                                ?: "Fenlzer could not cancel this import."
                        )
                    }
                }
        }
    }

    fun retryImport(importJobId: String) {
        val coordinator = coordinator ?: return
        viewModelScope.launch {
            runCatching { coordinator.retryImport(importJobId).await() }
                .onSuccess { result ->
                    result?.let {
                        mutableUiState.update { current ->
                            current.copy(lastImportResult = it, importError = null)
                        }
                    }
                }
                .onFailure { throwable ->
                    mutableUiState.update { current ->
                        current.copy(
                            importError = throwable.localizedMessage
                                ?: "Fenlzer could not retry this import."
                        )
                    }
                }
        }
    }

    fun moveImport(importJobId: String, offset: Int) {
        val coordinator = coordinator ?: return
        viewModelScope.launch {
            runCatching { coordinator.moveImport(importJobId, offset).await() }
                .onFailure { throwable ->
                    mutableUiState.update { current ->
                        current.copy(
                            importError = throwable.localizedMessage
                                ?: "Fenlzer could not reorder this import."
                        )
                    }
                }
        }
    }

    fun setHistoryFilter(filter: ImportHistoryFilter) {
        mutableUiState.update { current ->
            current.copy(
                historyFilter = filter,
                history = fullHistory.visibleFor(filter)
            )
        }
    }

    fun clearHistory() {
        val coordinator = coordinator ?: return
        viewModelScope.launch {
            coordinator.clearImportHistory().await()
        }
    }

    fun retryHistoryItem(item: ImportHistoryUiItem) {
        val coordinator = coordinator ?: return
        viewModelScope.launch {
            runCatching { coordinator.retryHistoryItem(item).await() }
                .onSuccess { result ->
                    result?.let {
                        mutableUiState.update { current ->
                            current.copy(lastImportResult = it, importError = null)
                        }
                    }
                }
                .onFailure { throwable ->
                    mutableUiState.update { current ->
                        current.copy(
                            importError = throwable.localizedMessage
                                ?: "Fenlzer could not retry this import."
                        )
                    }
                }
        }
    }

    fun clearLastImportResult() {
        mutableUiState.update { current ->
            current.copy(lastImportResult = null, importError = null, latestProgress = null)
        }
    }

    private fun importPlaylistItems(wholePlaylist: Boolean) {
        val coordinator = coordinator ?: return
        if (importJob?.isActive == true) return
        val preview = mutableUiState.value.playlistPreview ?: return

        importJob = viewModelScope.launch {
            mutableUiState.update { current ->
                current.copy(
                    isImportRunning = true,
                    lastImportResult = null,
                    importError = null,
                    playlistError = null
                )
            }
            runCatching {
                coordinator.importPlaylistItems(
                    preview = preview,
                    remoteItemIds = mutableUiState.value.selectedPlaylistRemoteItemIds,
                    wholePlaylist = wholePlaylist
                ).await()
            }.onSuccess { results ->
                mutableUiState.update { current ->
                    current.copy(
                        isImportRunning = false,
                        lastImportResult = results.lastOrNull(),
                        importError = results.lastOrNull()
                            ?.takeIf { it.outcome == YoutubeImportOutcome.FAILED }
                            ?.message
                    )
                }
            }.onFailure { throwable ->
                mutableUiState.update { current ->
                    current.copy(
                        isImportRunning = false,
                        playlistError = throwable.localizedMessage
                            ?: "Fenlzer could not import this playlist."
                    )
                }
            }
        }
    }

    private fun publishPlaylistPreview(preview: YoutubePlaylistPreview) {
        mutableUiState.update { current ->
            val selectable = preview.items
                .filter { item -> item.isSelectablePlaylistItem() }
                .map { item -> item.remoteItemId }
                .toSet()
            val selected = current.selectedPlaylistRemoteItemIds
                .ifEmpty { selectable }
                .intersect(selectable)
            current.copy(
                playlistPreview = preview,
                selectedPlaylistRemoteItemIds = selected
            )
        }
    }

    override fun onCleared() {
        searchJob?.cancel()
        importJob?.cancel()
        playlistPreviewJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(coordinator: YoutubeImportCoordinator?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return YoutubeImportViewModel(coordinator) as T
                }
            }
    }
}

data class YoutubeImportUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<YoutubeSearchResultItem> = emptyList(),
    val searchError: String? = null,
    val isImportRunning: Boolean = false,
    val latestProgress: YoutubeImportProgress? = null,
    val lastImportResult: YoutubeImportItemResult? = null,
    val importError: String? = null,
    val activeJobs: List<ActiveImportUiItem> = emptyList(),
    val history: List<ImportHistoryUiItem> = emptyList(),
    val historyFilter: ImportHistoryFilter = ImportHistoryFilter.ALL,
    val playlistUrl: String = "",
    val playlistLoading: Boolean = false,
    val playlistError: String? = null,
    val playlistPreview: YoutubePlaylistPreview? = null,
    val selectedPlaylistRemoteItemIds: Set<String> = emptySet()
)

private fun List<ImportHistoryUiItem>.visibleFor(
    selectedFilter: ImportHistoryFilter
): List<ImportHistoryUiItem> =
    filter { item ->
        when (selectedFilter) {
            ImportHistoryFilter.ALL -> true
            ImportHistoryFilter.SUCCESS -> item.result == "SUCCESS"
            ImportHistoryFilter.DUPLICATE -> item.result == "DUPLICATE"
            ImportHistoryFilter.FAILED -> item.result == "FAILED"
            ImportHistoryFilter.CANCELLED -> item.result == "CANCELLED"
        }
    }.take(30)

private fun YoutubePlaylistPreview.isLoading(): Boolean =
    status.equals("LOADING", ignoreCase = true) ||
        status.equals("PENDING", ignoreCase = true) ||
        status.equals("PROCESSING", ignoreCase = true) ||
        status.equals("RUNNING", ignoreCase = true)

private fun YoutubePlaylistPreview.terminalPreviewError(): String? =
    when {
        status.equals("FAILED", ignoreCase = true) ->
            "Fenlzer could not load this playlist preview."
        status.equals("EXPIRED", ignoreCase = true) ->
            "This playlist preview expired. Preview the playlist again."
        else -> null
    }

private fun com.fenl.fenlzer.importing.youtube.YoutubePlaylistPreviewItem.isSelectablePlaylistItem(): Boolean {
    val normalizedAvailability = availability?.uppercase()
    return canDownload &&
        (normalizedAvailability == null || normalizedAvailability !in unavailablePlaylistStates)
}

private val unavailablePlaylistStates = setOf("PRIVATE", "DELETED", "UNAVAILABLE")
private const val PLAYLIST_PREVIEW_POLL_DELAY_MS = 1_000L
private const val PLAYLIST_PREVIEW_POLL_LIMIT = 30
