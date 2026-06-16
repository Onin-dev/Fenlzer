package com.fenl.fenlzer.importing.youtube

import androidx.room.withTransaction
import com.fenl.fenlzer.common.FenlzerDispatchers
import com.fenl.fenlzer.data.local.FenlzerDatabase
import com.fenl.fenlzer.data.local.dao.ImportDao
import com.fenl.fenlzer.data.local.dao.PlaylistDao
import com.fenl.fenlzer.data.local.dao.QueueDao
import com.fenl.fenlzer.data.local.dao.RemoteDiscoverDao
import com.fenl.fenlzer.data.local.dao.TrackDao
import com.fenl.fenlzer.data.repository.StatsRepository
import com.fenl.fenlzer.data.local.entity.ImportHistoryEntryEntity
import com.fenl.fenlzer.data.local.entity.ImportJobEntity
import com.fenl.fenlzer.data.local.entity.RemoteItemEntity
import com.fenl.fenlzer.data.local.entity.TrackEntity
import com.fenl.fenlzer.data.local.entity.TrackOriginalMetadataEntity
import com.fenl.fenlzer.data.remote.ApiJobState
import com.fenl.fenlzer.data.remote.ApiJobReason
import com.fenl.fenlzer.data.remote.ApiOperationException
import com.fenl.fenlzer.data.remote.ApiPreferredFormat
import com.fenl.fenlzer.data.remote.ApiPriorityClass
import com.fenl.fenlzer.data.remote.ApiRepository
import com.fenl.fenlzer.data.remote.BatchDownloadRequest
import com.fenl.fenlzer.data.remote.CompactJobStatus
import com.fenl.fenlzer.data.remote.ConfirmFileRequest
import com.fenl.fenlzer.data.remote.CreateDownloadRequest
import com.fenl.fenlzer.data.remote.DownloadSource
import com.fenl.fenlzer.data.remote.JobObject
import com.fenl.fenlzer.data.remote.PlaylistPreviewData
import com.fenl.fenlzer.data.remote.PlaylistPreviewItem
import com.fenl.fenlzer.data.remote.SearchResult
import com.fenl.fenlzer.data.storage.FenlzerStorage
import com.fenl.fenlzer.domain.text.AudioTitleFormatter
import com.fenl.fenlzer.domain.text.SearchNormalizer
import com.fenl.fenlzer.importing.ImportIntent
import com.fenl.fenlzer.importing.ImportExecutionResult
import com.fenl.fenlzer.importing.ImportPriorityClass
import com.fenl.fenlzer.importing.ImportReason
import com.fenl.fenlzer.importing.ImportSourceType
import com.fenl.fenlzer.importing.local.Sha256
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import okhttp3.ResponseBody
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import java.util.UUID

