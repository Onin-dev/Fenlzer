package com.fenl.fenlzer.importing.local

import android.net.Uri
import com.fenl.fenlzer.data.local.entity.ImportHistoryEntryEntity
import com.fenl.fenlzer.data.local.entity.ImportJobEntity

object LocalImportBatchResultMapper {
    fun map(
        expectedJobIds: List<String>,
        jobs: List<ImportJobEntity>,
        history: List<ImportHistoryEntryEntity>,
        startedAt: Long
    ): LocalImportBatchResult? {
        if (expectedJobIds.isEmpty()) return null
        val jobsById = jobs.associateBy { it.importJobId }
        val orderedJobs = expectedJobIds.mapNotNull(jobsById::get)
        if (orderedJobs.size != expectedJobIds.size || orderedJobs.any { it.status !in terminalStatuses }) {
            return null
        }
        val latestHistoryByJob = history
            .filter { it.importJobId != null }
            .groupBy { it.importJobId }
            .mapValues { (_, entries) -> entries.maxBy { it.createdAt } }
        if (orderedJobs.any { latestHistoryByJob[it.importJobId] == null }) return null

        val items = orderedJobs.map { job ->
            val entry = requireNotNull(latestHistoryByJob[job.importJobId])
            val outcome = when (entry.result) {
                RESULT_SUCCESS -> LocalImportOutcome.SUCCESS
                RESULT_DUPLICATE -> LocalImportOutcome.DUPLICATE
                else -> LocalImportOutcome.FAILED
            }
            LocalImportItemResult(
                filename = job.technicalDetailsJson ?: entry.displayTitle,
                displayTitle = entry.displayTitle,
                outcome = outcome,
                sourceUri = job.sourceUrl?.let(Uri::parse),
                trackId = entry.trackId.takeIf { outcome == LocalImportOutcome.SUCCESS },
                duplicateTrackId = entry.trackId.takeIf { outcome == LocalImportOutcome.DUPLICATE },
                message = entry.friendlyMessage ?: job.errorMessage,
                metadataWarning = entry.friendlyMessage
                    ?.contains("empty metadata", ignoreCase = true) == true
            )
        }
        return LocalImportBatchResult(
            items = items,
            startedAt = startedAt,
            completedAt = orderedJobs.maxOf { it.completedAt ?: it.updatedAt }
        )
    }

    private val terminalStatuses = setOf(
        "COMPLETED",
        "DUPLICATE",
        "FAILED",
        "CANCELLED"
    )
    private const val RESULT_SUCCESS = "SUCCESS"
    private const val RESULT_DUPLICATE = "DUPLICATE"
}
