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
    version = 3,
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE queue_states ADD COLUMN playbackPositionMs INTEGER NOT NULL DEFAULT 0"
                )
                connection.execSQL(
                    "ALTER TABLE queue_states ADD COLUMN wasPlaying INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
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
    }
}