class YoutubeImportRepository(
    private val apiRepository: ApiRepository,
    private val trackDao: TrackDao,
    private val importDao: ImportDao,
    private val remoteDiscoverDao: RemoteDiscoverDao,
    private val playlistDao: PlaylistDao? = null,
    private val queueDao: QueueDao? = null,
    private val statsRepository: StatsRepository? = null,
    private val database: FenlzerDatabase? = null,
    private val storage: FenlzerStorage,
    private val thumbnailPromoter: YoutubeThumbnailPromoter = YoutubeThumbnailPromoter(storage),
    private val dispatchers: FenlzerDispatchers = FenlzerDispatchers(),
    private val now: () -> Long = { System.currentTimeMillis() },
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val pollDelayMs: Long = 1_000L,
    private val maxPollAttempts: Int = 240
) {
    fun observeActiveImports(): Flow<List<ActiveImportUiItem>> {
        return importDao.observeActiveJobs().map { jobs ->
            val compactStatuses = loadCompactStatuses(jobs)
            val localQueuePositions = jobs
                .filter { job -> job.status == STATUS_QUEUED }
                .sortedWith(compareByDescending<ImportJobEntity> { it.priority }.thenBy { it.createdAt })
                .mapIndexed { index, job -> job.importJobId to (index + 1) }
                .toMap()
            jobs.map { job ->
                val remoteItem = job.remoteItem()
                val compactStatus = job.apiJobId?.let(compactStatuses::get)
                val compactEffectiveStatus = compactStatus?.effectiveStatus()
                val effectiveStatus = when {
                    job.status in localTransferStatuses -> job.status
                    compactEffectiveStatus != null &&
                        ApiJobState.isTerminalSuccess(compactEffectiveStatus) &&
                        job.status != ApiJobState.TRANSFER_CONFIRMED -> ApiJobState.READY_FOR_TRANSFER
                    else -> compactEffectiveStatus ?: job.status
                }
                ActiveImportUiItem(
                    importJobId = job.importJobId,
                    apiJobId = job.apiJobId,
                    title = job.displayTitle(),
                    sourceLabel = job.sourceLabel(),
                    status = effectiveStatus,
                    progressPercent = compactStatus?.progressPercent ?: job.progressPercent,
                    queuePosition = compactStatus?.queuePosition
                        ?: localQueuePositions[job.importJobId],
                    thumbnailUrl = remoteItem?.thumbnailUrl,
                    errorMessage = compactStatus?.errorMessage ?: job.errorMessage,
                    retryable = effectiveStatus in retryableLocalStatuses ||
                        compactStatus?.retryable == true,
                    cancellable = effectiveStatus in cancellableLocalStatuses,
                    attemptCount = job.attemptCount,
                    maxAttempts = job.maxAttempts,
                    dismissible = effectiveStatus == STATUS_FAILED
                )
            }
        }
    }

    fun observeImportHistory(): Flow<List<ImportHistoryUiItem>> {
        return importDao.observeImportHistory().map { entries ->
            entries.map { entry ->
                ImportHistoryUiItem(
                    historyId = entry.historyId,
                    importJobId = entry.importJobId,
                    title = entry.displayTitle,
                    result = entry.result,
                    reason = entry.reason,
                    sourceLabel = entry.sourceType.toSourceLabel(),
                    message = entry.friendlyMessage,
                    trackId = entry.trackId,
                    createdAt = entry.createdAt
                )
            }
        }
    }

    suspend fun search(query: String): List<YoutubeSearchResultItem> =
    withContext(dispatchers.io) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            throw YoutubeImportException(
                message = "Enter a YouTube search query first.",
                errorCode = "EMPTY_QUERY",
                retryable = false
            )
        }

        val videoId = trimmedQuery.extractYoutubeVideoId()
        val apiQuery = videoId ?: trimmedQuery
        val limit = if (videoId != null) 1 else 5

        val results = apiRepository.searchYoutube(query = apiQuery, limit = limit)
            .results
            .take(limit)
            .map { it.toImportItem() }

        if (results.isNotEmpty()) {
            results
        } else if (videoId != null) {
            listOf(trimmedQuery.toExactYoutubeUrlResult(videoId))
        } else {
            emptyList()
        }
    }

    suspend fun createPlaylistPreview(playlistUrl: String): YoutubePlaylistPreview =
        withContext(dispatchers.io) {
            val trimmedUrl = playlistUrl.trim()
            if (trimmedUrl.isBlank()) {
                throw YoutubeImportException(
                    message = "Enter a YouTube playlist URL first.",
                    errorCode = "EMPTY_PLAYLIST_URL",
                    retryable = false
                )
            }
            apiRepository.createPlaylistPreview(trimmedUrl)
                .also { preview -> persistPreviewRemoteItems(preview.items) }
                .toPreview()
        }

    suspend fun refreshPlaylistPreview(previewId: String): YoutubePlaylistPreview =
        withContext(dispatchers.io) {
            apiRepository.getPlaylistPreview(previewId)
                .also { preview -> persistPreviewRemoteItems(preview.items) }
                .toPreview()
        }

    suspend fun prepareSearchImport(
        result: YoutubeSearchResultItem,
        intent: ImportIntent = ImportIntent.youtubeSearch()
    ): YoutubeImportItemResult = withContext(dispatchers.io) {
        storage.ensureDirectories()
        intent.pendingActionType?.let { pendingActionType ->
            importDao.getActivePendingActionJob(
                remoteItemId = result.remoteItemId,
                pendingActionType = pendingActionType,
                targetPlaylistId = intent.targetPlaylistId
            )?.let { existing ->
                return@withContext YoutubeImportItemResult(
                    importJobId = existing.importJobId,
                    trackId = null,
                    displayTitle = result.title,
                    outcome = YoutubeImportOutcome.QUEUED,
                    message = "This action is already queued."
                )
            }
        }
        val createdAt = now()
        remoteDiscoverDao.upsertRemoteItem(result.toRemoteItem(createdAt))
        val job = ImportJobEntity(
            importJobId = idFactory(),
            apiJobId = null,
            jobType = JOB_TYPE_YOUTUBE_SEARCH,
            sourceType = intent.sourceType,
            reason = intent.reason,
            priorityClass = intent.priorityClass,
            pendingActionType = intent.pendingActionType,
            priority = intent.priorityClass.toLocalPriority(),
            status = STATUS_QUEUED,
            sourceUrl = result.sourceUrl,
            youtubeVideoId = result.youtubeVideoId,
            remoteItemId = result.remoteItemId,
            targetPlaylistId = intent.targetPlaylistId,
            targetFavourite = intent.targetFavourite,
            preferredFormat = ApiPreferredFormat.M4A_AAC,
            progressPercent = 0,
            technicalDetailsJson = result.title,
            createdAt = createdAt,
            updatedAt = createdAt
        )
        importDao.upsertJob(job)
        YoutubeImportItemResult(
            importJobId = job.importJobId,
            trackId = null,
            displayTitle = result.title,
            outcome = YoutubeImportOutcome.QUEUED,
            message = "Import queued."
        )
    }

    suspend fun executeImportJob(
        importJobId: String,
        onProgress: (YoutubeImportProgress) -> Unit = {}
    ): ImportExecutionResult = withContext(dispatchers.io) {
        val job = importDao.getJob(importJobId)
            ?: return@withContext ImportExecutionResult.TerminalFailure("Import job not found.")
        if (job.status == ApiJobState.CANCELLED) return@withContext ImportExecutionResult.Cancelled
        resumeJob(job, job.toRecoveredSearchResult(), onProgress)
        val latest = importDao.getJob(importJobId)
            ?: return@withContext ImportExecutionResult.TerminalFailure("Import job not found.")
        when (latest.status) {
            ApiJobState.TRANSFER_CONFIRMED, STATUS_COMPLETED, STATUS_DUPLICATE ->
                ImportExecutionResult.Completed
            ApiJobState.CANCELLED -> ImportExecutionResult.Cancelled
            STATUS_NEEDS_ATTENTION -> ImportExecutionResult.RetryableFailure(latest.errorMessage)
            else -> {
                ensureTerminalFailureHistory(latest)
                ImportExecutionResult.TerminalFailure(latest.errorMessage)
            }
        }
    }

    suspend fun importPlaylistItems(
        preview: YoutubePlaylistPreview,
        remoteItemIds: Set<String>,
        wholePlaylist: Boolean
    ): List<YoutubeImportItemResult> = withContext(dispatchers.io) {
        val selectedItems = preview.items
            .filter { item ->
                wholePlaylist || item.remoteItemId in remoteItemIds
            }
            .filter { item ->
                val availability = item.availability?.uppercase(Locale.US)
                item.canDownload &&
                    availability != "PRIVATE" &&
                    availability != "DELETED" &&
                    availability != "UNAVAILABLE"
            }
        if (selectedItems.isEmpty()) {
            throw YoutubeImportException(
                message = "Select at least one downloadable playlist song.",
                errorCode = "NO_PLAYLIST_SELECTION",
                retryable = false
            )
        }

        val createdAt = now()
        selectedItems.forEach { item ->
            remoteDiscoverDao.upsertRemoteItem(item.toRemoteItem(createdAt))
        }

        val jobsByClientId = selectedItems.associate { item ->
            val importJobId = idFactory()
            val job = ImportJobEntity(
                importJobId = importJobId,
                apiJobId = null,
                jobType = JOB_TYPE_YOUTUBE_PLAYLIST_ITEM,
                sourceType = ImportSourceType.YOUTUBE_PLAYLIST,
                reason = ImportReason.YOUTUBE_PLAYLIST,
                priorityClass = ImportPriorityClass.MANUAL,
                priority = PRIORITY_MANUAL,
                status = STATUS_QUEUED,
                sourceUrl = item.sourceUrl,
                youtubeVideoId = item.youtubeVideoId,
                remoteItemId = item.remoteItemId,
                targetFavourite = false,
                preferredFormat = ApiPreferredFormat.M4A_AAC,
                progressPercent = 0,
                technicalDetailsJson = item.title,
                createdAt = createdAt,
                updatedAt = createdAt
            )
            importDao.upsertJob(job)
            importJobId to job
        }

        val batch = apiRepository.createDownloadBatch(
            request = BatchDownloadRequest(
                batchId = "batch_${idFactory()}",
                priorityClass = ApiPriorityClass.MANUAL,
                preferredFormat = ApiPreferredFormat.M4A_AAC,
                fallbackToBestAvailable = true,
                reason = ApiJobReason.YOUTUBE_PLAYLIST,
                items = selectedItems.map { item ->
                    buildJsonObject {
                        put("clientJobId", JsonPrimitive(jobsByClientId.entries.first {
                            it.value.remoteItemId == item.remoteItemId
                        }.key))
                        item.youtubeVideoId?.let { put("youtubeVideoId", JsonPrimitive(it)) }
                        item.sourceUrl?.let { put("sourceUrl", JsonPrimitive(it)) }
                        put("remoteItemId", JsonPrimitive(item.remoteItemId))
                    }
                }
            )
        )

        batch.createdJobs.forEach { created ->
            val localJob = created.clientJobId?.let(jobsByClientId::get) ?: return@forEach
            importDao.upsertJob(
                localJob.copy(
                    apiJobId = created.apiJobId,
                    status = normalizeJobStatus(created.status, null),
                    progressPercent = if (created.status == STATUS_QUEUED) 0 else localJob.progressPercent,
                    updatedAt = now()
                )
            )
        }

        selectedItems.mapNotNull { item ->
            val job = jobsByClientId.values.firstOrNull { it.remoteItemId == item.remoteItemId }
                ?: return@mapNotNull null
            YoutubeImportItemResult(
                importJobId = job.importJobId,
                trackId = null,
                displayTitle = item.title,
                outcome = YoutubeImportOutcome.QUEUED,
                message = "Import queued."
            )
        }
    }

    suspend fun cancelImport(importJobId: String) = withContext(dispatchers.io) {
        val job = importDao.getJob(importJobId) ?: return@withContext
        val updated = job.copy(
            status = ApiJobState.CANCELLED,
            errorCode = null,
            errorMessage = "Cancelled by user.",
            progressPercent = job.progressPercent,
            isVisibleInActiveImports = true,
            updatedAt = now(),
            completedAt = now()
        )
        importDao.upsertJob(updated)
        cleanupTemporaryFiles(importJobId)
        if (importDao.countHistoryForJob(importJobId) == 0) {
            insertHistory(
                importJobId = job.importJobId,
                result = HISTORY_CANCELLED,
                displayTitle = job.displayTitle(),
                youtubeVideoId = job.youtubeVideoId,
                sourceUrl = job.sourceUrl,
                friendlyMessage = "Cancelled by user."
            )
        }
        job.apiJobId?.let { apiJobId ->
            apiRepository.cancelJob(apiJobId, "User cancelled from Active Imports")
        }
    }

    suspend fun retryImport(importJobId: String): YoutubeImportItemResult? = withContext(dispatchers.io) {
        val job = importDao.getJob(importJobId) ?: return@withContext null
        if (job.apiJobId != null) {
            val retry = apiRepository.retryJob(job.apiJobId)
            importDao.upsertJob(
                job.copy(
                    apiJobId = retry.newJob.apiJobId,
                    status = normalizeJobStatus(retry.newJob.status, null),
                    progressPercent = 0,
                    errorCode = null,
                    errorMessage = null,
                    attemptCount = 0,
                    isVisibleInActiveImports = true,
                    completedAt = null,
                    updatedAt = now()
                )
            )
        } else {
            importDao.upsertJob(
                job.copy(
                    status = STATUS_QUEUED,
                    progressPercent = 0,
                    errorCode = null,
                    errorMessage = null,
                    attemptCount = 0,
                    isVisibleInActiveImports = true,
                    completedAt = null,
                    updatedAt = now()
                )
            )
        }
        val updated = importDao.getJob(importJobId) ?: return@withContext null
        YoutubeImportItemResult(
            importJobId = updated.importJobId,
            trackId = null,
            displayTitle = updated.displayTitle(),
            outcome = YoutubeImportOutcome.QUEUED,
            message = "Retry queued."
        )
    }

    suspend fun finalizeRetryExhausted(importJobId: String) = withContext(dispatchers.io) {
        val job = importDao.getJob(importJobId) ?: return@withContext
        val message = job.errorMessage ?: "This download failed after three attempts."
        val failed = job.copy(
            status = STATUS_FAILED,
            errorMessage = message,
            isVisibleInActiveImports = true,
            completedAt = now(),
            updatedAt = now()
        )
        importDao.upsertJob(failed)
        ensureTerminalFailureHistory(failed)
    }

    suspend fun retryHistoryItem(historyItem: ImportHistoryUiItem): YoutubeImportItemResult? {
        return retryImport(historyItem.importJobId ?: return null)
    }

    suspend fun moveImport(importJobId: String, offset: Int) = withContext(dispatchers.io) {
        val activeJobs = importDao.getActiveJobs()
        val targetPriorityClass = activeJobs.firstOrNull { it.importJobId == importJobId }
            ?.priorityClass
            ?: return@withContext
        val compactStatuses = loadCompactStatuses(activeJobs)
        val jobs = activeJobs
            .filter { job ->
                val effectiveStatus = job.apiJobId
                    ?.let(compactStatuses::get)
                    ?.effectiveStatus()
                    ?: normalizeJobStatus(job.status, null)
                effectiveStatus == STATUS_QUEUED
            }
            .filter { job -> job.priorityClass == targetPriorityClass }
            .sortedWith(compareByDescending<ImportJobEntity> { it.priority }.thenBy { it.createdAt })
        val index = jobs.indexOfFirst { it.importJobId == importJobId }
        if (index == -1) return@withContext
        val targetIndex = (index + offset).coerceIn(0, jobs.lastIndex)
        if (index == targetIndex) return@withContext
        val reordered = jobs.toMutableList().apply {
            add(targetIndex, removeAt(index))
        }
        reordered.forEachIndexed { orderedIndex, job ->
            val priorityBase = if (job.priorityClass == ImportPriorityClass.AUTO) {
                PRIORITY_AUTO
            } else {
                PRIORITY_MANUAL
            }
            importDao.upsertJob(
                job.copy(
                    priority = priorityBase + (reordered.size - orderedIndex),
                    updatedAt = now()
                )
            )
        }
        val apiIds = reordered.mapNotNull { it.apiJobId }
        if (apiIds.size > 1) {
            apiRepository.reorderJobs(apiIds)
        }
    }

    suspend fun clearImportHistory() = withContext(dispatchers.io) {
        importDao.clearImportHistory()
    }

    suspend fun importSearchResult(
        result: YoutubeSearchResultItem,
        onProgress: (YoutubeImportProgress) -> Unit = {},
        intent: ImportIntent = ImportIntent.youtubeSearch()
    ): YoutubeImportItemResult = withContext(dispatchers.io) {
        storage.ensureDirectories()

        val createdAt = now()
        remoteDiscoverDao.upsertRemoteItem(result.toRemoteItem(createdAt))
        var job = ImportJobEntity(
            importJobId = idFactory(),
            apiJobId = null,
            jobType = JOB_TYPE_YOUTUBE_SEARCH,
            sourceType = intent.sourceType,
            reason = intent.reason,
            priorityClass = intent.priorityClass,
            pendingActionType = intent.pendingActionType,
            priority = intent.priorityClass.toLocalPriority(),
            status = STATUS_QUEUED,
            sourceUrl = result.sourceUrl,
            youtubeVideoId = result.youtubeVideoId,
                remoteItemId = result.remoteItemId,
                targetPlaylistId = intent.targetPlaylistId,
                targetFavourite = intent.targetFavourite,
            preferredFormat = ApiPreferredFormat.M4A_AAC,
            progressPercent = 0,
            technicalDetailsJson = result.title,
            createdAt = createdAt,
            updatedAt = createdAt
        )
        importDao.upsertJob(job)

        suspend fun updateJob(
            status: String,
            progressPercent: Int? = null,
            apiJobId: String? = job.apiJobId,
            actualFormat: String? = job.actualFormat,
            errorCode: String? = null,
            errorMessage: String? = null,
            completedAt: Long? = null
        ) {
            ensureJobNotLocallyCancelled(job.importJobId)
            job = job.copy(
                apiJobId = apiJobId,
                status = status,
                progressPercent = progressPercent,
                actualFormat = actualFormat,
                errorCode = errorCode,
                errorMessage = errorMessage,
                updatedAt = now(),
                completedAt = completedAt
            )
            importDao.upsertJob(job)
            onProgress(
                YoutubeImportProgress(
                    importJobId = job.importJobId,
                    apiJobId = job.apiJobId,
                    displayTitle = result.title,
                    status = job.status,
                    progressPercent = job.progressPercent,
                    message = job.errorMessage
                )
            )
        }

        try {
            duplicateByStrongIdentifier(result)?.let { duplicate ->
                val message = "Already imported as ${duplicate.displayTitle()}."
                completeImportedTrack(job, result.remoteItemId, duplicate.trackId)
                promoteArtworkIfNeeded(duplicate, result.thumbnailUrl)
                updateJob(
                    status = STATUS_DUPLICATE,
                    progressPercent = 100,
                    errorCode = ERROR_DUPLICATE,
                    errorMessage = message,
                    completedAt = now()
                )
                insertHistory(
                    importJobId = job.importJobId,
                    result = HISTORY_DUPLICATE,
                    displayTitle = result.title,
                    trackId = duplicate.trackId,
                    youtubeVideoId = result.youtubeVideoId,
                    sourceUrl = result.sourceUrl,
                    friendlyMessage = message,
                    errorCode = ERROR_DUPLICATE
                )
                return@withContext YoutubeImportItemResult(
                    importJobId = job.importJobId,
                    trackId = duplicate.trackId,
                    displayTitle = result.title,
                    outcome = YoutubeImportOutcome.DUPLICATE,
                    duplicateTrackId = duplicate.trackId,
                    message = message
                )
            }

            val createdJob = apiRepository.createYoutubeDownload(
                request = CreateDownloadRequest(
                    clientJobId = job.importJobId,
                    source = DownloadSource(
                        youtubeVideoId = result.youtubeVideoId,
                        sourceUrl = result.sourceUrl,
                        remoteItemId = result.remoteItemId
                    ),
                    jobType = job.apiJobType(),
                    priorityClass = job.apiPriorityClass(),
                    preferredFormat = job.preferredFormat,
                    fallbackToBestAvailable = true,
                    reason = job.reason ?: ImportReason.MANUAL_SINGLE,
                    target = job.apiTarget()
                )
            ).job

            updateJob(
                status = createdJob.effectiveStatus(),
                progressPercent = createdJob.progressPercent ?: 0,
                apiJobId = createdJob.apiJobId,
                actualFormat = createdJob.actualFormat
            )

            val readyJob = awaitTransferReady(
                currentJob = createdJob,
                updateJob = ::updateJob
            )
            val imported = transferAndImport(
                searchResult = result,
                readyJob = readyJob,
                importJob = job,
                updateJob = ::updateJob
            )

            imported
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: YoutubeImportCancelledException) {
            YoutubeImportItemResult(
                importJobId = job.importJobId,
                trackId = null,
                displayTitle = result.title,
                outcome = YoutubeImportOutcome.FAILED,
                message = exception.message
            )
        } catch (exception: ApiOperationException) {
            if (isJobLocallyCancelled(job.importJobId)) {
                return@withContext job.cancelledResult(result.title)
            }
            val message = exception.message
            updateJob(
                status = if (exception.retryable) STATUS_NEEDS_ATTENTION else STATUS_FAILED,
                errorCode = exception.errorCode,
                errorMessage = message,
                completedAt = if (exception.retryable) null else now()
            )
            insertHistory(
                importJobId = job.importJobId,
                result = HISTORY_FAILED,
                displayTitle = result.title,
                youtubeVideoId = result.youtubeVideoId,
                sourceUrl = result.sourceUrl,
                friendlyMessage = message,
                errorCode = exception.errorCode
            )
            YoutubeImportItemResult(
                importJobId = job.importJobId,
                trackId = null,
                displayTitle = result.title,
                outcome = YoutubeImportOutcome.FAILED,
                message = message
            )
        } catch (exception: YoutubeImportException) {
            if (isJobLocallyCancelled(job.importJobId)) {
                return@withContext job.cancelledResult(result.title)
            }
            val message = exception.message
            updateJob(
                status = if (exception.retryable) STATUS_NEEDS_ATTENTION else STATUS_FAILED,
                errorCode = exception.errorCode,
                errorMessage = message,
                completedAt = if (exception.retryable) null else now()
            )
            insertHistory(
                importJobId = job.importJobId,
                result = HISTORY_FAILED,
                displayTitle = result.title,
                youtubeVideoId = result.youtubeVideoId,
                sourceUrl = result.sourceUrl,
                friendlyMessage = message,
                errorCode = exception.errorCode
            )
            YoutubeImportItemResult(
                importJobId = job.importJobId,
                trackId = null,
                displayTitle = result.title,
                outcome = YoutubeImportOutcome.FAILED,
                message = message
            )
        } catch (throwable: Throwable) {
            if (isJobLocallyCancelled(job.importJobId)) {
                return@withContext job.cancelledResult(result.title)
            }
            val message = throwable.localizedMessage ?: "This YouTube import could not be completed."
            updateJob(
                status = STATUS_FAILED,
                errorCode = ERROR_IMPORT_FAILED,
                errorMessage = message,
                completedAt = now()
            )
            insertHistory(
                importJobId = job.importJobId,
                result = HISTORY_FAILED,
                displayTitle = result.title,
                youtubeVideoId = result.youtubeVideoId,
                sourceUrl = result.sourceUrl,
                friendlyMessage = message,
                errorCode = ERROR_IMPORT_FAILED,
                technicalDetailsJson = throwable::class.qualifiedName
            )
            YoutubeImportItemResult(
                importJobId = job.importJobId,
                trackId = null,
                displayTitle = result.title,
                outcome = YoutubeImportOutcome.FAILED,
                message = message
            )
        }
    }

    suspend fun resumeRecoverableSearchImports(
        onProgress: (YoutubeImportProgress) -> Unit = {}
    ): List<YoutubeImportItemResult> = withContext(dispatchers.io) {
        val recoverableJobs = importDao.getRecoverableYoutubeSearchJobs()
        restoreCompactStatuses(recoverableJobs)
        importDao.getRecoverableYoutubeSearchJobs().mapNotNull { localJob ->
            val result = localJob.toRecoveredSearchResult()
            resumeJob(localJob, result, onProgress)
        }
    }

    private suspend fun restoreCompactStatuses(localJobs: List<ImportJobEntity>) {
        val statuses = loadCompactStatuses(localJobs)
        if (localJobs.any { it.apiJobId != null } && statuses.isEmpty()) {
            localJobs
                .filter { it.apiJobId != null }
                .forEach { job ->
                    importDao.upsertJob(
                        job.copy(
                            status = STATUS_NEEDS_ATTENTION,
                            errorCode = ERROR_RESTORE_STATUS_FAILED,
                            errorMessage = "Fenlzer could not restore this job status. Try again from Active Imports.",
                            updatedAt = now()
                        )
                    )
                }
            return
        }

        localJobs.forEach { job ->
            val apiJobId = job.apiJobId ?: return@forEach
            val compact = statuses[apiJobId]
            if (compact == null || compact.effectiveStatus() == ApiJobState.UNKNOWN) {
                importDao.upsertJob(
                    job.copy(
                        status = STATUS_NEEDS_ATTENTION,
                        errorCode = ERROR_RESTORE_STATUS_UNKNOWN,
                        errorMessage = "The API could not identify this job after restart.",
                        updatedAt = now()
                    )
                )
            } else {
                val effectiveStatus = compact.effectiveStatus()
                importDao.upsertJob(
                    job.copy(
                        status = effectiveStatus.restoredLocalStatus(),
                        progressPercent = compact.progressPercent ?: job.progressPercent,
                        errorCode = compact.errorCode,
                        errorMessage = compact.errorMessage,
                        updatedAt = now()
                    )
                )
            }
        }
    }

    private suspend fun loadCompactStatuses(
        localJobs: List<ImportJobEntity>
    ): Map<String, CompactJobStatus> {
        val apiJobIds = localJobs.mapNotNull { it.apiJobId }.distinct()
        if (apiJobIds.isEmpty()) return emptyMap()
        return runCatching {
            apiRepository.getManyJobStatuses(apiJobIds).jobs.associateBy { it.apiJobId }
        }.getOrDefault(emptyMap())
    }

    private suspend fun resumeJob(
        localJob: ImportJobEntity,
        searchResult: YoutubeSearchResultItem,
        onProgress: (YoutubeImportProgress) -> Unit
    ): YoutubeImportItemResult? {
        var job = localJob

        suspend fun updateJob(
            status: String,
            progressPercent: Int? = null,
            apiJobId: String? = job.apiJobId,
            actualFormat: String? = job.actualFormat,
            errorCode: String? = null,
            errorMessage: String? = null,
            completedAt: Long? = null
        ) {
            ensureJobNotLocallyCancelled(job.importJobId)
            job = job.copy(
                apiJobId = apiJobId,
                status = status,
                progressPercent = progressPercent,
                actualFormat = actualFormat,
                errorCode = errorCode,
                errorMessage = errorMessage,
                updatedAt = now(),
                completedAt = completedAt
            )
            importDao.upsertJob(job)
            onProgress(
                YoutubeImportProgress(
                    importJobId = job.importJobId,
                    apiJobId = job.apiJobId,
                    displayTitle = searchResult.title,
                    status = job.status,
                    progressPercent = job.progressPercent,
                    message = job.errorMessage
                )
            )
        }

        return try {
            duplicateByStrongIdentifier(searchResult)?.let { existingTrack ->
                job.apiJobId?.let { apiJobId ->
                    val remoteJob = apiRepository.getJob(apiJobId).job
                    if (remoteJob.file?.available == true) {
                        confirmTransferredFile(
                            readyJob = remoteJob,
                            sha256 = existingTrack.audioHash,
                            sizeBytes = existingTrack.fileSizeBytes,
                            localTrackId = existingTrack.trackId
                        )
                    } else if (!ApiJobState.isTerminalSuccess(remoteJob.effectiveStatus())) {
                        apiRepository.cancelJob(
                            apiJobId,
                            "Local track already exists while restoring import"
                        )
                    }
                }
                val message = "Already imported as ${existingTrack.displayTitle()}."
                completeImportedTrack(job, searchResult.remoteItemId, existingTrack.trackId)
                promoteArtworkIfNeeded(existingTrack, searchResult.thumbnailUrl)
                updateJob(
                    status = ApiJobState.TRANSFER_CONFIRMED,
                    progressPercent = 100,
                    apiJobId = job.apiJobId,
                    actualFormat = job.actualFormat,
                    errorCode = null,
                    errorMessage = null,
                    completedAt = now()
                )
                insertHistory(
                    importJobId = job.importJobId,
                    result = HISTORY_DUPLICATE,
                    displayTitle = searchResult.title,
                    trackId = existingTrack.trackId,
                    youtubeVideoId = searchResult.youtubeVideoId,
                    sourceUrl = searchResult.sourceUrl,
                    friendlyMessage = message,
                    errorCode = ERROR_DUPLICATE
                )
                return YoutubeImportItemResult(
                    importJobId = job.importJobId,
                    trackId = existingTrack.trackId,
                    displayTitle = existingTrack.displayTitle(),
                    outcome = YoutubeImportOutcome.DUPLICATE,
                    duplicateTrackId = existingTrack.trackId,
                    message = message
                )
            }

            val remoteJob = if (localJob.apiJobId == null) {
                apiRepository.createYoutubeDownload(
                    request = CreateDownloadRequest(
                        clientJobId = localJob.importJobId,
                        source = DownloadSource(
                            youtubeVideoId = searchResult.youtubeVideoId,
                            sourceUrl = searchResult.sourceUrl,
                            remoteItemId = searchResult.remoteItemId
                        ),
                        jobType = localJob.apiJobType(),
                        priorityClass = localJob.apiPriorityClass(),
                        preferredFormat = localJob.preferredFormat,
                        fallbackToBestAvailable = true,
                        reason = localJob.reason ?: ImportReason.MANUAL_SINGLE,
                        target = localJob.apiTarget()
                    ),
                    idempotencyKey = "fenlzer_resume_${localJob.importJobId}"
                ).job.also { createdJob ->
                    updateJob(
                        status = createdJob.effectiveStatus(),
                        progressPercent = createdJob.progressPercent ?: localJob.progressPercent,
                        apiJobId = createdJob.apiJobId,
                        actualFormat = createdJob.actualFormat,
                        errorCode = null,
                        errorMessage = null,
                        completedAt = null
                    )
                }
            } else {
                apiRepository.getJob(localJob.apiJobId).job
            }

            if (ApiJobState.isTerminalSuccess(remoteJob.effectiveStatus()) && remoteJob.file?.available != true) {
                val existingTrack = duplicateByStrongIdentifier(searchResult)
                if (existingTrack != null) {
                    completeImportedTrack(job, searchResult.remoteItemId, existingTrack.trackId)
                    promoteArtworkIfNeeded(existingTrack, searchResult.thumbnailUrl)
                    updateJob(
                        status = ApiJobState.TRANSFER_CONFIRMED,
                        progressPercent = 100,
                        apiJobId = remoteJob.apiJobId,
                        actualFormat = remoteJob.actualFormat,
                        errorCode = null,
                        errorMessage = null,
                        completedAt = now()
                    )
                    return YoutubeImportItemResult(
                        importJobId = job.importJobId,
                        trackId = existingTrack.trackId,
                        displayTitle = existingTrack.displayTitle(),
                        outcome = YoutubeImportOutcome.SUCCESS,
                        message = "Import restored."
                    )
                }
                throw YoutubeImportException(
                    message = "This API job was already confirmed, but no matching local song was found.",
                    errorCode = ERROR_RESTORE_CONFIRMED_WITHOUT_LOCAL_TRACK,
                    retryable = false
                )
            }

            val readyJob = awaitTransferReady(remoteJob, ::updateJob)
            transferAndImport(
                searchResult = searchResult,
                readyJob = readyJob,
                importJob = job,
                updateJob = ::updateJob
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: YoutubeImportCancelledException) {
            null
        } catch (exception: ApiOperationException) {
            if (isJobLocallyCancelled(job.importJobId)) return null
            updateJob(
                status = if (exception.retryable) STATUS_NEEDS_ATTENTION else STATUS_FAILED,
                errorCode = exception.errorCode,
                errorMessage = exception.message,
                completedAt = if (exception.retryable) null else now()
            )
            null
        } catch (exception: YoutubeImportException) {
            if (isJobLocallyCancelled(job.importJobId)) return null
            updateJob(
                status = if (exception.retryable) STATUS_NEEDS_ATTENTION else STATUS_FAILED,
                errorCode = exception.errorCode,
                errorMessage = exception.message,
                completedAt = if (exception.retryable) null else now()
            )
            null
        } catch (throwable: Throwable) {
            if (isJobLocallyCancelled(job.importJobId)) return null
            updateJob(
                status = STATUS_NEEDS_ATTENTION,
                errorCode = ERROR_IMPORT_FAILED,
                errorMessage = throwable.localizedMessage
                    ?: "Fenlzer could not restore this import automatically.",
                completedAt = null
            )
            null
        }
    }

    private suspend fun isJobLocallyCancelled(importJobId: String): Boolean =
        importDao.getJob(importJobId)?.status == ApiJobState.CANCELLED

    private suspend fun ensureJobNotLocallyCancelled(importJobId: String) {
        if (isJobLocallyCancelled(importJobId)) {
            throw YoutubeImportCancelledException()
        }
    }

    private fun ImportJobEntity.cancelledResult(displayTitle: String): YoutubeImportItemResult =
        YoutubeImportItemResult(
            importJobId = importJobId,
            trackId = null,
            displayTitle = displayTitle,
            outcome = YoutubeImportOutcome.FAILED,
            message = "Cancelled by user."
        )

    private suspend fun awaitTransferReady(
        currentJob: JobObject,
        updateJob: suspend (
            status: String,
            progressPercent: Int?,
            apiJobId: String?,
            actualFormat: String?,
            errorCode: String?,
            errorMessage: String?,
            completedAt: Long?
        ) -> Unit
    ): JobObject {
        var job = currentJob
        repeat(maxPollAttempts) {
            if (job.isReadyForTransfer()) {
                updateJob(
                    job.effectiveStatus(),
                    job.progressPercent ?: 100,
                    job.apiJobId,
                    job.actualFormat,
                    null,
                    null,
                    null
                )
                return job
            }

            if (job.isFailed()) {
                throw YoutubeImportException(
                    message = job.error?.message
                        ?: job.errorMessage
                        ?: "The API could not prepare this YouTube download.",
                    errorCode = job.error?.code ?: job.errorCode ?: job.status,
                    retryable = job.retryable
                )
            }

            updateJob(
                job.effectiveStatus(),
                job.progressPercent,
                job.apiJobId,
                job.actualFormat,
                null,
                null,
                null
            )

            if (pollDelayMs > 0L) delay(pollDelayMs)
            job = apiRepository.getJob(job.apiJobId).job
        }

        updateJob(
            STATUS_NEEDS_ATTENTION,
            job.progressPercent,
            job.apiJobId,
            job.actualFormat,
            ERROR_POLL_TIMEOUT,
            "This import is still running. Reopen Import later to restore it.",
            null
        )
        throw YoutubeImportException(
            message = "This import is still running. Reopen Import later to restore it.",
            errorCode = ERROR_POLL_TIMEOUT,
            retryable = true
        )
    }

    private suspend fun transferAndImport(
        searchResult: YoutubeSearchResultItem,
        readyJob: JobObject,
        importJob: ImportJobEntity,
        updateJob: suspend (
            status: String,
            progressPercent: Int?,
            apiJobId: String?,
            actualFormat: String?,
            errorCode: String?,
            errorMessage: String?,
            completedAt: Long?
        ) -> Unit
    ): YoutubeImportItemResult {
        updateJob(
            STATUS_COPYING,
            readyJob.progressPercent ?: 100,
            readyJob.apiJobId,
            readyJob.actualFormat,
            null,
            null,
            null
        )

        val response = apiRepository.getJobFile(readyJob.apiJobId)
        val body = response.body() ?: throw YoutubeImportException(
            message = "The API returned an empty file transfer.",
            errorCode = ERROR_EMPTY_TRANSFER,
            retryable = true
        )
        val expectedSha256 = YoutubeTransferRules.normalizeSha256(
            response.headers()["X-Fenlzer-SHA256"] ?: readyJob.file?.sha256
        ) ?: throw YoutubeImportException(
            message = "The API did not provide a SHA-256 hash for verification.",
            errorCode = ERROR_MISSING_HASH,
            retryable = false
        )
        val filename = response.headers().contentDispositionFilename()
            ?: readyJob.file?.filename
            ?: "${readyJob.source.youtubeVideoId ?: readyJob.apiJobId}.m4a"
        val extension = YoutubeTransferRules.extensionFromFilename(filename)
        val tempFile = File.createTempFile(
            "youtube-import-${importJob.importJobId}-",
            ".$extension",
            storage.tempImportDir
        )
        var stagedAudioFile: File? = null
        var trackInserted = false

        return try {
            val transfer = streamToTempFile(body, tempFile) { percent ->
                updateJob(STATUS_COPYING, percent, readyJob.apiJobId, readyJob.actualFormat, null, null, null)
            }
            if (transfer.sha256 != expectedSha256) {
                tempFile.delete()
                throw YoutubeImportException(
                    message = "The downloaded file did not match the API SHA-256 hash.",
                    errorCode = ERROR_HASH_MISMATCH,
                    retryable = true
                )
            }

            trackDao.getTrackByAudioHash(transfer.sha256)?.let { duplicate ->
                tempFile.delete()
                confirmTransferredFile(
                    readyJob = readyJob,
                    sha256 = transfer.sha256,
                    sizeBytes = transfer.bytesCopied,
                    localTrackId = duplicate.trackId
                )
                completeImportedTrack(importJob, searchResult.remoteItemId, duplicate.trackId)
                promoteArtworkIfNeeded(duplicate, searchResult.thumbnailUrl)
                updateJob(
                    ApiJobState.TRANSFER_CONFIRMED,
                    100,
                    readyJob.apiJobId,
                    readyJob.actualFormat,
                    null,
                    null,
                    now()
                )
                val message = "Already imported as ${duplicate.displayTitle()}."
                insertHistory(
                    importJobId = importJob.importJobId,
                    result = HISTORY_DUPLICATE,
                    displayTitle = searchResult.title,
                    trackId = duplicate.trackId,
                    youtubeVideoId = searchResult.youtubeVideoId,
                    sourceUrl = searchResult.sourceUrl,
                    friendlyMessage = message,
                    errorCode = ERROR_DUPLICATE
                )
                return YoutubeImportItemResult(
                    importJobId = importJob.importJobId,
                    trackId = duplicate.trackId,
                    displayTitle = duplicate.displayTitle(),
                    outcome = YoutubeImportOutcome.DUPLICATE,
                    duplicateTrackId = duplicate.trackId,
                    message = message
                )
            }

            updateJob(
                STATUS_EXTRACTING_METADATA,
                100,
                readyJob.apiJobId,
                readyJob.actualFormat,
                null,
                null,
                null
            )
            val finalAudioFile = storage.audioFile(transfer.sha256, extension)
            stagedAudioFile = finalAudioFile
            moveTempFile(tempFile, finalAudioFile)
            val importedAt = now()
            val trackId = idFactory()
            val remoteThumbnailUrl = readyJob.metadata?.thumbnailUrl ?: searchResult.thumbnailUrl
            val promotedThumbnail = thumbnailPromoter.download(remoteThumbnailUrl)
                ?.canonicalThumbnail()
            val track = readyJob.toTrackEntity(
                searchResult = searchResult,
                importJob = importJob,
                trackId = trackId,
                internalFilename = finalAudioFile.name,
                audioHash = transfer.sha256,
                fileSizeBytes = transfer.bytesCopied,
                finalAudioFormat = readyJob.actualFormat ?: extension.uppercase(Locale.US),
                thumbnailAssetId = promotedThumbnail?.asset?.thumbnailAssetId,
                importedAt = importedAt
            )
            val originalMetadata = track.toOriginalMetadataEntity()

            try {
                trackDao.insertTrackWithOriginalMetadataAndThumbnail(
                    track = track,
                    originalMetadata = originalMetadata,
                    thumbnailAsset = promotedThumbnail?.asset
                )
            } catch (throwable: Throwable) {
                promotedThumbnail?.cleanupUncommitted()
                throw throwable
            }
            trackInserted = true
            completeImportedTrack(importJob, searchResult.remoteItemId, trackId)
            confirmTransferredFile(
                readyJob = readyJob,
                sha256 = transfer.sha256,
                sizeBytes = transfer.bytesCopied,
                localTrackId = trackId
            )
            updateJob(
                ApiJobState.TRANSFER_CONFIRMED,
                100,
                readyJob.apiJobId,
                readyJob.actualFormat,
                null,
                null,
                now()
            )
            val displayTitle = track.displayTitle()
            insertHistory(
                importJobId = importJob.importJobId,
                result = HISTORY_SUCCESS,
                displayTitle = displayTitle,
                trackId = trackId,
                youtubeVideoId = track.youtubeVideoId,
                sourceUrl = track.sourceUrl,
                friendlyMessage = "Imported successfully."
            )
            YoutubeImportItemResult(
                importJobId = importJob.importJobId,
                trackId = trackId,
                displayTitle = displayTitle,
                outcome = YoutubeImportOutcome.SUCCESS,
                message = "Imported successfully."
            )
        } catch (cancellation: CancellationException) {
            tempFile.delete()
            if (!trackInserted) stagedAudioFile?.delete()
            throw cancellation
        } catch (throwable: Throwable) {
            tempFile.delete()
            if (!trackInserted) stagedAudioFile?.delete()
            throw throwable
        }
    }

    private suspend fun confirmTransferredFile(
        readyJob: JobObject,
        sha256: String,
        sizeBytes: Long,
        localTrackId: String
    ) {
        apiRepository.confirmJobFile(
            jobId = readyJob.apiJobId,
            request = ConfirmFileRequest(
                clientJobId = readyJob.clientJobId,
                receivedSha256 = sha256,
                receivedSizeBytes = sizeBytes,
                localTrackId = localTrackId,
                importedAt = Instant.ofEpochMilli(now()).toString()
            )
        )
    }

    private suspend fun streamToTempFile(
        body: ResponseBody,
        tempFile: File,
        onProgress: suspend (Int) -> Unit
    ): TransferResult {
        val digest = MessageDigest.getInstance("SHA-256")
        val totalBytes = body.contentLength().takeIf { it > 0L }
        var bytesCopied = 0L
        var lastPercent = -1

        body.use { responseBody ->
            responseBody.byteStream().use { source ->
                tempFile.outputStream().use { target ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = source.read(buffer)
                        if (read == -1) break
                        target.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        bytesCopied += read

                        if (totalBytes != null) {
                            val percent = ((bytesCopied * 100L) / totalBytes)
                                .toInt()
                                .coerceIn(0, 100)
                            if (percent >= lastPercent + 5 || percent == 100) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
        }

        return TransferResult(
            sha256 = Sha256.bytesToHex(digest.digest()),
            bytesCopied = bytesCopied
        )
    }

    private suspend fun duplicateByStrongIdentifier(
        result: YoutubeSearchResultItem
    ): TrackEntity? {
        result.youtubeVideoId?.let { youtubeVideoId ->
            trackDao.getTrackByYoutubeVideoId(youtubeVideoId)?.let { return it }
        }
        result.sourceUrl?.let { sourceUrl ->
            trackDao.getTrackBySourceUrl(sourceUrl)?.let { return it }
        }
        return null
    }

    private suspend fun ImportJobEntity.toRecoveredSearchResult(): YoutubeSearchResultItem {
        val remoteItem = remoteItemId?.let { remoteDiscoverDao.getRemoteItem(it) }
        val fallbackRemoteItemId = remoteItemId ?: apiJobId ?: importJobId
        return YoutubeSearchResultItem(
            remoteItemId = fallbackRemoteItemId,
            youtubeVideoId = remoteItem?.youtubeVideoId ?: youtubeVideoId,
            sourceUrl = remoteItem?.sourceUrl ?: sourceUrl,
            title = remoteItem?.title
                ?: technicalDetailsJson
                ?: youtubeVideoId
                ?: sourceUrl
                ?: "YouTube import",
            artistOrChannel = remoteItem?.artistOrChannel,
            durationMs = remoteItem?.durationMs,
            thumbnailUrl = remoteItem?.thumbnailUrl,
            canStream = remoteItem?.canStream ?: true,
            canDownload = remoteItem?.canDownload ?: true,
            isLive = false,
            isUnavailable = false
        )
    }

    private suspend fun completeImportedTrack(
        importJob: ImportJobEntity,
        remoteItemId: String,
        trackId: String
    ) {
        val timestamp = now()
        val completeReferences: suspend () -> Unit = {
            remoteDiscoverDao.markImported(
                remoteItemId = remoteItemId,
                importState = REMOTE_IMPORT_IMPORTED,
                trackId = trackId,
                updatedAt = timestamp
            )
            queueDao?.convertRemoteItemToTrack(remoteItemId = remoteItemId, trackId = trackId)
            if (importJob.targetFavourite) {
                trackDao.setFavourite(trackId, true, timestamp, timestamp)
            }
            importJob.targetPlaylistId?.let { playlistId ->
                playlistDao?.addTrackIfMissing(playlistId, trackId, timestamp)
            }
        }
        if (database != null) {
            database.withTransaction { completeReferences() }
        } else {
            completeReferences()
        }
        statsRepository?.mergeRemoteItemIntoTrack(remoteItemId = remoteItemId, trackId = trackId)
    }

    private suspend fun promoteArtworkIfNeeded(track: TrackEntity, sourceUrl: String?) {
        if (track.thumbnailAssetId != null || track.sourceType == ImportSourceType.LOCAL_FILE) return
        val effectiveUrl = sourceUrl ?: track.remoteThumbnailUrl
        val promoted = thumbnailPromoter.download(effectiveUrl)?.canonicalThumbnail() ?: return
        try {
            trackDao.updateTrackWithThumbnailAsset(
                track = track.copy(
                    thumbnailAssetId = promoted.asset.thumbnailAssetId,
                    remoteThumbnailUrl = effectiveUrl,
                    updatedAt = now()
                ),
                thumbnailAsset = promoted.asset
            )
        } catch (throwable: Throwable) {
            promoted.cleanupUncommitted()
            throw throwable
        }
    }

    private suspend fun PromotedYoutubeThumbnail.canonicalThumbnail(): PromotedYoutubeThumbnail {
        val contentHash = asset.contentHash ?: return this
        val existing = trackDao.getPermanentThumbnailAssetByHash(contentHash) ?: return this
        return copy(asset = existing, createdFile = false)
    }

    private suspend fun ImportJobEntity.remoteItem(): RemoteItemEntity? =
        remoteItemId?.let { remoteDiscoverDao.getRemoteItem(it) }

    private suspend fun persistPreviewRemoteItems(items: List<PlaylistPreviewItem>) {
        val createdAt = now()
        remoteDiscoverDao.upsertRemoteItems(items.map { item -> item.toRemoteItem(createdAt) })
    }

    private suspend fun insertHistory(
        importJobId: String?,
        result: String,
        displayTitle: String,
        trackId: String? = null,
        youtubeVideoId: String? = null,
        sourceUrl: String? = null,
        friendlyMessage: String? = null,
        errorCode: String? = null,
        technicalDetailsJson: String? = null
    ) {
        val job = importJobId?.let { id -> importDao.getJob(id) }
        importDao.insertHistoryEntry(
            ImportHistoryEntryEntity(
                historyId = idFactory(),
                importJobId = importJobId,
                result = result,
                reason = job?.reason ?: ImportReason.LEGACY_YOUTUBE,
                sourceType = job?.sourceType ?: ImportSourceType.LEGACY_YOUTUBE,
                jobType = job?.jobType,
                requestedFormat = job?.preferredFormat,
                finalFormat = job?.actualFormat,
                pendingActionType = job?.pendingActionType,
                trackId = trackId,
                sourceUrl = sourceUrl,
                youtubeVideoId = youtubeVideoId,
                displayTitle = displayTitle,
                errorCode = errorCode,
                friendlyMessage = friendlyMessage,
                technicalDetailsJson = technicalDetailsJson,
                createdAt = now()
            )
        )
    }

    private suspend fun ensureTerminalFailureHistory(job: ImportJobEntity) {
        if (importDao.countHistoryForJob(job.importJobId) > 0) return
        insertHistory(
            importJobId = job.importJobId,
            result = HISTORY_FAILED,
            displayTitle = job.displayTitle(),
            youtubeVideoId = job.youtubeVideoId,
            sourceUrl = job.sourceUrl,
            friendlyMessage = job.errorMessage ?: "This import failed.",
            errorCode = job.errorCode
        )
    }

    private fun cleanupTemporaryFiles(importJobId: String) {
        storage.ensureDirectories()
        storage.tempImportDir.listFiles()
            ?.filter { file -> file.name.startsWith("youtube-import-$importJobId-") }
            ?.forEach(File::delete)
    }

    private fun moveTempFile(tempFile: File, finalAudioFile: File) {
        if (finalAudioFile.exists()) {
            tempFile.delete()
            return
        }
        if (!tempFile.renameTo(finalAudioFile)) {
            tempFile.copyTo(finalAudioFile, overwrite = false)
            tempFile.delete()
        }
    }

    private fun SearchResult.toImportItem(): YoutubeSearchResultItem =
        YoutubeSearchResultItem(
            remoteItemId = remoteItemId,
            youtubeVideoId = youtubeVideoId,
            sourceUrl = sourceUrl,
            title = title,
            artistOrChannel = artistOrChannel,
            durationMs = durationMs,
            thumbnailUrl = thumbnailUrl,
            canStream = canStream,
            canDownload = canDownload,
            isLive = isLive,
            isUnavailable = isUnavailable
        )

    private fun YoutubeSearchResultItem.toRemoteItem(createdAt: Long): RemoteItemEntity =
        RemoteItemEntity(
            remoteItemId = remoteItemId,
            youtubeVideoId = youtubeVideoId,
            sourceUrl = sourceUrl,
            title = title,
            artistOrChannel = artistOrChannel,
            durationMs = durationMs,
            thumbnailUrl = thumbnailUrl,
            canStream = canStream,
            canDownload = canDownload,
            streamState = REMOTE_STREAM_UNRESOLVED,
            lastPlayableUrl = null,
            playableUrlExpiresAt = null,
            lastResolvedAt = null,
            importState = REMOTE_IMPORT_NOT_IMPORTED,
            importedTrackId = null,
            createdAt = createdAt,
            updatedAt = createdAt
        )

    private fun PlaylistPreviewItem.toRemoteItem(createdAt: Long): RemoteItemEntity =
        RemoteItemEntity(
            remoteItemId = remoteItemId,
            youtubeVideoId = youtubeVideoId,
            sourceUrl = sourceUrl,
            title = title,
            artistOrChannel = artistOrChannel,
            durationMs = durationMs,
            thumbnailUrl = thumbnailUrl,
            canStream = canStream,
            canDownload = canDownload,
            streamState = REMOTE_STREAM_UNRESOLVED,
            lastPlayableUrl = null,
            playableUrlExpiresAt = null,
            lastResolvedAt = null,
            importState = REMOTE_IMPORT_NOT_IMPORTED,
            importedTrackId = null,
            createdAt = createdAt,
            updatedAt = createdAt
        )

    private fun YoutubePlaylistPreviewItem.toRemoteItem(createdAt: Long): RemoteItemEntity =
        RemoteItemEntity(
            remoteItemId = remoteItemId,
            youtubeVideoId = youtubeVideoId,
            sourceUrl = sourceUrl,
            title = title,
            artistOrChannel = artistOrChannel,
            durationMs = durationMs,
            thumbnailUrl = thumbnailUrl,
            canStream = true,
            canDownload = canDownload,
            streamState = REMOTE_STREAM_UNRESOLVED,
            lastPlayableUrl = null,
            playableUrlExpiresAt = null,
            lastResolvedAt = null,
            importState = REMOTE_IMPORT_NOT_IMPORTED,
            importedTrackId = null,
            createdAt = createdAt,
            updatedAt = createdAt
        )

    private fun PlaylistPreviewData.toPreview(): YoutubePlaylistPreview =
        YoutubePlaylistPreview(
            previewId = previewId,
            status = status,
            title = playlistTitle,
            thumbnailUrl = playlistThumbnailUrl,
            totalExpectedItems = totalExpectedItems,
            loadedItemCount = loadedItemCount,
            items = items.map { item ->
                YoutubePlaylistPreviewItem(
                    position = item.position,
                    remoteItemId = item.remoteItemId,
                    youtubeVideoId = item.youtubeVideoId,
                    sourceUrl = item.sourceUrl,
                    title = item.title,
                    artistOrChannel = item.artistOrChannel,
                    durationMs = item.durationMs,
                    thumbnailUrl = item.thumbnailUrl,
                    canDownload = item.canDownload,
                    availability = item.availability,
                    alreadyKnownByClient = item.alreadyKnownByClient
                )
            }
        )

    private fun JobObject.isReadyForTransfer(): Boolean {
        val status = effectiveStatus()
        return file?.available == true ||
            status == ApiJobState.READY_FOR_TRANSFER ||
            (status == ApiJobState.COMPLETED && file != null)
    }

    private fun JobObject.isFailed(): Boolean {
        val status = effectiveStatus()
        return status == ApiJobState.FAILED ||
            status == ApiJobState.CANCELLED ||
            status == ApiJobState.EXPIRED ||
            status == ApiJobState.NEEDS_ATTENTION ||
            status == ApiJobState.UNKNOWN
    }

    private fun JobObject.effectiveStatus(): String =
        normalizeJobStatus(status = status, state = state)

    private fun CompactJobStatus.effectiveStatus(): String =
        normalizeJobStatus(status = status, state = state)

    private fun String.restoredLocalStatus(): String =
        when {
            ApiJobState.isTerminalSuccess(this) -> ApiJobState.READY_FOR_TRANSFER
            ApiJobState.isTerminalFailure(this) -> STATUS_NEEDS_ATTENTION
            this == ApiJobState.UNKNOWN -> STATUS_NEEDS_ATTENTION
            else -> this
        }

    private fun normalizeJobStatus(status: String?, state: String?): String {
        val normalizedState = state
            ?.trim()
            ?.uppercase(Locale.US)
            ?.takeIf { it.isNotBlank() && it != ApiJobState.UNKNOWN }
        val normalizedStatus = status
            ?.trim()
            ?.uppercase(Locale.US)
            ?.takeIf { it.isNotBlank() }
            ?: ApiJobState.UNKNOWN
        return when (normalizedState ?: normalizedStatus) {
            ApiJobState.RUNNING -> normalizedState ?: ApiJobState.RUNNING
            ApiJobState.PROCESSING -> ApiJobState.POST_PROCESSING
            else -> normalizedState ?: normalizedStatus
        }
    }

    private fun JobObject.toTrackEntity(
        searchResult: YoutubeSearchResultItem,
        importJob: ImportJobEntity,
        trackId: String,
        internalFilename: String,
        audioHash: String,
        fileSizeBytes: Long,
        finalAudioFormat: String,
        thumbnailAssetId: String?,
        importedAt: Long
    ): TrackEntity {
        val metadataTitle = metadata?.title ?: displayTitle ?: searchResult.title
        val displayTitle = AudioTitleFormatter.displayTitle(
            title = metadataTitle,
            fallbackFilename = file?.filename
        )
        val artist = metadata?.artistOrChannel ?: searchResult.artistOrChannel.orEmpty()
        val sourceVideoId = youtubeVideoId ?: source.youtubeVideoId ?: searchResult.youtubeVideoId
        val sourceUrl = sourceUrl ?: source.sourceUrl ?: searchResult.sourceUrl

        return TrackEntity(
            trackId = trackId,
            title = displayTitle,
            titleSortKey = SearchNormalizer.sortKey(displayTitle),
            artist = artist,
            artistSortKey = SearchNormalizer.sortKey(artist),
            album = "",
            albumSortKey = "",
            albumArtist = "",
            albumArtistSortKey = "",
            genre = "",
            year = null,
            trackNumber = null,
            discNumber = null,
            durationMs = metadata?.durationMs ?: searchResult.durationMs ?: 0L,
            notes = "",
            sourceType = importJob.sourceType ?: ImportSourceType.LEGACY_YOUTUBE,
            importReason = importJob.reason ?: ImportReason.LEGACY_YOUTUBE,
            requestedDownloadFormat = importJob.preferredFormat,
            youtubeVideoId = sourceVideoId,
            sourceUrl = sourceUrl,
            originalFilename = file?.filename,
            internalFilename = internalFilename,
            audioHash = audioHash,
            fileSizeBytes = fileSizeBytes,
            finalAudioFormat = finalAudioFormat,
            thumbnailAssetId = thumbnailAssetId,
            embeddedThumbnailAssetId = null,
            remoteThumbnailUrl = metadata?.thumbnailUrl ?: searchResult.thumbnailUrl,
            isFavourite = false,
            favouritedAt = null,
            importedAt = importedAt,
            updatedAt = importedAt
        )
    }

    private fun TrackEntity.toOriginalMetadataEntity(): TrackOriginalMetadataEntity =
        TrackOriginalMetadataEntity(
            trackId = trackId,
            originalTitle = title,
            originalArtist = artist,
            originalAlbum = album,
            originalAlbumArtist = albumArtist,
            originalGenre = genre,
            originalYear = year,
            originalTrackNumber = trackNumber,
            originalDiscNumber = discNumber,
            originalThumbnailKind = when {
                thumbnailAssetId != null -> "YOUTUBE"
                remoteThumbnailUrl != null -> "YOUTUBE_REMOTE"
                else -> "NONE"
            },
            rawMetadataJson = null
        )

    private fun TrackEntity.displayTitle(): String =
        AudioTitleFormatter.displayTitle(title = title, fallbackFilename = originalFilename)

    private fun ImportJobEntity.displayTitle(): String {
        return technicalDetailsJson
            ?.takeIf { it.isNotBlank() }
            ?: youtubeVideoId
            ?: sourceUrl
            ?: "Import job"
    }

    private fun ImportJobEntity.sourceLabel(): String =
        sourceType.toSourceLabel()

    private fun String?.toSourceLabel(): String = when (this) {
        ImportSourceType.LOCAL_FILE -> "Local file"
        ImportSourceType.YOUTUBE_SEARCH -> "YouTube search"
        ImportSourceType.YOUTUBE_PLAYLIST -> "YouTube playlist"
        ImportSourceType.DISCOVER_MANUAL -> "Discover"
        ImportSourceType.DISCOVER_AUTO_FAVOURITE -> "Discover auto-favourite"
        ImportSourceType.DISCOVER_AUTO_PLAYLIST -> "Discover auto-playlist"
        ImportSourceType.LEGACY_YOUTUBE -> "YouTube"
        else -> this?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.titlecase() }
            ?: "Import"
    }

    private fun String.toLocalPriority(): Int =
        if (this == ImportPriorityClass.AUTO) PRIORITY_AUTO else PRIORITY_MANUAL

    private fun ImportJobEntity.apiPriorityClass(): String =
        if (priorityClass == ImportPriorityClass.AUTO) ApiPriorityClass.AUTO else ApiPriorityClass.MANUAL

    private fun ImportJobEntity.apiJobType(): String = when (sourceType) {
        ImportSourceType.DISCOVER_AUTO_FAVOURITE -> JOB_TYPE_DISCOVER_AUTO_FAVOURITE
        ImportSourceType.DISCOVER_AUTO_PLAYLIST -> JOB_TYPE_DISCOVER_AUTO_PLAYLIST
        else -> jobType
    }

    private fun ImportJobEntity.apiTarget() = buildJsonObject {
        put("favouriteAfterImport", JsonPrimitive(targetFavourite))
        put("playlistIdAfterImport", targetPlaylistId?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun okhttp3.Headers.contentDispositionFilename(): String? {
        val disposition = this["Content-Disposition"] ?: return null
        return Regex("""filename="?([^";]+)"?""")
            .find(disposition)
            ?.groupValues
            ?.getOrNull(1)
    }

    private data class TransferResult(
        val sha256: String,
        val bytesCopied: Long
    )

    companion object {
        const val JOB_TYPE_YOUTUBE_SEARCH = "YOUTUBE_SEARCH"
        private const val JOB_TYPE_YOUTUBE_PLAYLIST_ITEM = "YOUTUBE_PLAYLIST_ITEM"
        private const val JOB_TYPE_DISCOVER_AUTO_FAVOURITE = "DISCOVER_AUTO_FAVOURITE"
        private const val JOB_TYPE_DISCOVER_AUTO_PLAYLIST = "DISCOVER_AUTO_PLAYLIST"
        private const val PRIORITY_MANUAL = 1_000
        private const val PRIORITY_AUTO = 0
        private const val STATUS_QUEUED = "QUEUED"
        private const val STATUS_COPYING = "COPYING"
        private const val STATUS_EXTRACTING_METADATA = "EXTRACTING_METADATA"
        private const val STATUS_COMPLETED = "COMPLETED"
        private const val STATUS_DUPLICATE = "DUPLICATE"
        private const val STATUS_FAILED = "FAILED"
        private const val STATUS_NEEDS_ATTENTION = "NEEDS_ATTENTION"
        private const val HISTORY_SUCCESS = "SUCCESS"
        private const val HISTORY_DUPLICATE = "DUPLICATE"
        private const val HISTORY_FAILED = "FAILED"
        private const val HISTORY_CANCELLED = "CANCELLED"
        private const val ERROR_DUPLICATE = "DUPLICATE_TRACK"
        private const val ERROR_IMPORT_FAILED = "YOUTUBE_IMPORT_FAILED"
        private const val ERROR_POLL_TIMEOUT = "POLL_TIMEOUT"
        private const val ERROR_HASH_MISMATCH = "SHA256_MISMATCH"
        private const val ERROR_MISSING_HASH = "MISSING_SHA256"
        private const val ERROR_EMPTY_TRANSFER = "EMPTY_TRANSFER"
        private const val ERROR_RESTORE_STATUS_FAILED = "RESTORE_STATUS_FAILED"
        private const val ERROR_RESTORE_STATUS_UNKNOWN = "RESTORE_STATUS_UNKNOWN"
        private const val ERROR_RESTORE_CONFIRMED_WITHOUT_LOCAL_TRACK =
            "RESTORE_CONFIRMED_WITHOUT_LOCAL_TRACK"
        private const val REMOTE_STREAM_UNRESOLVED = "UNRESOLVED"
        private const val REMOTE_IMPORT_NOT_IMPORTED = "NOT_IMPORTED"
        private const val REMOTE_IMPORT_IMPORTED = "IMPORTED"
        private val retryableLocalStatuses = setOf(
            STATUS_FAILED,
            STATUS_NEEDS_ATTENTION,
            ApiJobState.FAILED,
            ApiJobState.EXPIRED,
            ApiJobState.UNKNOWN
        )
        private val cancellableLocalStatuses = setOf(
            STATUS_QUEUED,
            ApiJobState.QUEUED,
            ApiJobState.DOWNLOADING_METADATA,
            ApiJobState.DOWNLOADING,
            ApiJobState.POST_PROCESSING,
            ApiJobState.PROCESSING,
            ApiJobState.RUNNING,
            ApiJobState.READY_FOR_TRANSFER,
            STATUS_COPYING,
            STATUS_EXTRACTING_METADATA,
            STATUS_NEEDS_ATTENTION
        )
        private val localTransferStatuses = setOf(
            STATUS_COPYING,
            STATUS_EXTRACTING_METADATA,
            ApiJobState.TRANSFER_CONFIRMED
        )
    }
}

class YoutubeImportException(
    override val message: String,
    val errorCode: String,
    val retryable: Boolean
) : IOException(message)

private class YoutubeImportCancelledException : IOException("Cancelled by user.")

object YoutubeTransferRules {
    fun normalizeSha256(value: String?): String? {
        val normalized = value
            ?.trim()
            ?.removePrefix("sha256:")
            ?.lowercase(Locale.US)
            ?: return null
        return normalized.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
    }

    fun extensionFromFilename(filename: String): String {
        return filename
            .substringAfterLast('.', missingDelimiterValue = "m4a")
            .lowercase(Locale.US)
            .takeIf { it in setOf("mp3", "m4a", "wav", "flac", "ogg") }
            ?: "m4a"
    }
}


private fun String.extractYoutubeVideoId(): String? {
    val trimmed = trim()
    val watchMatch = Regex("[?&]v=([A-Za-z0-9_-]{11})").find(trimmed)?.groupValues?.getOrNull(1)
    if (watchMatch != null) return watchMatch
    val shortMatch = Regex("youtu\\.be/([A-Za-z0-9_-]{11})").find(trimmed)?.groupValues?.getOrNull(1)
    if (shortMatch != null) return shortMatch
    val shortsMatch = Regex("/shorts/([A-Za-z0-9_-]{11})").find(trimmed)?.groupValues?.getOrNull(1)
    if (shortsMatch != null) return shortsMatch
    val embedMatch = Regex("/embed/([A-Za-z0-9_-]{11})").find(trimmed)?.groupValues?.getOrNull(1)
    if (embedMatch != null) return embedMatch
    return trimmed.takeIf { Regex("^[A-Za-z0-9_-]{11}$").matches(it) }
}

private fun String.toExactYoutubeUrlResult(videoId: String): YoutubeSearchResultItem {
    val canonicalUrl = "https://www.youtube.com/watch?v=$videoId"
    return YoutubeSearchResultItem(
        remoteItemId = "youtube:$videoId",
        youtubeVideoId = videoId,
        sourceUrl = canonicalUrl,
        title = "YouTube video $videoId",
        artistOrChannel = "YouTube",
        durationMs = null,
        thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
        canStream = true,
        canDownload = true,
        isLive = false,
        isUnavailable = false
    )
}

private fun String.isLikelyYoutubeUrl(): Boolean =
    extractYoutubeVideoId() != null ||
        trim().lowercase(Locale.US).startsWith("http://") ||
        trim().lowercase(Locale.US).startsWith("https://")
