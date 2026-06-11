package com.fenl.fenlzer.data.repository

import android.net.Uri
import com.fenl.fenlzer.common.FenlzerDispatchers
import com.fenl.fenlzer.data.local.dao.PlaybackDao
import com.fenl.fenlzer.data.local.dao.TrackDao
import com.fenl.fenlzer.data.local.entity.TrackEntity
import com.fenl.fenlzer.data.local.entity.TrackStatsSnapshotEntity
import com.fenl.fenlzer.data.storage.FenlzerStorage
import com.fenl.fenlzer.domain.text.AudioTitleFormatter
import com.fenl.fenlzer.domain.delete.DeleteFromFenlzerResult
import com.fenl.fenlzer.domain.delete.DeleteFromFenlzerUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class TrackRepository(
    private val trackDao: TrackDao,
    private val playbackDao: PlaybackDao? = null,
    private val storage: FenlzerStorage,
    private val deleteFromFenlzerUseCase: DeleteFromFenlzerUseCase? = null,
    private val dispatchers: FenlzerDispatchers = FenlzerDispatchers(),
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    fun observeLibraryTracks(): Flow<List<LibraryTrack>> {
        val tracksFlow = trackDao.observeTracksByRecentlyAdded()
        val statsFlow = playbackDao?.observeTrackStatsSnapshots()
        return if (statsFlow == null) {
            tracksFlow.map { tracks -> tracks.toLibraryTracks(emptyMap()) }
        } else {
            combine(tracksFlow, statsFlow) { tracks, stats ->
                tracks.toLibraryTracks(stats.associateBy { it.trackId })
            }
        }
    }

    private suspend fun List<TrackEntity>.toLibraryTracks(
        statsByTrackId: Map<String, TrackStatsSnapshotEntity>
    ): List<LibraryTrack> {
        val thumbnailIds = mapNotNull { it.thumbnailAssetId ?: it.embeddedThumbnailAssetId }
            .distinct()
        val thumbnailAssets = if (thumbnailIds.isEmpty()) {
            emptyMap()
        } else {
            trackDao.getThumbnailAssets(thumbnailIds)
                .associateBy { it.thumbnailAssetId }
        }

        return map { track ->
            val assetId = track.thumbnailAssetId ?: track.embeddedThumbnailAssetId
            val asset = assetId?.let(thumbnailAssets::get)
            val localThumbnailUri = asset
                ?.internalFilename
                ?.let { filename -> Uri.fromFile(File(storage.thumbnailsDir, filename)) }
            track.toLibraryTrack(
                stats = statsByTrackId[track.trackId],
                thumbnailUri = localThumbnailUri
                    ?: track.remoteThumbnailUrl?.let(Uri::parse)
            )
        }
    }

    suspend fun toggleFavourite(track: LibraryTrack) = withContext(dispatchers.io) {
        setFavourite(
            trackId = track.trackId,
            isFavourite = !track.isFavourite
        )
    }

    suspend fun setFavourite(trackId: String, isFavourite: Boolean) = withContext(dispatchers.io) {
        trackDao.setFavourite(
            trackId = trackId,
            isFavourite = isFavourite,
            favouritedAt = if (isFavourite) now() else null,
            updatedAt = now()
        )
    }

    suspend fun deleteTrack(track: LibraryTrack): DeleteFromFenlzerResult =
        deleteTracks(listOf(track.trackId))

    suspend fun deleteTracks(trackIds: Collection<String>): DeleteFromFenlzerResult =
        deleteFromFenlzerUseCase?.deleteTracks(trackIds)
            ?: withContext(dispatchers.io) {
                val tracks = trackDao.getTracksByIds(trackIds.distinct())
                trackDao.deleteTracks(tracks.map { it.trackId })
                val deletedAudioFiles = tracks.count { track ->
                    val file = File(storage.audioDir, track.internalFilename)
                    !file.exists() || file.delete()
                }
                DeleteFromFenlzerResult(
                    deletedTracks = tracks.size,
                    deletedAudioFiles = deletedAudioFiles
                )
            }

    suspend fun deleteAllSongs(): DeleteFromFenlzerResult =
        deleteFromFenlzerUseCase?.deleteAllSongs()
            ?: withContext(dispatchers.io) {
                val tracks = trackDao.getAllTracks()
                trackDao.deleteAllTracks()
                val deletedAudioFiles = tracks.count { track ->
                    val file = File(storage.audioDir, track.internalFilename)
                    !file.exists() || file.delete()
                }
                DeleteFromFenlzerResult(
                    deletedTracks = tracks.size,
                    deletedAudioFiles = deletedAudioFiles
                )
            }

    private fun TrackEntity.toLibraryTrack(
        stats: TrackStatsSnapshotEntity?,
        thumbnailUri: Uri?
    ): LibraryTrack {
        return LibraryTrack(
            trackId = trackId,
            title = title,
            displayTitle = AudioTitleFormatter.displayTitle(
                title = title,
                fallbackFilename = originalFilename
            ),
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            genre = genre,
            durationMs = durationMs,
            sourceType = sourceType,
            originalFilename = originalFilename,
            internalFilename = internalFilename,
            audioHash = audioHash,
            isFavourite = isFavourite,
            favouritedAt = favouritedAt,
            importedAt = importedAt,
            updatedAt = updatedAt,
            playCount = stats?.playCount ?: 0,
            totalListenedMs = stats?.totalListenedMs ?: 0L,
            lastPlayedAt = stats?.lastPlayedAt,
            thumbnailUri = thumbnailUri,
            hasThumbnail = thumbnailUri != null || remoteThumbnailUrl != null
        )
    }
}

data class LibraryTrack(
    val trackId: String,
    val title: String,
    val displayTitle: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val durationMs: Long,
    val sourceType: String,
    val originalFilename: String?,
    val internalFilename: String,
    val audioHash: String,
    val isFavourite: Boolean,
    val favouritedAt: Long?,
    val importedAt: Long,
    val updatedAt: Long,
    val playCount: Int = 0,
    val totalListenedMs: Long = 0L,
    val lastPlayedAt: Long? = null,
    val thumbnailUri: Uri?,
    val hasThumbnail: Boolean
)
