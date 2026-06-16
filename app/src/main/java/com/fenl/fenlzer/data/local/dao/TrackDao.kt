package com.fenl.fenlzer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.fenl.fenlzer.data.local.entity.BulkMetadataOperationEntity
import com.fenl.fenlzer.data.local.entity.ThumbnailAssetEntity
import com.fenl.fenlzer.data.local.entity.TrackEntity
import com.fenl.fenlzer.data.local.entity.TrackOriginalMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun countTracks(): Int

    @Query("SELECT * FROM tracks ORDER BY importedAt DESC")
    fun observeTracksByRecentlyAdded(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY importedAt DESC")
    suspend fun getTracksByRecentlyAdded(): List<TrackEntity>

    @Query("SELECT * FROM tracks")
    suspend fun getAllTracks(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE trackId = :trackId")
    suspend fun getTrack(trackId: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE trackId = :trackId")
    fun observeTrack(trackId: String): Flow<TrackEntity?>

    @Query("SELECT * FROM tracks WHERE trackId IN (:trackIds)")
    suspend fun getTracksByIds(trackIds: List<String>): List<TrackEntity>

    @Query("SELECT * FROM track_original_metadata WHERE trackId = :trackId")
    suspend fun getOriginalMetadata(trackId: String): TrackOriginalMetadataEntity?

    @Query("SELECT * FROM track_original_metadata WHERE trackId = :trackId")
    fun observeOriginalMetadata(trackId: String): Flow<TrackOriginalMetadataEntity?>

    @Query("SELECT * FROM tracks WHERE audioHash = :audioHash LIMIT 1")
    suspend fun getTrackByAudioHash(audioHash: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE youtubeVideoId = :youtubeVideoId LIMIT 1")
    suspend fun getTrackByYoutubeVideoId(youtubeVideoId: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE sourceUrl = :sourceUrl LIMIT 1")
    suspend fun getTrackBySourceUrl(sourceUrl: String): TrackEntity?

    @Query("SELECT * FROM thumbnail_assets WHERE thumbnailAssetId IN (:thumbnailAssetIds)")
    suspend fun getThumbnailAssets(thumbnailAssetIds: List<String>): List<ThumbnailAssetEntity>

    @Query("SELECT * FROM thumbnail_assets WHERE thumbnailAssetId = :thumbnailAssetId")
    suspend fun getThumbnailAsset(thumbnailAssetId: String): ThumbnailAssetEntity?

    @Query(
        """
        SELECT * FROM thumbnail_assets
        WHERE contentHash = :contentHash AND isPermanent = 1
        ORDER BY createdAt ASC
        LIMIT 1
        """
    )
    suspend fun getPermanentThumbnailAssetByHash(contentHash: String): ThumbnailAssetEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTrack(track: TrackEntity)

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Update
    suspend fun updateTracks(tracks: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOriginalMetadata(metadata: TrackOriginalMetadataEntity)

    @Transaction
    suspend fun insertTrackWithOriginalMetadata(
        track: TrackEntity,
        originalMetadata: TrackOriginalMetadataEntity
    ) {
        insertTrack(track)
        insertOriginalMetadata(originalMetadata)
    }

    @Transaction
    suspend fun insertTrackWithOriginalMetadataAndThumbnail(
        track: TrackEntity,
        originalMetadata: TrackOriginalMetadataEntity,
        thumbnailAsset: ThumbnailAssetEntity?
    ) {
        thumbnailAsset?.let { upsertThumbnailAsset(it) }
        insertTrack(track)
        insertOriginalMetadata(originalMetadata)
    }

    @Transaction
    suspend fun updateTrackWithThumbnailAsset(
        track: TrackEntity,
        thumbnailAsset: ThumbnailAssetEntity
    ) {
        upsertThumbnailAsset(thumbnailAsset)
        updateTrack(track)
    }

    @Upsert
    suspend fun upsertThumbnailAsset(thumbnailAsset: ThumbnailAssetEntity)

    @Query(
        """
        UPDATE tracks
        SET isFavourite = :isFavourite,
            favouritedAt = :favouritedAt,
            updatedAt = :updatedAt
        WHERE trackId = :trackId
        """
    )
    suspend fun setFavourite(trackId: String, isFavourite: Boolean, favouritedAt: Long?, updatedAt: Long)

    @Query("DELETE FROM tracks WHERE trackId = :trackId")
    suspend fun deleteTrack(trackId: String)

    @Query("DELETE FROM tracks WHERE trackId IN (:trackIds)")
    suspend fun deleteTracks(trackIds: List<String>)

    @Query("DELETE FROM tracks")
    suspend fun deleteAllTracks()

    @Query(
        """
        SELECT COUNT(*) FROM tracks
        WHERE thumbnailAssetId = :thumbnailAssetId
           OR embeddedThumbnailAssetId = :thumbnailAssetId
        """
    )
    suspend fun countTrackThumbnailReferences(thumbnailAssetId: String): Int

    @Query("SELECT COUNT(*) FROM playlists WHERE customThumbnailAssetId = :thumbnailAssetId")
    suspend fun countPlaylistThumbnailReferences(thumbnailAssetId: String): Int

    @Query("DELETE FROM thumbnail_assets WHERE thumbnailAssetId = :thumbnailAssetId")
    suspend fun deleteThumbnailAsset(thumbnailAssetId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBulkMetadataOperation(operation: BulkMetadataOperationEntity)

    @Query("SELECT COUNT(*) FROM bulk_metadata_operations")
    suspend fun countBulkMetadataOperations(): Int

    @Query("SELECT * FROM bulk_metadata_operations ORDER BY createdAt DESC")
    suspend fun getBulkMetadataOperations(): List<BulkMetadataOperationEntity>
}
