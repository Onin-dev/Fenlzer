package com.fenl.fenlzer.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.fenl.fenlzer.common.FenlzerDispatchers
import com.fenl.fenlzer.data.local.dao.PlaylistDao
import com.fenl.fenlzer.data.local.dao.TrackDao
import com.fenl.fenlzer.data.local.entity.PlaylistEntity
import com.fenl.fenlzer.data.local.entity.PlaylistTrackEntity
import com.fenl.fenlzer.data.local.entity.ThumbnailAssetEntity
import com.fenl.fenlzer.data.local.entity.TrackEntity
import com.fenl.fenlzer.data.storage.FenlzerStorage
import com.fenl.fenlzer.domain.text.AudioTitleFormatter
import com.fenl.fenlzer.importing.local.Sha256
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class PlaylistRepository(
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
    private val storage: FenlzerStorage,
    private val dispatchers: FenlzerDispatchers = FenlzerDispatchers(),
    private val now: () -> Long = { System.currentTimeMillis() },
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) {
    fun observeRegularPlaylists(): Flow<List<RegularPlaylistSummary>> {
        return combine(
            playlistDao.observePlaylists(),
            playlistDao.observePlaylistTracks(),
            trackDao.observeTracksByRecentlyAdded()
        ) { playlists, playlistTracks, tracks ->
            val regularPlaylists = playlists.filter { it.playlistType == PLAYLIST_TYPE_REGULAR }
            val tracksById = tracks.associateBy { it.trackId }
            val thumbnailUrisByAssetId = thumbnailUrisByAssetId(
                tracks.thumbnailAssetIds() + regularPlaylists.mapNotNull { it.customThumbnailAssetId }
            )
            val thumbnailsByTrackId = thumbnailUrisByTrackId(tracks, thumbnailUrisByAssetId)
            regularPlaylists.map { playlist ->
                val memberTracks = playlistTracks
                    .filter { it.playlistId == playlist.playlistId }
                    .sortedBy { it.position }
                    .mapNotNull { playlistTrack -> tracksById[playlistTrack.trackId] }
                val customThumbnailUri = playlist.customThumbnailAssetId
                    ?.let(thumbnailUrisByAssetId::get)
                RegularPlaylistSummary(
                    playlistId = playlist.playlistId,
                    name = playlist.name,
                    songCount = memberTracks.size,
                    totalDurationMs = memberTracks.sumOf { it.durationMs },
                    modifiedAt = playlist.modifiedAt,
                    thumbnailUris = customThumbnailUri?.let(::listOf)
                        ?: memberTracks
                            .take(4)
                            .map { track -> thumbnailsByTrackId[track.trackId] },
                    hasCustomThumbnail = playlist.customThumbnailAssetId != null
                )
            }.sortedByDescending { it.modifiedAt }
        }
    }

    fun observePlaylistDetail(playlistId: String): Flow<RegularPlaylistDetail?> {
        return combine(
            playlistDao.observePlaylists(),
            playlistDao.observePlaylistTracks(),
            trackDao.observeTracksByRecentlyAdded()
        ) { playlists, playlistTracks, tracks ->
            val playlist = playlists.firstOrNull {
                it.playlistId == playlistId && it.playlistType == PLAYLIST_TYPE_REGULAR
            } ?: return@combine null
            val tracksById = tracks.associateBy { it.trackId }
            val thumbnailUrisByAssetId = thumbnailUrisByAssetId(
                tracks.thumbnailAssetIds() + listOfNotNull(playlist.customThumbnailAssetId)
            )
            val thumbnailsByTrackId = thumbnailUrisByTrackId(tracks, thumbnailUrisByAssetId)
            val memberTracks = playlistTracks
                .filter { it.playlistId == playlistId }
                .sortedBy { it.position }
                .mapNotNull { playlistTrack -> tracksById[playlistTrack.trackId] }
            val customThumbnailUri = playlist.customThumbnailAssetId
                ?.let(thumbnailUrisByAssetId::get)

            RegularPlaylistDetail(
                playlistId = playlist.playlistId,
                name = playlist.name,
                songCount = memberTracks.size,
                totalDurationMs = memberTracks.sumOf { it.durationMs },
                modifiedAt = playlist.modifiedAt,
                tracks = memberTracks.map { track ->
                    track.toPlaylistTrackItem(thumbnailsByTrackId[track.trackId])
                },
                thumbnailUris = customThumbnailUri?.let(::listOf)
                    ?: memberTracks
                        .take(4)
                        .map { track -> thumbnailsByTrackId[track.trackId] },
                hasCustomThumbnail = playlist.customThumbnailAssetId != null
            )
        }
    }

    fun observeMembershipTargets(trackId: String): Flow<List<PlaylistMembershipTarget>> {
        return combine(
            playlistDao.observePlaylists(),
            playlistDao.observePlaylistTracks(),
            trackDao.observeTracksByRecentlyAdded()
        ) { playlists, playlistTracks, tracks ->
            val track = tracks.firstOrNull { it.trackId == trackId }
            val regularTargets = playlists
                .filter { it.playlistType == PLAYLIST_TYPE_REGULAR }
                .sortedByDescending { it.modifiedAt }
                .map { playlist ->
                    PlaylistMembershipTarget(
                        targetId = playlist.playlistId,
                        name = playlist.name,
                        selected = playlistTracks.any {
                            it.playlistId == playlist.playlistId && it.trackId == trackId
                        }
                    )
                }

            listOf(
                PlaylistMembershipTarget(
                    targetId = FAVOURITES_TARGET_ID,
                    name = "Favourites",
                    selected = track?.isFavourite == true,
                    favouritesTarget = true
                )
            ) + regularTargets
        }
    }

    suspend fun createPlaylist(name: String): String = withContext(dispatchers.io) {
        val playlistId = idFactory()
        val createdAt = now()
        playlistDao.upsertPlaylist(
            PlaylistEntity(
                playlistId = playlistId,
                name = cleanPlaylistName(name),
                playlistType = PLAYLIST_TYPE_REGULAR,
                customThumbnailAssetId = null,
                createdAt = createdAt,
                modifiedAt = createdAt
            )
        )
        playlistId
    }

    suspend fun renamePlaylist(playlistId: String, name: String) = withContext(dispatchers.io) {
        playlistDao.renamePlaylist(
            playlistId = playlistId,
            name = cleanPlaylistName(name),
            modifiedAt = now()
        )
    }

    suspend fun deletePlaylist(playlistId: String) = withContext(dispatchers.io) {
        playlistDao.deletePlaylist(playlistId)
    }

    suspend fun setCustomThumbnail(
        playlistId: String,
        sourceUri: Uri,
        contentResolver: ContentResolver
    ) = withContext(dispatchers.io) {
        val bytes = contentResolver.openInputStream(sourceUri)?.use { input ->
            input.readBytes()
        } ?: throw IOException("Unable to read playlist thumbnail")
        val contentHash = Sha256.hashBytes(bytes)
        val extension = guessImageExtension(bytes, contentResolver.getType(sourceUri))
        storage.ensureDirectories()
        val thumbnailFile = storage.thumbnailFile(contentHash, extension)
        if (!thumbnailFile.exists()) {
            thumbnailFile.writeBytes(bytes)
        }
        val createdAt = now()
        val asset = ThumbnailAssetEntity(
            thumbnailAssetId = idFactory(),
            kind = "PLAYLIST_CUSTOM",
            internalFilename = thumbnailFile.name,
            sourceUrl = sourceUri.toString(),
            contentHash = contentHash,
            createdAt = createdAt,
            lastAccessedAt = createdAt,
            isPermanent = true
        )
        trackDao.upsertThumbnailAsset(asset)
        playlistDao.setPlaylistCustomThumbnail(
            playlistId = playlistId,
            thumbnailAssetId = asset.thumbnailAssetId,
            modifiedAt = now()
        )
    }

    suspend fun clearCustomThumbnail(playlistId: String) = withContext(dispatchers.io) {
        playlistDao.setPlaylistCustomThumbnail(
            playlistId = playlistId,
            thumbnailAssetId = null,
            modifiedAt = now()
        )
    }

    suspend fun addTrackToPlaylist(playlistId: String, trackId: String) = withContext(dispatchers.io) {
        if (playlistDao.countPlaylistTrack(playlistId, trackId) > 0) return@withContext
        val position = (playlistDao.maxPlaylistTrackPosition(playlistId) ?: -1) + 1
        playlistDao.insertPlaylistTrack(
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = trackId,
                position = position,
                addedAt = now()
            )
        )
        playlistDao.touchPlaylist(playlistId, now())
    }

    suspend fun removeTrackFromPlaylist(
        playlistId: String,
        trackId: String
    ) = withContext(dispatchers.io) {
        playlistDao.removeTrackFromPlaylist(playlistId, trackId)
        val orderedTrackIds = playlistDao.getPlaylistTracks(playlistId)
            .sortedBy { it.position }
            .map { it.trackId }
        playlistDao.reorderPlaylistTracks(
            playlistId = playlistId,
            orderedTrackIds = orderedTrackIds,
            modifiedAt = now()
        )
    }

    suspend fun toggleMembership(target: PlaylistMembershipTarget, trackId: String) {
        if (target.favouritesTarget) {
            setFavourite(trackId = trackId, isFavourite = !target.selected)
        } else if (target.selected) {
            removeTrackFromPlaylist(target.targetId, trackId)
        } else {
            addTrackToPlaylist(target.targetId, trackId)
        }
    }

    suspend fun setFavourite(trackId: String, isFavourite: Boolean) = withContext(dispatchers.io) {
        trackDao.setFavourite(
            trackId = trackId,
            isFavourite = isFavourite,
            favouritedAt = if (isFavourite) now() else null,
            updatedAt = now()
        )
    }

    suspend fun reorderPlaylist(
        playlistId: String,
        orderedTrackIds: List<String>
    ) = withContext(dispatchers.io) {
        playlistDao.reorderPlaylistTracks(
            playlistId = playlistId,
            orderedTrackIds = orderedTrackIds.distinct(),
            modifiedAt = now()
        )
    }

    suspend fun saveStaticPlaylist(
        name: String,
        trackIds: List<String>
    ): String = withContext(dispatchers.io) {
        val playlistId = createPlaylist(name)
        val createdAt = now()
        val tracks = trackIds.distinct().mapIndexed { index, trackId ->
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = trackId,
                position = index,
                addedAt = createdAt + index
            )
        }
        playlistDao.replacePlaylistTracks(
            playlistId = playlistId,
            tracks = tracks,
            modifiedAt = now()
        )
        playlistId
    }

    private suspend fun thumbnailUrisByAssetId(thumbnailIds: List<String>): Map<String, Uri> {
        val distinctIds = thumbnailIds.distinct()
        val thumbnailsById = if (distinctIds.isEmpty()) {
            emptyMap()
        } else {
            trackDao.getThumbnailAssets(distinctIds).associateBy { it.thumbnailAssetId }
        }

        return thumbnailsById.mapValues { (_, asset) ->
            Uri.fromFile(File(storage.thumbnailsDir, asset.internalFilename))
        }
    }

    private fun thumbnailUrisByTrackId(
        tracks: List<TrackEntity>,
        thumbnailUrisByAssetId: Map<String, Uri>
    ): Map<String, Uri?> {
        return tracks.associate { track ->
            val thumbnailId = track.thumbnailAssetId ?: track.embeddedThumbnailAssetId
            val localThumbnailUri = thumbnailId?.let(thumbnailUrisByAssetId::get)
            track.trackId to (localThumbnailUri ?: track.remoteThumbnailUrl?.let(Uri::parse))
        }
    }

    private fun List<TrackEntity>.thumbnailAssetIds(): List<String> {
        return mapNotNull { it.thumbnailAssetId ?: it.embeddedThumbnailAssetId }
    }

    private fun TrackEntity.toPlaylistTrackItem(thumbnailUri: Uri?): PlaylistTrackItem {
        return PlaylistTrackItem(
            trackId = trackId,
            displayTitle = AudioTitleFormatter.displayTitle(
                title = title,
                fallbackFilename = originalFilename
            ),
            artist = artist,
            album = album,
            genre = genre,
            year = year,
            durationMs = durationMs,
            isFavourite = isFavourite,
            thumbnailUri = thumbnailUri
        )
    }

    private fun cleanPlaylistName(name: String): String {
        return name.trim().ifBlank { "Untitled Playlist" }
    }

    private fun guessImageExtension(bytes: ByteArray, mimeType: String?): String {
        return when {
            bytes.size >= 8 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte() -> "png"
            bytes.size >= 6 &&
                bytes[0] == 0x47.toByte() &&
                bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() -> "gif"
            bytes.size >= 12 &&
                bytes[0] == 0x52.toByte() &&
                bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() &&
                bytes[8] == 0x57.toByte() &&
                bytes[9] == 0x45.toByte() &&
                bytes[10] == 0x42.toByte() &&
                bytes[11] == 0x50.toByte() -> "webp"
            mimeType == "image/png" -> "png"
            mimeType == "image/gif" -> "gif"
            mimeType == "image/webp" -> "webp"
            else -> "jpg"
        }
    }

    companion object {
        const val PLAYLIST_TYPE_REGULAR = "REGULAR"
        const val FAVOURITES_TARGET_ID = "favourites"
    }
}
