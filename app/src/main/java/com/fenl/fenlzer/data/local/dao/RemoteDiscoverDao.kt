package com.fenl.fenlzer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.fenl.fenlzer.data.local.entity.DiscoverRefreshDiagnosticsEntity
import com.fenl.fenlzer.data.local.entity.DiscoverSnapshotEntity
import com.fenl.fenlzer.data.local.entity.DiscoverSnapshotItemEntity
import com.fenl.fenlzer.data.local.entity.RemoteItemEntity
import kotlinx.coroutines.flow.Flow

data class DiscoverRemoteItemRow(
    val snapshotId: String,
    val position: Int,
    val recommendationReason: String?,
    val remoteItemId: String,
    val youtubeVideoId: String?,
    val sourceUrl: String?,
    val title: String,
    val artistOrChannel: String?,
    val durationMs: Long?,
    val thumbnailUrl: String?,
    val canStream: Boolean,
    val canDownload: Boolean,
    val streamState: String,
    val lastPlayableUrl: String?,
    val playableUrlExpiresAt: Long?,
    val importState: String,
    val importedTrackId: String?
)

@Dao
interface RemoteDiscoverDao {
    @Upsert
    suspend fun upsertRemoteItems(remoteItems: List<RemoteItemEntity>)

    @Upsert
    suspend fun upsertRemoteItem(remoteItem: RemoteItemEntity)

    @Query("SELECT * FROM remote_items WHERE remoteItemId = :remoteItemId")
    suspend fun getRemoteItem(remoteItemId: String): RemoteItemEntity?

    @Query("SELECT * FROM remote_items WHERE remoteItemId IN (:remoteItemIds)")
    suspend fun getRemoteItems(remoteItemIds: List<String>): List<RemoteItemEntity>

    @Query(
        """
        SELECT remote_items.* FROM remote_items
        WHERE youtubeVideoId = :youtubeVideoId
        LIMIT 1
        """
    )
    suspend fun getRemoteItemByYoutubeVideoId(youtubeVideoId: String): RemoteItemEntity?

    @Query(
        """
        SELECT remote_items.* FROM remote_items
        WHERE sourceUrl = :sourceUrl
        LIMIT 1
        """
    )
    suspend fun getRemoteItemBySourceUrl(sourceUrl: String): RemoteItemEntity?

    @Query("SELECT * FROM discover_snapshots ORDER BY generatedAt DESC LIMIT 1")
    fun observeLatestDiscoverSnapshot(): Flow<DiscoverSnapshotEntity?>

    @Query("SELECT * FROM discover_snapshots ORDER BY generatedAt DESC LIMIT 1")
    suspend fun getLatestDiscoverSnapshot(): DiscoverSnapshotEntity?

    @Query(
        """
        SELECT
            discover_snapshot_items.snapshotId AS snapshotId,
            discover_snapshot_items.position AS position,
            discover_snapshot_items.recommendationReason AS recommendationReason,
            remote_items.remoteItemId AS remoteItemId,
            remote_items.youtubeVideoId AS youtubeVideoId,
            remote_items.sourceUrl AS sourceUrl,
            remote_items.title AS title,
            remote_items.artistOrChannel AS artistOrChannel,
            remote_items.durationMs AS durationMs,
            remote_items.thumbnailUrl AS thumbnailUrl,
            remote_items.canStream AS canStream,
            remote_items.canDownload AS canDownload,
            remote_items.streamState AS streamState,
            remote_items.lastPlayableUrl AS lastPlayableUrl,
            remote_items.playableUrlExpiresAt AS playableUrlExpiresAt,
            remote_items.importState AS importState,
            remote_items.importedTrackId AS importedTrackId
        FROM discover_snapshot_items
        INNER JOIN remote_items ON remote_items.remoteItemId = discover_snapshot_items.remoteItemId
        WHERE discover_snapshot_items.snapshotId = (
            SELECT snapshotId FROM discover_snapshots ORDER BY generatedAt DESC LIMIT 1
        )
        ORDER BY discover_snapshot_items.position ASC
        """
    )
    fun observeLatestDiscoverItems(): Flow<List<DiscoverRemoteItemRow>>

    @Query(
        """
        SELECT
            discover_snapshot_items.snapshotId AS snapshotId,
            discover_snapshot_items.position AS position,
            discover_snapshot_items.recommendationReason AS recommendationReason,
            remote_items.remoteItemId AS remoteItemId,
            remote_items.youtubeVideoId AS youtubeVideoId,
            remote_items.sourceUrl AS sourceUrl,
            remote_items.title AS title,
            remote_items.artistOrChannel AS artistOrChannel,
            remote_items.durationMs AS durationMs,
            remote_items.thumbnailUrl AS thumbnailUrl,
            remote_items.canStream AS canStream,
            remote_items.canDownload AS canDownload,
            remote_items.streamState AS streamState,
            remote_items.lastPlayableUrl AS lastPlayableUrl,
            remote_items.playableUrlExpiresAt AS playableUrlExpiresAt,
            remote_items.importState AS importState,
            remote_items.importedTrackId AS importedTrackId
        FROM discover_snapshot_items
        INNER JOIN remote_items ON remote_items.remoteItemId = discover_snapshot_items.remoteItemId
        WHERE discover_snapshot_items.snapshotId = (
            SELECT snapshotId FROM discover_snapshots ORDER BY generatedAt DESC LIMIT 1
        )
        ORDER BY discover_snapshot_items.position ASC
        """
    )
    suspend fun getLatestDiscoverItems(): List<DiscoverRemoteItemRow>

    @Query(
        """
        SELECT * FROM discover_refresh_diagnostics
        WHERE snapshotId = :snapshotId
        """
    )
    fun observeDiagnostics(snapshotId: String): Flow<DiscoverRefreshDiagnosticsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: DiscoverSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshotItems(items: List<DiscoverSnapshotItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiagnostics(diagnostics: DiscoverRefreshDiagnosticsEntity)

    @Query(
        """
        UPDATE discover_snapshots
        SET lastOpenedAt = :openedAt
        WHERE snapshotId = :snapshotId
        """
    )
    suspend fun markSnapshotOpened(snapshotId: String, openedAt: Long)

    @Query(
        """
        UPDATE remote_items
        SET streamState = :streamState,
            lastPlayableUrl = :playableUrl,
            playableUrlExpiresAt = :expiresAt,
            lastResolvedAt = :resolvedAt,
            updatedAt = :updatedAt
        WHERE remoteItemId = :remoteItemId
        """
    )
    suspend fun updateStreamResolution(
        remoteItemId: String,
        streamState: String,
        playableUrl: String?,
        expiresAt: Long?,
        resolvedAt: Long?,
        updatedAt: Long
    )

    @Query(
        """
        UPDATE remote_items
        SET importState = :importState,
            importedTrackId = :trackId,
            updatedAt = :updatedAt
        WHERE remoteItemId = :remoteItemId
        """
    )
    suspend fun markImported(remoteItemId: String, importState: String, trackId: String, updatedAt: Long)

    @Transaction
    suspend fun replaceSnapshot(
        snapshot: DiscoverSnapshotEntity,
        remoteItems: List<RemoteItemEntity>,
        snapshotItems: List<DiscoverSnapshotItemEntity>,
        diagnostics: DiscoverRefreshDiagnosticsEntity
    ) {
        upsertRemoteItems(remoteItems)
        insertSnapshot(snapshot)
        insertSnapshotItems(snapshotItems)
        insertDiagnostics(diagnostics)
    }
}
