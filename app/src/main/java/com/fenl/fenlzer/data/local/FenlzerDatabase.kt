package com.fenl.fenlzer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.fenl.fenlzer.data.local.dao.ApiDiagnosticDao
import com.fenl.fenlzer.data.local.dao.ImportDao
import com.fenl.fenlzer.data.local.dao.PlaybackDao
import com.fenl.fenlzer.data.local.dao.PlaylistDao
import com.fenl.fenlzer.data.local.dao.QueueDao
import com.fenl.fenlzer.data.local.dao.RemoteDiscoverDao
import com.fenl.fenlzer.data.local.dao.TrackDao
import com.fenl.fenlzer.data.local.entity.ApiDiagnosticEntryEntity
import com.fenl.fenlzer.data.local.entity.BulkMetadataOperationEntity
import com.fenl.fenlzer.data.local.entity.DiscoverRefreshDiagnosticsEntity
import com.fenl.fenlzer.data.local.entity.DiscoverSnapshotEntity
import com.fenl.fenlzer.data.local.entity.DiscoverSnapshotItemEntity
import com.fenl.fenlzer.data.local.entity.ImportHistoryEntryEntity
import com.fenl.fenlzer.data.local.entity.ImportJobEntity
import com.fenl.fenlzer.data.local.entity.PlaybackEventEntity
import com.fenl.fenlzer.data.local.entity.PlaybackProgressRecoveryEntity
import com.fenl.fenlzer.data.local.entity.PlaybackSessionEntity
import com.fenl.fenlzer.data.local.entity.PlaylistEntity
import com.fenl.fenlzer.data.local.entity.PlaylistTrackEntity
import com.fenl.fenlzer.data.local.entity.QueueItemEntity
import com.fenl.fenlzer.data.local.entity.QueueStateEntity
import com.fenl.fenlzer.data.local.entity.RemoteItemEntity
import com.fenl.fenlzer.data.local.entity.ThumbnailAssetEntity
import com.fenl.fenlzer.data.local.entity.TrackEntity
import com.fenl.fenlzer.data.local.entity.TrackOriginalMetadataEntity
import com.fenl.fenlzer.data.local.entity.TrackStatsSnapshotEntity

