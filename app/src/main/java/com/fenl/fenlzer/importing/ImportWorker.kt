package com.fenl.fenlzer.importing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.fenl.fenlzer.FenlzerApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

class ImportWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = coroutineScope {
        val importJobId = inputData.getString(ImportQueueCoordinator.INPUT_IMPORT_JOB_ID)
            ?: return@coroutineScope Result.failure()
        val graph = (applicationContext as? FenlzerApplication)?.appGraph
            ?: return@coroutineScope Result.failure()
        val coordinator = graph.importQueueCoordinator
            ?: return@coroutineScope Result.failure()
        val importDao = graph.database?.importDao()
            ?: return@coroutineScope Result.failure()
        val notifications = ImportNotificationController(applicationContext, importDao)

        setForeground(notifications.foregroundInfo())
        coordinator.recordAttempt(importJobId)
        coordinator.getJob(importJobId)?.let { job ->
            if (job.attemptCount > job.maxAttempts) {
                coordinator.finalizeRetryExhausted(importJobId)
                return@coroutineScope Result.failure(
                    workDataOf(ERROR_MESSAGE to "Import retry limit reached.")
                )
            }
        }
        val notificationUpdater = launch {
            while (isActive) {
                setForeground(notifications.foregroundInfo())
                delay(NOTIFICATION_REFRESH_MS)
            }
        }

        try {
            val execution = coordinator.execute(importJobId) { progress ->
                setProgressAsync(
                    workDataOf(
                        PROGRESS_TITLE to progress.title,
                        PROGRESS_STATUS to progress.status,
                        PROGRESS_PERCENT to (progress.progressPercent ?: -1)
                    )
                )
            }
            when (execution) {
                ImportExecutionResult.Completed,
                ImportExecutionResult.Cancelled -> Result.success()

                is ImportExecutionResult.TerminalFailure -> Result.failure(
                    workDataOf(ERROR_MESSAGE to execution.message)
                )

                is ImportExecutionResult.RetryableFailure -> {
                    val job = coordinator.getJob(importJobId)
                    if (job != null && ImportRetryPolicy.canRetry(job.attemptCount, job.maxAttempts)) {
                        Result.retry()
                    } else {
                        coordinator.finalizeRetryExhausted(importJobId)
                        Result.failure(workDataOf(ERROR_MESSAGE to execution.message))
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } finally {
            notificationUpdater.cancel()
        }
    }

    companion object {
        const val PROGRESS_TITLE = "progress_title"
        const val PROGRESS_STATUS = "progress_status"
        const val PROGRESS_PERCENT = "progress_percent"
        const val ERROR_MESSAGE = "error_message"
        private const val NOTIFICATION_REFRESH_MS = 1_000L
    }
}
