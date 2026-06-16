package com.fenl.fenlzer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.fenl.fenlzer.data.local.entity.PlaylistEntity
import com.fenl.fenlzer.data.local.entity.PlaylistTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY modifiedAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY modifiedAt DESC")
    suspend fun getPlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_tracks ORDER BY playlistId ASC, position ASC")
    fun observePlaylistTracks(): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks ORDER BY playlistId ASC, position ASC")
    suspend fun getAllPlaylistTracks(): List<PlaylistTrackEntity>

    @Query("SELECT * FROM playlists WHERE playlistId = :playlistId")
    suspend fun getPlaylist(playlistId: String): PlaylistEntity?

    @Query("SELECT COUNT(*) FROM playlists WHERE playlistType = 'REGULAR'")
    suspend fun countRegularPlaylists(): Int

    @Upsert
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlaylistTrack(playlistTrack: PlaylistTrackEntity)

    @Query(
        """
        SELECT * FROM playlist_tracks
        WHERE playlistId = :playlistId
        ORDER BY position ASC
        """
    )
    suspend fun getPlaylistTracks(playlistId: String): List<PlaylistTrackEntity>

    @Query("SELECT MAX(position) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun maxPlaylistTrackPosition(playlistId: String): Int?

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun countPlaylistTrack(playlistId: String, trackId: String): Int

    @Transaction
    suspend fun addTrackIfMissing(
        playlistId: String,
        trackId: String,
        addedAt: Long
    ): Boolean {
        if (countPlaylistTrack(playlistId, trackId) > 0) return false
        val position = (maxPlaylistTrackPosition(playlistId) ?: -1) + 1
        insertPlaylistTrack(
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = trackId,
                position = position,
                addedAt = addedAt
            )
        )
        touchPlaylist(playlistId, addedAt)
        return true
    }

    @Query("UPDATE playlists SET name = :name, modifiedAt = :modifiedAt WHERE playlistId = :playlistId")
    suspend fun renamePlaylist(playlistId: String, name: String, modifiedAt: Long)

    @Query(
        """
        UPDATE playlists
        SET customThumbnailAssetId = :thumbnailAssetId,
            modifiedAt = :modifiedAt
        WHERE playlistId = :playlistId
        """
    )
    suspend fun setPlaylistCustomThumbnail(
        playlistId: String,
        thumbnailAssetId: String?,
        modifiedAt: Long
    )

    @Query("UPDATE playlists SET modifiedAt = :modifiedAt WHERE playlistId = :playlistId")
    suspend fun touchPlaylist(playlistId: String, modifiedAt: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: String)

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query(
        """
        UPDATE playlist_tracks
        SET position = :position
        WHERE playlistId = :playlistId AND trackId = :trackId
        """
    )
    suspend fun updateTrackPosition(playlistId: String, trackId: String, position: Int)

    @Transaction
    suspend fun replacePlaylistTracks(
        playlistId: String,
        tracks: List<PlaylistTrackEntity>,
        modifiedAt: Long
    ) {
        clearPlaylistTracks(playlistId)
        tracks.forEach { track ->
            insertPlaylistTrack(track)
        }
        touchPlaylist(playlistId, modifiedAt)
    }

    @Transaction
    suspend fun reorderPlaylistTracks(
        playlistId: String,
        orderedTrackIds: List<String>,
        modifiedAt: Long
    ) {
        orderedTrackIds.forEachIndexed { index, trackId ->
            updateTrackPosition(playlistId, trackId, index)
        }
        touchPlaylist(playlistId, modifiedAt)
    }
}