@Database(
    entities = [
        TrackEntity::class,
        TrackOriginalMetadataEntity::class,
        ThumbnailAssetEntity::class,
        BulkMetadataOperationEntity::class,
        RemoteItemEntity::class,
        DiscoverSnapshotEntity::class,
        DiscoverSnapshotItemEntity::class,
        DiscoverRefreshDiagnosticsEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        QueueStateEntity::class,
        QueueItemEntity::class,
        PlaybackEventEntity::class,
        PlaybackSessionEntity::class,
        TrackStatsSnapshotEntity::class,
        PlaybackProgressRecoveryEntity::class,
        ImportJobEntity::class,
        ImportHistoryEntryEntity::class,
        ApiDiagnosticEntryEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class FenlzerDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun queueDao(): QueueDao
    abstract fun remoteDiscoverDao(): RemoteDiscoverDao
    abstract fun playbackDao(): PlaybackDao
    abstract fun importDao(): ImportDao
    abstract fun apiDiagnosticDao(): ApiDiagnosticDao

    companion object {
        const val DATABASE_NAME = "fenlzer.db"

        fun create(context: Context): FenlzerDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                FenlzerDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(*ALL_MIGRATIONS)
                .build()
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE queue_states ADD COLUMN playbackPositionMs INTEGER NOT NULL DEFAULT 0"
                )
                connection.execSQL(
                    "ALTER TABLE queue_states ADD COLUMN wasPlaying INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS playback_progress_recovery (
                        progressId TEXT NOT NULL,
                        queueItemId TEXT,
                        trackId TEXT,
                        remoteItemId TEXT,
                        startedAt INTEGER NOT NULL,
                        lastUpdatedAt INTEGER NOT NULL,
                        listenedMs INTEGER NOT NULL,
                        durationMsAtPlayback INTEGER NOT NULL,
                        lastPositionMs INTEGER NOT NULL,
                        sourceContext TEXT NOT NULL,
                        PRIMARY KEY(progressId),
                        FOREIGN KEY(trackId) REFERENCES tracks(trackId)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(remoteItemId) REFERENCES remote_items(remoteItemId)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_playback_progress_recovery_trackId ON playback_progress_recovery(trackId)"
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_playback_progress_recovery_remoteItemId ON playback_progress_recovery(remoteItemId)"
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_playback_progress_recovery_lastUpdatedAt ON playback_progress_recovery(lastUpdatedAt)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE tracks ADD COLUMN importReason TEXT")
                connection.execSQL("ALTER TABLE tracks ADD COLUMN requestedDownloadFormat TEXT")

                connection.execSQL("ALTER TABLE import_jobs ADD COLUMN sourceType TEXT")
                connection.execSQL("ALTER TABLE import_jobs ADD COLUMN reason TEXT")
                connection.execSQL("ALTER TABLE import_jobs ADD COLUMN priorityClass TEXT")
                connection.execSQL("ALTER TABLE import_jobs ADD COLUMN pendingActionType TEXT")

                connection.execSQL("ALTER TABLE import_history_entries ADD COLUMN sourceType TEXT")
                connection.execSQL("ALTER TABLE import_history_entries ADD COLUMN jobType TEXT")
                connection.execSQL("ALTER TABLE import_history_entries ADD COLUMN requestedFormat TEXT")
                connection.execSQL("ALTER TABLE import_history_entries ADD COLUMN finalFormat TEXT")
                connection.execSQL("ALTER TABLE import_history_entries ADD COLUMN pendingActionType TEXT")

                connection.execSQL(
                    """
                    UPDATE import_jobs
                    SET sourceType = CASE
                        WHEN targetPlaylistId IS NOT NULL THEN 'DISCOVER_AUTO_PLAYLIST'
                        WHEN targetFavourite = 1 THEN 'DISCOVER_AUTO_FAVOURITE'
                        WHEN jobType = 'LOCAL_FILE' THEN 'LOCAL_FILE'
                        WHEN jobType = 'YOUTUBE_PLAYLIST_ITEM' THEN 'YOUTUBE_PLAYLIST'
                        ELSE 'LEGACY_YOUTUBE'
                    END,
                    reason = CASE
                        WHEN targetPlaylistId IS NOT NULL THEN 'AUTO_PLAYLIST_ADD'
                        WHEN targetFavourite = 1 THEN 'AUTO_FAVOURITE'
                        WHEN jobType = 'LOCAL_FILE' THEN 'MANUAL_LOCAL'
                        WHEN jobType = 'YOUTUBE_PLAYLIST_ITEM' THEN 'YOUTUBE_PLAYLIST'
                        ELSE 'LEGACY_YOUTUBE'
                    END,
                    priorityClass = CASE
                        WHEN targetPlaylistId IS NOT NULL OR targetFavourite = 1 THEN 'AUTO'
                        ELSE 'MANUAL'
                    END,
                    pendingActionType = CASE
                        WHEN targetPlaylistId IS NOT NULL THEN 'ADD_TO_PLAYLIST'
                        WHEN targetFavourite = 1 THEN 'FAVOURITE'
                        ELSE NULL
                    END
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    UPDATE import_history_entries
                    SET sourceType = COALESCE(
                            (SELECT sourceType FROM import_jobs
                             WHERE import_jobs.importJobId = import_history_entries.importJobId),
                            CASE WHEN youtubeVideoId IS NULL THEN 'LOCAL_FILE' ELSE 'LEGACY_YOUTUBE' END
                        ),
                        jobType = (SELECT jobType FROM import_jobs
                                   WHERE import_jobs.importJobId = import_history_entries.importJobId),
                        requestedFormat = (SELECT preferredFormat FROM import_jobs
                                           WHERE import_jobs.importJobId = import_history_entries.importJobId),
                        finalFormat = (SELECT actualFormat FROM import_jobs
                                       WHERE import_jobs.importJobId = import_history_entries.importJobId),
                        pendingActionType = (SELECT pendingActionType FROM import_jobs
                                             WHERE import_jobs.importJobId = import_history_entries.importJobId),
                        reason = COALESCE(
                            (SELECT reason FROM import_jobs
                             WHERE import_jobs.importJobId = import_history_entries.importJobId),
                            reason
                        )
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    UPDATE tracks
                    SET sourceType = COALESCE(
                            (SELECT sourceType FROM import_history_entries
                             WHERE import_history_entries.trackId = tracks.trackId
                             ORDER BY createdAt DESC LIMIT 1),
                            CASE WHEN sourceType = 'YOUTUBE' THEN 'LEGACY_YOUTUBE' ELSE sourceType END
                        ),
                        importReason = (SELECT reason FROM import_history_entries
                                        WHERE import_history_entries.trackId = tracks.trackId
                                        ORDER BY createdAt DESC LIMIT 1),
                        requestedDownloadFormat = (SELECT requestedFormat FROM import_history_entries
                                                   WHERE import_history_entries.trackId = tracks.trackId
                                                   ORDER BY createdAt DESC LIMIT 1)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE import_jobs ADD COLUMN attemptCount INTEGER NOT NULL DEFAULT 0"
                )
                connection.execSQL(
                    "ALTER TABLE import_jobs ADD COLUMN maxAttempts INTEGER NOT NULL DEFAULT 3"
                )
                connection.execSQL(
                    "ALTER TABLE import_jobs ADD COLUMN isVisibleInActiveImports INTEGER NOT NULL DEFAULT 0"
                )
                connection.execSQL(
                    """
                    UPDATE import_jobs
                    SET isVisibleInActiveImports = 1
                    WHERE status IN (
                        'QUEUED', 'DOWNLOADING_METADATA', 'DOWNLOADING', 'POST_PROCESSING',
                        'PROCESSING', 'RUNNING', 'READY_FOR_TRANSFER', 'COPYING',
                        'EXTRACTING_METADATA', 'NEEDS_ATTENTION', 'FAILED'
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE track_stats_snapshots ADD COLUMN completionSampleCount INTEGER NOT NULL DEFAULT 0"
                )
                connection.execSQL(
                    "ALTER TABLE playback_progress_recovery ADD COLUMN privateMode INTEGER NOT NULL DEFAULT 0"
                )
                connection.execSQL(
                    """
                    UPDATE track_stats_snapshots
                    SET completionSampleCount = CASE
                            WHEN (
                                SELECT COUNT(*) FROM playback_events
                                WHERE playback_events.trackId = track_stats_snapshots.trackId
                            ) > 0 THEN (
                                SELECT COUNT(*) FROM playback_events
                                WHERE playback_events.trackId = track_stats_snapshots.trackId
                            )
                            WHEN totalListenedMs > 0 THEN MAX(playCount + skipCount, 1)
                            ELSE 0
                        END,
                        averageCompletionPercent = COALESCE(
                            (
                                SELECT AVG(completionPercent) FROM playback_events
                                WHERE playback_events.trackId = track_stats_snapshots.trackId
                            ),
                            averageCompletionPercent
                        )
                    """.trimIndent()
                )
                connection.execSQL(
                    "UPDATE discover_snapshots SET lastOpenedAt = generatedAt"
                )
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6
        )
    }
}
