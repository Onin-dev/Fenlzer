package com.fenl.fenlzer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.fenl.fenlzer.data.local.entity.ImportHistoryEntryEntity
import com.fenl.fenlzer.data.local.entity.ImportJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportDao {
    @Query(
        """
        SELECT * FROM import_jobs
        WHERE status IN (
            'QUEUED',
            'DOWNLOADING_METADATA',
            'DOWNLOADING',
            'POST_PROCESSING',
            'PROCESSING',
            'RUNNING',
            'READY_FOR_TRANSFER',
            'COPYING',
            'EXTRACTING_METADATA',
            'NEEDS_ATTENTION'
        )
        OR (
            isVisibleInActiveImports = 1
            AND status IN ('COMPLETED', 'TRANSFER_CONFIRMED', 'DUPLICATE', 'FAILED', 'CANCELLED')
        )
        ORDER BY priority DESC, createdAt ASC
        """
    )
    fun observeActiveJobs(): Flow<List<ImportJobEntity>>

    @Query(
        """
        SELECT * FROM import_jobs
        WHERE status IN (
            'QUEUED',
            'DOWNLOADING_METADATA',
            'DOWNLOADING',
            'POST_PROCESSING',
            'PROCESSING',
            'RUNNING',
            'READY_FOR_TRANSFER',
            'COPYING',
            'EXTRACTING_METADATA',
            'NEEDS_ATTENTION'
        )
        ORDER BY priority DESC, createdAt ASC
        """
    )
    suspend fun getActiveJobs(): List<ImportJobEntity>

    @Query(
        """
        SELECT * FROM import_jobs
        WHERE status IN (
            'QUEUED', 'DOWNLOADING_METADATA', 'DOWNLOADING', 'POST_PROCESSING',
            'PROCESSING', 'RUNNING', 'READY_FOR_TRANSFER', 'COPYING',
            'EXTRACTING_METADATA', 'NEEDS_ATTENTION'
        )
        ORDER BY priority DESC, createdAt ASC
        """
    )
    suspend fun getRunnableJobs(): List<ImportJobEntity>

    @Query(
        """
        SELECT * FROM import_jobs
        WHERE jobType IN ('YOUTUBE_SEARCH', 'YOUTUBE_PLAYLIST_ITEM')
          AND status IN (
              'QUEUED',
              'DOWNLOADING_METADATA',
              'DOWNLOADING',
              'POST_PROCESSING',
              'PROCESSING',
              'RUNNING',
              'READY_FOR_TRANSFER',
              'COPYING',
              'EXTRACTING_METADATA',
              'NEEDS_ATTENTION'
          )
        ORDER BY priority DESC, createdAt ASC
        """
    )
    suspend fun getRecoverableYoutubeSearchJobs(): List<ImportJobEntity>

    @Query("SELECT * FROM import_jobs WHERE importJobId = :importJobId")
    suspend fun getJob(importJobId: String): ImportJobEntity?

    @Query(
        """
        UPDATE import_jobs
        SET attemptCount = attemptCount + 1,
            updatedAt = :updatedAt
        WHERE importJobId = :importJobId
        """
    )
    suspend fun incrementAttempt(importJobId: String, updatedAt: Long)

    @Query(
        """
        UPDATE import_jobs
        SET isVisibleInActiveImports = 0
        WHERE status IN ('COMPLETED', 'TRANSFER_CONFIRMED', 'DUPLICATE', 'CANCELLED')
        """
    )
    suspend fun acknowledgeFinishedJobs()

    @Query(
        """
        UPDATE import_jobs
        SET isVisibleInActiveImports = 0
        WHERE importJobId = :importJobId AND status = 'FAILED'
        """
    )
    suspend fun dismissFailedJob(importJobId: String)

    @Query("SELECT COUNT(*) FROM import_history_entries WHERE importJobId = :importJobId")
    suspend fun countHistoryForJob(importJobId: String): Int

    @Upsert
    suspend fun upsertJob(job: ImportJobEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryEntry(historyEntry: ImportHistoryEntryEntity)

    @Query("SELECT * FROM import_history_entries ORDER BY createdAt DESC")
    fun observeImportHistory(): Flow<List<ImportHistoryEntryEntity>>

    @Query("SELECT * FROM import_history_entries ORDER BY createdAt DESC")
    suspend fun getImportHistory(): List<ImportHistoryEntryEntity>

    @Query("DELETE FROM import_history_entries")
    suspend fun clearImportHistory()
}
