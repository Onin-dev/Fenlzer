package com.fenl.fenlzer.importing.youtube

import com.fenl.fenlzer.importing.ImportIntent
import com.fenl.fenlzer.importing.ImportQueueCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class YoutubeImportCoordinator(
    private val repository: YoutubeImportRepository,
    private val importQueueCoordinator: ImportQueueCoordinator,
    private val scope: CoroutineScope
) {
    private var recoveryJob: Job? = null

    fun observeActiveImports(): Flow<List<ActiveImportUiItem>> =
        importQueueCoordinator.observeActiveImports()

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
            importQueueCoordinator.recover()
        }.also { job ->
            recoveryJob = job
        }
    }

    fun importSearchResult(
        result: YoutubeSearchResultItem,
        intent: ImportIntent = ImportIntent.youtubeSearch()
    ): Deferred<YoutubeImportItemResult> =
        scope.async {
            importQueueCoordinator.enqueueYoutube(result, intent)
        }

    fun importPlaylistItems(
        preview: YoutubePlaylistPreview,
        remoteItemIds: Set<String>,
        wholePlaylist: Boolean
    ): Deferred<List<YoutubeImportItemResult>> =
        scope.async {
            importQueueCoordinator.enqueueYoutubePlaylist(
                preview = preview,
                remoteItemIds = remoteItemIds,
                wholePlaylist = wholePlaylist
            )
        }

    fun retryImport(importJobId: String): Deferred<YoutubeImportItemResult?> =
        scope.async {
            importQueueCoordinator.retry(importJobId)
        }

    fun cancelImport(importJobId: String): Deferred<Unit> =
        scope.async {
            importQueueCoordinator.cancel(importJobId)
        }

    fun moveImport(importJobId: String, offset: Int): Deferred<Unit> =
        scope.async {
            importQueueCoordinator.move(importJobId, offset)
        }

    fun clearImportHistory(): Deferred<Unit> =
        scope.async {
            repository.clearImportHistory()
        }

    fun retryHistoryItem(historyItem: ImportHistoryUiItem): Deferred<YoutubeImportItemResult?> =
        scope.async {
            importQueueCoordinator.retry(historyItem.importJobId ?: return@async null)
        }

    fun acknowledgeFinishedJobs(): Deferred<Unit> = scope.async {
        importQueueCoordinator.acknowledgeFinishedJobs()
    }

    fun dismissFailedJob(importJobId: String): Deferred<Unit> = scope.async {
        importQueueCoordinator.dismissFailedJob(importJobId)
    }
}
