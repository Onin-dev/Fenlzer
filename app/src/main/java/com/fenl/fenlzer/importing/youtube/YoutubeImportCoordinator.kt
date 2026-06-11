package com.fenl.fenlzer.importing.youtube

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class YoutubeImportCoordinator(
    private val repository: YoutubeImportRepository,
    private val scope: CoroutineScope
) {
    private val importRunnerMutex = Mutex()
    private var recoveryJob: Job? = null

    fun observeActiveImports(): Flow<List<ActiveImportUiItem>> =
        repository.observeActiveImports()

    fun observeImportHistory(): Flow<List<ImportHistoryUiItem>> =
        repository.observeImportHistory()

    suspend fun search(query: String): List<YoutubeSearchResultItem> =
        repository.search(query)

    suspend fun createPlaylistPreview(playlistUrl: String): YoutubePlaylistPreview =
        repository.createPlaylistPreview(playlistUrl)

    suspend fun refreshPlaylistPreview(previewId: String): YoutubePlaylistPreview =
        repository.refreshPlaylistPreview(previewId)

    fun startRecovery(): Job {
        recoveryJob?.takeIf { it.isActive }?.let { return it }
        return scope.launch {
            importRunnerMutex.withLock {
                repository.resumeRecoverableSearchImports()
            }
        }.also { job ->
            recoveryJob = job
        }
    }

    fun importSearchResult(
        result: YoutubeSearchResultItem,
        targetFavourite: Boolean = false
    ): Deferred<YoutubeImportItemResult> =
        scope.async {
            importRunnerMutex.withLock {
                repository.importSearchResult(result, targetFavourite = targetFavourite)
            }
        }

    fun importPlaylistItems(
        preview: YoutubePlaylistPreview,
        remoteItemIds: Set<String>,
        wholePlaylist: Boolean
    ): Deferred<List<YoutubeImportItemResult>> =
        scope.async {
            importRunnerMutex.withLock {
                repository.importPlaylistItems(
                    preview = preview,
                    remoteItemIds = remoteItemIds,
                    wholePlaylist = wholePlaylist
                )
            }
        }

    fun retryImport(importJobId: String): Deferred<YoutubeImportItemResult?> =
        scope.async {
            importRunnerMutex.withLock {
                repository.retryImport(importJobId)
            }
        }

    fun cancelImport(importJobId: String): Deferred<Unit> =
        scope.async {
            repository.cancelImport(importJobId)
        }

    fun moveImport(importJobId: String, offset: Int): Deferred<Unit> =
        scope.async {
            repository.moveImport(importJobId, offset)
        }

    fun clearImportHistory(): Deferred<Unit> =
        scope.async {
            repository.clearImportHistory()
        }

    fun retryHistoryItem(historyItem: ImportHistoryUiItem): Deferred<YoutubeImportItemResult?> =
        scope.async {
            importRunnerMutex.withLock {
                repository.retryHistoryItem(historyItem)
            }
        }
}
