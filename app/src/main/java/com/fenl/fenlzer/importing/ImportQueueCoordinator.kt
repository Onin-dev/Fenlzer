package com.fenl.fenlzer.importing

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.fenl.fenlzer.data.local.dao.ImportDao
import com.fenl.fenlzer.importing.local.LocalImportProgress
import com.fenl.fenlzer.importing.local.LocalImportRepository
import com.fenl.fenlzer.importing.youtube.ActiveImportUiItem
import com.fenl.fenlzer.importing.youtube.ImportHistoryUiItem
import com.fenl.fenlzer.importing.youtube.YoutubeImportItemResult
import com.fenl.fenlzer.importing.youtube.YoutubeImportProgress
import com.fenl.fenlzer.importing.youtube.YoutubeImportRepository
import com.fenl.fenlzer.importing.youtube.YoutubePlaylistPreview
import com.fenl.fenlzer.importing.youtube.YoutubeSearchResultItem
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

data class ImportWorkerProgress(
    val title: String,
    val status: String,
    val progressPercent: Int?
)

class ImportQueueCoordinator(
    context: Context,
    private val importDao: ImportDao,
    private val localRepository: LocalImportRepository,
    private val youtubeRepository: YoutubeImportRepository,
    private val workManager: WorkManager = WorkManager.getInstance(context.applicationContext),
    private val now: () -> Long = System::currentTimeMillis
) {
    fun observeActiveImports(): Flow<List<ActiveImportUiItem>> =
        youtubeRepository.observeActiveImports()

    fun observeImportHistory(): Flow<List<ImportHistoryUiItem>> =
        youtubeRepository.observeImportHistory()

    suspend fun enqueueLocalImports(uris: List<Uri>): List<String> {
        val jobs = localRepository.prepareImportJobs(uris)
        jobs.forEach { job -> enqueue(job.importJobId, replace = false) }
        return jobs.map { it.importJobId }
    }

    suspend fun enqueueYoutube(
        result: YoutubeSearchResultItem,
        intent: ImportIntent = ImportIntent.youtubeSearch()
    ): YoutubeImportItemResult {
        val queued = youtubeRepository.prepareSearchImport(result, intent)
        enqueue(queued.importJobId, replace = false)
        return queued
    }

    suspend fun enqueueYoutubePlaylist(
        preview: YoutubePlaylistPreview,
        remoteItemIds: Set<String>,
        wholePlaylist: Boolean
    ): List<YoutubeImportItemResult> {
        val queued = youtubeRepository.importPlaylistItems(preview, remoteItemIds, wholePlaylist)
        queued.forEach { item -> enqueue(item.importJobId, replace = false) }
        return queued
    }

    suspend fun recover() {
        importDao.getRunnableJobs().forEach { job ->
            enqueue(job.importJobId, replace = false)
        }
    }

    suspend fun cancel(importJobId: String) {
        val job = importDao.getJob(importJobId) ?: return
        workManager.cancelUniqueWork(uniqueWorkName(importJobId))
        if (job.jobType == JOB_TYPE_LOCAL_FILE) {
            localRepository.cancelImport(importJobId)
        } else {
            youtubeRepository.cancelImport(importJobId)
        }
    }

    suspend fun retry(importJobId: String): YoutubeImportItemResult? {
        val job = importDao.getJob(importJobId) ?: return null
        val result = if (job.jobType == JOB_TYPE_LOCAL_FILE) {
            localRepository.prepareRetry(importJobId)
            YoutubeImportItemResult(
                importJobId = importJobId,
                trackId = null,
                displayTitle = job.technicalDetailsJson ?: "Local import",
                outcome = com.fenl.fenlzer.importing.youtube.YoutubeImportOutcome.QUEUED,
                message = "Retry queued."
            )
        } else {
            youtubeRepository.retryImport(importJobId)
        }
        enqueue(importJobId, replace = true)
        return result
    }

    suspend fun move(importJobId: String, offset: Int) {
        youtubeRepository.moveImport(importJobId, offset)
    }

    suspend fun acknowledgeFinishedJobs() = importDao.acknowledgeFinishedJobs()

    suspend fun dismissFailedJob(importJobId: String) = importDao.dismissFailedJob(importJobId)

    suspend fun recordAttempt(importJobId: String) {
        importDao.incrementAttempt(importJobId, now())
    }

    suspend fun execute(
        importJobId: String,
        onProgress: (ImportWorkerProgress) -> Unit
    ): ImportExecutionResult {
        val job = importDao.getJob(importJobId)
            ?: return ImportExecutionResult.TerminalFailure("Import job not found.")
        return if (job.jobType == JOB_TYPE_LOCAL_FILE) {
            localRepository.executeImportJob(importJobId) { progress: LocalImportProgress ->
                onProgress(
                    ImportWorkerProgress(
                        title = progress.filename,
                        status = progress.stage.name,
                        progressPercent = progress.percent
                    )
                )
            }
        } else {
            youtubeRepository.executeImportJob(importJobId) { progress: YoutubeImportProgress ->
                onProgress(
                    ImportWorkerProgress(
                        title = progress.displayTitle,
                        status = progress.status,
                        progressPercent = progress.progressPercent
                    )
                )
            }
        }
    }

    suspend fun finalizeRetryExhausted(importJobId: String) {
        val job = importDao.getJob(importJobId) ?: return
        if (job.jobType == JOB_TYPE_LOCAL_FILE) {
            localRepository.finalizeRetryExhausted(importJobId)
        } else {
            youtubeRepository.finalizeRetryExhausted(importJobId)
        }
    }

    suspend fun getJob(importJobId: String) = importDao.getJob(importJobId)

    private suspend fun enqueue(importJobId: String, replace: Boolean) {
        val job = importDao.getJob(importJobId) ?: return
        workManager.enqueueUniqueWork(
            uniqueWorkName(importJobId),
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            requestFor(job.importJobId, job.jobType != JOB_TYPE_LOCAL_FILE, job.priorityClass)
        )
    }

    companion object {
        const val INPUT_IMPORT_JOB_ID = "import_job_id"
        const val TAG_GLOBAL_IMPORT = "fenlzer_import"
        private const val JOB_TYPE_LOCAL_FILE = "LOCAL_FILE"

        fun uniqueWorkName(importJobId: String) = "fenlzer_import_$importJobId"

        fun requestFor(
            importJobId: String,
            requiresNetwork: Boolean,
            priorityClass: String?
        ): OneTimeWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (requiresNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED
                )
                .build()
            return OneTimeWorkRequestBuilder<ImportWorker>()
                .setInputData(workDataOf(INPUT_IMPORT_JOB_ID to importJobId))
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                .addTag(TAG_GLOBAL_IMPORT)
                .addTag("${TAG_GLOBAL_IMPORT}_$importJobId")
                .addTag("${TAG_GLOBAL_IMPORT}_${priorityClass ?: ImportPriorityClass.MANUAL}")
                .build()
        }
    }
}
