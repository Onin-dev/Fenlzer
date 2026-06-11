package com.fenl.fenlzer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.fenl.fenlzer.data.local.entity.PlaybackEventEntity
import com.fenl.fenlzer.data.local.entity.PlaybackProgressRecoveryEntity
import com.fenl.fenlzer.data.local.entity.PlaybackSessionEntity
import com.fenl.fenlzer.data.local.entity.TrackStatsSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: PlaybackSessionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvent(event: PlaybackEventEntity)

    @Upsert
    suspend fun upsertTrackStats(stats: TrackStatsSnapshotEntity)

    @Query("SELECT * FROM track_stats_snapshots WHERE trackId = :trackId")
    suspend fun getTrackStats(trackId: String): TrackStatsSnapshotEntity?

    @Query("SELECT * FROM track_stats_snapshots")
    fun observeTrackStatsSnapshots(): Flow<List<TrackStatsSnapshotEntity>>

    @Query("SELECT * FROM track_stats_snapshots")
    suspend fun getTrackStatsSnapshots(): List<TrackStatsSnapshotEntity>

    @Query("SELECT * FROM track_stats_snapshots WHERE trackId = :trackId")
    fun observeTrackStats(trackId: String): Flow<TrackStatsSnapshotEntity?>

    @Query("SELECT * FROM playback_events ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecentEvents(limit: Int = 100): Flow<List<PlaybackEventEntity>>

    @Query("SELECT * FROM playback_events ORDER BY startedAt DESC")
    fun observePlaybackEvents(): Flow<List<PlaybackEventEntity>>

    @Query("SELECT * FROM playback_events ORDER BY startedAt DESC")
    suspend fun getPlaybackEventsSnapshot(): List<PlaybackEventEntity>

    @Query("SELECT * FROM playback_events WHERE remoteItemId = :remoteItemId")
    suspend fun getPlaybackEventsForRemoteItem(remoteItemId: String): List<PlaybackEventEntity>

    @Query("SELECT * FROM playback_events WHERE privateMode = 0 ORDER BY startedAt ASC")
    suspend fun getNonPrivatePlaybackEvents(): List<PlaybackEventEntity>

    @Query("SELECT * FROM playback_sessions ORDER BY startedAt DESC")
    fun observeSessions(): Flow<List<PlaybackSessionEntity>>

    @Query("SELECT * FROM playback_sessions ORDER BY COALESCE(endedAt, startedAt) DESC LIMIT 1")
    suspend fun getLatestSession(): PlaybackSessionEntity?

    @Query(
        """
        UPDATE playback_sessions
        SET endedAt = :endedAt,
            totalListenedMs = :totalListenedMs,
            eventCount = :eventCount
        WHERE sessionId = :sessionId
        """
    )
    suspend fun updateSession(
        sessionId: String,
        endedAt: Long?,
        totalListenedMs: Long,
        eventCount: Int
    )

    @Query("SELECT COUNT(*) FROM playback_events")
    suspend fun countPlaybackEvents(): Int

    @Query("SELECT COUNT(*) FROM playback_sessions")
    suspend fun countPlaybackSessions(): Int

    @Query("SELECT COUNT(*) FROM track_stats_snapshots")
    suspend fun countTrackStatsSnapshots(): Int

    @Upsert
    suspend fun upsertPlaybackProgressRecovery(progress: PlaybackProgressRecoveryEntity)

    @Query("SELECT * FROM playback_progress_recovery WHERE progressId = :progressId")
    suspend fun getPlaybackProgressRecovery(
        progressId: String = DEFAULT_RECOVERY_PROGRESS_ID
    ): PlaybackProgressRecoveryEntity?

    @Query("DELETE FROM playback_progress_recovery WHERE progressId = :progressId")
    suspend fun clearPlaybackProgressRecovery(
        progressId: String = DEFAULT_RECOVERY_PROGRESS_ID
    )

    @Query("DELETE FROM playback_events")
    suspend fun clearPlaybackEvents()

    @Query(
        """
        UPDATE playback_events
        SET trackId = :trackId,
            remoteItemId = NULL
        WHERE remoteItemId = :remoteItemId
        """
    )
    suspend fun convertRemoteEventsToTrack(remoteItemId: String, trackId: String)

    @Query(
        """
        UPDATE playback_progress_recovery
        SET trackId = :trackId,
            remoteItemId = NULL
        WHERE remoteItemId = :remoteItemId
        """
    )
    suspend fun convertRemoteProgressToTrack(remoteItemId: String, trackId: String)

    @Query("DELETE FROM playback_sessions")
    suspend fun clearPlaybackSessions()

    @Query("DELETE FROM track_stats_snapshots")
    suspend fun clearTrackStatsSnapshots()

    companion object {
        const val DEFAULT_RECOVERY_PROGRESS_ID = "active_playback"
    }
}
