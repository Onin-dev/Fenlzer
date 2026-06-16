package com.fenl.fenlzer.importing.local

import com.fenl.fenlzer.data.local.entity.ImportHistoryEntryEntity
import com.fenl.fenlzer.data.local.entity.ImportJobEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalImportBatchResultMapperTest {
    @Test
    fun waitsForEveryJobAndMapsSuccessDuplicateAndFailure() {
        val jobs = listOf(
            job("success", "COMPLETED", 10L),
            job("duplicate", "DUPLICATE", 11L),
            job("failure", "FAILED", 12L)
        )
        val history = listOf(
            history("success", "SUCCESS", "track-1"),
            history("duplicate", "DUPLICATE", "track-2"),
            history("failure", "FAILED", null)
        )

        val result = LocalImportBatchResultMapper.map(
            expectedJobIds = jobs.map { it.importJobId },
            jobs = jobs,
            history = history,
            startedAt = 1L
        )

        requireNotNull(result)
        assertEquals(listOf("track-1"), result.successes.mapNotNull { it.trackId })
        assertEquals(listOf("track-2"), result.duplicates.mapNotNull { it.duplicateTrackId })
        assertEquals(1, result.failures.size)
        assertEquals(12L, result.completedAt)

        assertNull(
            LocalImportBatchResultMapper.map(
                expectedJobIds = jobs.map { it.importJobId },
                jobs = jobs.map { if (it.importJobId == "failure") it.copy(status = "COPYING") else it },
                history = history,
                startedAt = 1L
            )
        )
    }

    private fun job(id: String, status: String, completedAt: Long) = ImportJobEntity(
        importJobId = id,
        jobType = "LOCAL_FILE",
        sourceType = "LOCAL_FILE",
        reason = "MANUAL_LOCAL",
        priorityClass = "MANUAL",
        priority = 1_000,
        status = status,
        targetFavourite = false,
        preferredFormat = "ORIGINAL",
        technicalDetailsJson = "$id.mp3",
        createdAt = 1L,
        updatedAt = completedAt,
        completedAt = completedAt
    )

    private fun history(jobId: String, result: String, trackId: String?) =
        ImportHistoryEntryEntity(
            historyId = "history-$jobId",
            importJobId = jobId,
            result = result,
            reason = "MANUAL_LOCAL",
            sourceType = "LOCAL_FILE",
            jobType = "LOCAL_FILE",
            trackId = trackId,
            displayTitle = jobId,
            friendlyMessage = if (result == "FAILED") "Import failed." else "Imported successfully.",
            createdAt = 2L
        )
}
