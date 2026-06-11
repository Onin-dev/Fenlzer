package com.fenl.fenlzer.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.fenl.fenlzer.common.FenlzerDispatchers
import com.fenl.fenlzer.data.local.dao.PlaybackDao
import com.fenl.fenlzer.data.local.dao.PlaylistDao
import com.fenl.fenlzer.data.local.dao.TrackDao
import com.fenl.fenlzer.data.local.entity.BulkMetadataOperationEntity
import com.fenl.fenlzer.data.local.entity.ThumbnailAssetEntity
import com.fenl.fenlzer.data.local.entity.TrackEntity
import com.fenl.fenlzer.data.local.entity.TrackOriginalMetadataEntity
import com.fenl.fenlzer.data.local.entity.TrackStatsSnapshotEntity
import com.fenl.fenlzer.data.storage.FenlzerStorage
import com.fenl.fenlzer.domain.text.AudioTitleFormatter
import com.fenl.fenlzer.domain.text.SearchNormalizer
import com.fenl.fenlzer.importing.local.Sha256
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MetadataRepository(
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val playbackDao: PlaybackDao,
    private val storage: FenlzerStorage,
    private val dispatchers: FenlzerDispatchers = FenlzerDispatchers(),
    private val now: () -> Long = { System.currentTimeMillis() },
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) {
    fun observeSongDetails(trackId: String): Flow<SongDetails?> {
        return combine(
            trackDao.observeTrack(trackId),
            trackDao.observeOriginalMetadata(trackId),
            playbackDao.observeTrackStats(trackId),
            playlistDao.observePlaylists(),
            playlistDao.observePlaylistTracks()
        ) { track, original, stats, playlists, playlistTracks ->
            if (track == null) return@combine null
            val playlistNames = playlistTracks
                .filter { it.trackId == track.trackId }
                .mapNotNull { playlistTrack ->
                    playlists.firstOrNull { it.playlistId == playlistTrack.playlistId }?.name
                }
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
            val thumbnailUri = thumbnailUriForTrack(track)
            SongDetails(
                trackId = track.trackId,
                title = track.displayTitle(),
                metadata = track.toMetadataDraft(),
                originalMetadata = original?.toOriginalMetadata(),
                thumbnailUri = thumbnailUri,
                isFavourite = track.isFavourite,
                favouritedAt = track.favouritedAt,
                importedAt = track.importedAt,
                stats = stats?.toSongStats() ?: SongStats(),
                playlistNames = playlistNames,
                source = SongSourceInfo(
                    sourceType = track.sourceType,
                    originalFilename = track.originalFilename,
                    youtubeVideoId = track.youtubeVideoId,
                    sourceUrl = track.sourceUrl
                ),
                technical = SongTechnicalInfo(
                    internalFilename = track.internalFilename,
                    audioHash = track.audioHash,
                    fileSizeBytes = track.fileSizeBytes,
                    finalAudioFormat = track.finalAudioFormat,
                    durationMs = track.durationMs
                )
            )
        }
    }

    fun observeArtists(): Flow<List<ArtistSummary>> {
        return combine(
            trackDao.observeTracksByRecentlyAdded(),
            playbackDao.observeTrackStatsSnapshots()
        ) { tracks, stats ->
            val statsByTrackId = stats.associateBy { it.trackId }
            tracks.groupBy { it.artist }
                .map { (artist, artistTracks) ->
                    ArtistSummary(
                        name = artist,
                        songCount = artistTracks.size,
                        albumCount = artistTracks.map { it.albumIdentityKey() }.distinct().size,
                        totalDurationMs = artistTracks.sumOf { it.durationMs },
                        totalListenedMs = artistTracks.sumOf {
                            statsByTrackId[it.trackId]?.totalListenedMs ?: 0L
                        }
                    )
                }
                .sortedWith(compareBy { SearchNormalizer.sortKey(it.name.ifBlank { UNKNOWN_ARTIST }) })
        }
    }

    fun observeArtistDetail(artistName: String): Flow<ArtistDetail?> {
        return combine(
            trackDao.observeTracksByRecentlyAdded(),
            playbackDao.observeTrackStatsSnapshots()
        ) { tracks, stats ->
            val artistTracks = tracks
                .filter { it.artist == artistName }
                .sortedWith(
                    compareBy<TrackEntity> { SearchNormalizer.sortKey(it.album) }
                        .thenBy { it.discNumber ?: Int.MAX_VALUE }
                        .thenBy { it.trackNumber ?: Int.MAX_VALUE }
                        .thenBy { SearchNormalizer.sortKey(it.displayTitle()) }
                )
            if (artistTracks.isEmpty()) return@combine null
            val statsByTrackId = stats.associateBy { it.trackId }
            val libraryTracks = artistTracks.toLibraryTracks()
            val albums = artistTracks
                .groupBy { it.albumIdentityKey() }
                .map { (_, albumTracks) ->
                    albumTracks.toAlbumSummary(statsByTrackId, thumbnailUrisByTrackId(albumTracks))
                }
                .sortedWith(albumSummaryComparator())
            ArtistDetail(
                name = artistName,
                songCount = artistTracks.size,
                totalDurationMs = artistTracks.sumOf { it.durationMs },
                totalListenedMs = artistTracks.sumOf {
                    statsByTrackId[it.trackId]?.totalListenedMs ?: 0L
                },
                mostPlayedSongTitle = artistTracks
                    .maxByOrNull { statsByTrackId[it.trackId]?.totalListenedMs ?: 0L }
                    ?.takeIf { (statsByTrackId[it.trackId]?.totalListenedMs ?: 0L) > 0L }
                    ?.displayTitle(),
                albums = albums,
                tracks = libraryTracks
            )
        }
    }

    fun observeAlbums(): Flow<List<AlbumSummary>> {
        return combine(
            trackDao.observeTracksByRecentlyAdded(),
            playbackDao.observeTrackStatsSnapshots()
        ) { tracks, stats ->
            val statsByTrackId = stats.associateBy { it.trackId }
            tracks.groupBy { it.albumIdentityKey() }
                .map { (_, albumTracks) ->
                    albumTracks.toAlbumSummary(statsByTrackId, thumbnailUrisByTrackId(albumTracks))
                }
                .sortedWith(albumSummaryComparator())
        }
    }

    fun observeAlbumDetail(albumKey: String): Flow<AlbumDetail?> {
        return combine(
            trackDao.observeTracksByRecentlyAdded(),
            playbackDao.observeTrackStatsSnapshots()
        ) { tracks, stats ->
            val albumTracks = tracks
                .filter { it.albumIdentityKey() == albumKey }
                .sortedWith(albumTrackComparator())
            if (albumTracks.isEmpty()) return@combine null
            val statsByTrackId = stats.associateBy { it.trackId }
            val thumbnailsByTrackId = thumbnailUrisByTrackId(albumTracks)
            AlbumDetail(
                summary = albumTracks.toAlbumSummary(statsByTrackId, thumbnailsByTrackId),
                tracks = albumTracks.toLibraryTracks(thumbnailsByTrackId),
                totalListenedMs = albumTracks.sumOf {
                    statsByTrackId[it.trackId]?.totalListenedMs ?: 0L
                },
                averageCompletionPercent = albumTracks
                    .mapNotNull { statsByTrackId[it.trackId]?.averageCompletionPercent }
                    .takeIf { it.isNotEmpty() }
                    ?.average()
                    ?.toFloat()
                    ?: 0f,
                lastPlayedAt = albumTracks
                    .mapNotNull { statsByTrackId[it.trackId]?.lastPlayedAt }
                    .maxOrNull(),
                mostPlayedSongTitle = albumTracks
                    .maxByOrNull { statsByTrackId[it.trackId]?.totalListenedMs ?: 0L }
                    ?.takeIf { (statsByTrackId[it.trackId]?.totalListenedMs ?: 0L) > 0L }
                    ?.displayTitle()
            )
        }
    }

    suspend fun updateTrackMetadata(
        trackId: String,
        metadata: TrackMetadataDraft
    ) = withContext(dispatchers.io) {
        val track = trackDao.getTrack(trackId) ?: return@withContext
        trackDao.updateTrack(track.withMetadata(metadata, updatedAt = now()))
    }

    suspend fun resetTrackMetadata(
        trackId: String,
        resetCustomThumbnail: Boolean
    ) = withContext(dispatchers.io) {
        val track = trackDao.getTrack(trackId) ?: return@withContext
        val original = trackDao.getOriginalMetadata(trackId) ?: return@withContext
        val resetTrack = track.withMetadata(original.toMetadataDraft(), updatedAt = now())
            .copy(thumbnailAssetId = if (resetCustomThumbnail) null else track.thumbnailAssetId)
        trackDao.updateTrack(resetTrack)
    }

    suspend fun setTrackCustomThumbnail(
        trackId: String,
        sourceUri: Uri,
        contentResolver: ContentResolver
    ) = withContext(dispatchers.io) {
        val track = trackDao.getTrack(trackId) ?: return@withContext
        val asset = persistCustomThumbnail(sourceUri, contentResolver, kind = "TRACK_CUSTOM")
        trackDao.updateTrack(
            track.copy(
                thumbnailAssetId = asset.thumbnailAssetId,
                updatedAt = now()
            )
        )
    }

    suspend fun clearTrackCustomThumbnail(trackId: String) = withContext(dispatchers.io) {
        val track = trackDao.getTrack(trackId) ?: return@withContext
        trackDao.updateTrack(track.copy(thumbnailAssetId = null, updatedAt = now()))
    }

    suspend fun renameArtist(
        oldArtist: String,
        newArtist: String
    ): Int = withContext(dispatchers.io) {
        val tracks = trackDao.getTracksByRecentlyAdded().filter { it.artist == oldArtist }
        if (tracks.isEmpty()) return@withContext 0
        val updated = tracks.map { track ->
            track.copy(
                artist = newArtist,
                artistSortKey = SearchNormalizer.sortKey(newArtist),
                updatedAt = now()
            )
        }
        trackDao.updateTracks(updated)
        insertBulkOperation(
            operationType = "ARTIST_RENAME",
            oldValues = "artist=${oldArtist.auditValue()}",
            newValues = "artist=${newArtist.auditValue()}",
            affectedTrackIds = updated.map { it.trackId }
        )
        updated.size
    }

    suspend fun editAlbum(
        albumKey: String,
        draft: AlbumBulkEditDraft
    ): Int = withContext(dispatchers.io) {
        val allTracks = trackDao.getTracksByRecentlyAdded()
        val tracks = allTracks.filter { it.albumIdentityKey() == albumKey }
        if (tracks.isEmpty()) return@withContext 0
        val updated = tracks.map { track ->
            val year = if (draft.overwriteFilledFields || track.year.isNullOrBlank()) {
                draft.year
            } else {
                track.year
            }
            val genre = if (draft.overwriteFilledFields || track.genre.isBlank()) {
                draft.genre
            } else {
                track.genre
            }
            track.copy(
                album = draft.album,
                albumSortKey = SearchNormalizer.sortKey(draft.album),
                albumArtist = draft.albumArtist,
                albumArtistSortKey = SearchNormalizer.sortKey(draft.albumArtist),
                year = year,
                genre = genre,
                updatedAt = now()
            )
        }
        trackDao.updateTracks(updated)
        insertBulkOperation(
            operationType = "ALBUM_EDIT",
            oldValues = "albumKey=${albumKey.auditValue()}",
            newValues = "album=${draft.album.auditValue()}; albumArtist=${draft.albumArtist.auditValue()}; " +
                "year=${draft.year.orEmpty().auditValue()}; genre=${draft.genre.auditValue()}; " +
                "overwrite=${draft.overwriteFilledFields}",
            affectedTrackIds = updated.map { it.trackId }
        )
        updated.size
    }

    suspend fun setAlbumCustomThumbnail(
        albumKey: String,
        sourceUri: Uri,
        contentResolver: ContentResolver,
        overwriteExistingCustom: Boolean
    ): Int = withContext(dispatchers.io) {
        val tracks = trackDao.getTracksByRecentlyAdded()
            .filter { it.albumIdentityKey() == albumKey }
        if (tracks.isEmpty()) return@withContext 0
        val asset = persistCustomThumbnail(sourceUri, contentResolver, kind = "ALBUM_TRACK_CUSTOM")
        val updated = tracks
            .filter { overwriteExistingCustom || it.thumbnailAssetId == null }
            .map { track ->
                track.copy(
                    thumbnailAssetId = asset.thumbnailAssetId,
                    updatedAt = now()
                )
            }
        if (updated.isEmpty()) return@withContext 0
        trackDao.updateTracks(updated)
        insertBulkOperation(
            operationType = "ALBUM_THUMBNAIL_EDIT",
            oldValues = "albumKey=${albumKey.auditValue()}",
            newValues = "thumbnailAssetId=${asset.thumbnailAssetId.auditValue()}; overwrite=$overwriteExistingCustom",
            affectedTrackIds = updated.map { it.trackId }
        )
        updated.size
    }

    private suspend fun insertBulkOperation(
        operationType: String,
        oldValues: String,
        newValues: String,
        affectedTrackIds: List<String>
    ) {
        trackDao.insertBulkMetadataOperation(
            BulkMetadataOperationEntity(
                operationId = idFactory(),
                operationType = operationType,
                oldValuesJson = oldValues,
                newValuesJson = newValues,
                affectedTrackIdsJson = affectedTrackIds.joinToString(
                    separator = ",",
                    prefix = "[",
                    postfix = "]"
                ) { it.auditValue() },
                createdAt = now()
            )
        )
    }

    private suspend fun persistCustomThumbnail(
        sourceUri: Uri,
        contentResolver: ContentResolver,
        kind: String
    ): ThumbnailAssetEntity {
        val bytes = contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
            ?: throw IOException("Unable to read selected thumbnail")
        val contentHash = Sha256.hashBytes(bytes)
        val extension = guessImageExtension(bytes, contentResolver.getType(sourceUri))
        storage.ensureDirectories()
        val thumbnailFile = storage.thumbnailFile(contentHash, extension)
        if (!thumbnailFile.exists()) {
            thumbnailFile.writeBytes(bytes)
        }
        val createdAt = now()
        return ThumbnailAssetEntity(
            thumbnailAssetId = idFactory(),
            kind = kind,
            internalFilename = thumbnailFile.name,
            sourceUrl = sourceUri.toString(),
            contentHash = contentHash,
            createdAt = createdAt,
            lastAccessedAt = createdAt,
            isPermanent = true
        ).also { trackDao.upsertThumbnailAsset(it) }
    }

    private suspend fun thumbnailUriForTrack(track: TrackEntity): Uri? {
        val assetId = track.thumbnailAssetId ?: track.embeddedThumbnailAssetId
            ?: return track.remoteThumbnailUrl?.let(Uri::parse)
        return trackDao.getThumbnailAssets(listOf(assetId)).firstOrNull()
            ?.internalFilename
            ?.let { filename -> Uri.fromFile(File(storage.thumbnailsDir, filename)) }
    }

    private suspend fun thumbnailUrisByTrackId(tracks: List<TrackEntity>): Map<String, Uri?> {
        val assetIds = tracks
            .mapNotNull { it.thumbnailAssetId ?: it.embeddedThumbnailAssetId }
            .distinct()
        val assetsById = if (assetIds.isEmpty()) {
            emptyMap()
        } else {
            trackDao.getThumbnailAssets(assetIds).associateBy { it.thumbnailAssetId }
        }
        return tracks.associate { track ->
            val assetId = track.thumbnailAssetId ?: track.embeddedThumbnailAssetId
            val localUri = assetId
                ?.let(assetsById::get)
                ?.internalFilename
                ?.let { Uri.fromFile(File(storage.thumbnailsDir, it)) }
            track.trackId to (localUri ?: track.remoteThumbnailUrl?.let(Uri::parse))
        }
    }

    private suspend fun List<TrackEntity>.toLibraryTracks(): List<LibraryTrack> {
        return toLibraryTracks(thumbnailUrisByTrackId(this))
    }

    private fun List<TrackEntity>.toLibraryTracks(
        thumbnailsByTrackId: Map<String, Uri?>
    ): List<LibraryTrack> {
        return map { track ->
            LibraryTrack(
                trackId = track.trackId,
                title = track.title,
                displayTitle = track.displayTitle(),
                artist = track.artist,
                album = track.album,
                albumArtist = track.albumArtist,
                genre = track.genre,
                durationMs = track.durationMs,
                sourceType = track.sourceType,
                originalFilename = track.originalFilename,
                internalFilename = track.internalFilename,
                audioHash = track.audioHash,
                isFavourite = track.isFavourite,
                favouritedAt = track.favouritedAt,
                importedAt = track.importedAt,
                updatedAt = track.updatedAt,
                thumbnailUri = thumbnailsByTrackId[track.trackId],
                hasThumbnail = thumbnailsByTrackId[track.trackId] != null || track.remoteThumbnailUrl != null
            )
        }
    }

    private fun List<TrackEntity>.toAlbumSummary(
        statsByTrackId: Map<String, TrackStatsSnapshotEntity>,
        thumbnailsByTrackId: Map<String, Uri?>
    ): AlbumSummary {
        val first = first()
        val year = mapNotNull { it.year?.takeIf(String::isNotBlank) }.firstOrNull()
        return AlbumSummary(
            albumKey = first.albumIdentityKey(),
            title = first.album.ifBlank { UNKNOWN_ALBUM },
            album = first.album,
            albumArtist = first.albumIdentityArtist().ifBlank { UNKNOWN_ARTIST },
            songCount = size,
            totalDurationMs = sumOf { it.durationMs },
            totalListenedMs = sumOf { statsByTrackId[it.trackId]?.totalListenedMs ?: 0L },
            year = year,
            thumbnailUri = sortedWith(albumTrackComparator())
                .firstNotNullOfOrNull { thumbnailsByTrackId[it.trackId] }
        )
    }

    private fun TrackEntity.withMetadata(
        metadata: TrackMetadataDraft,
        updatedAt: Long
    ): TrackEntity {
        return copy(
            title = metadata.title,
            titleSortKey = SearchNormalizer.sortKey(metadata.title),
            artist = metadata.artist,
            artistSortKey = SearchNormalizer.sortKey(metadata.artist),
            album = metadata.album,
            albumSortKey = SearchNormalizer.sortKey(metadata.album),
            albumArtist = metadata.albumArtist,
            albumArtistSortKey = SearchNormalizer.sortKey(metadata.albumArtist),
            genre = metadata.genre,
            year = metadata.year?.takeIf { it.isNotBlank() },
            trackNumber = metadata.trackNumber,
            discNumber = metadata.discNumber,
            notes = metadata.notes,
            updatedAt = updatedAt
        )
    }

    private fun TrackEntity.toMetadataDraft(): TrackMetadataDraft = TrackMetadataDraft(
        title = title,
        artist = artist,
        albumArtist = albumArtist,
        album = album,
        genre = genre,
        year = year,
        trackNumber = trackNumber,
        discNumber = discNumber,
        notes = notes
    )

    private fun TrackOriginalMetadataEntity.toMetadataDraft(): TrackMetadataDraft = TrackMetadataDraft(
        title = originalTitle,
        artist = originalArtist,
        albumArtist = originalAlbumArtist,
        album = originalAlbum,
        genre = originalGenre,
        year = originalYear,
        trackNumber = originalTrackNumber,
        discNumber = originalDiscNumber,
        notes = ""
    )

    private fun TrackOriginalMetadataEntity.toOriginalMetadata(): OriginalMetadata = OriginalMetadata(
        title = originalTitle,
        artist = originalArtist,
        albumArtist = originalAlbumArtist,
        album = originalAlbum,
        genre = originalGenre,
        year = originalYear,
        trackNumber = originalTrackNumber,
        discNumber = originalDiscNumber,
        thumbnailKind = originalThumbnailKind,
        rawMetadataJson = rawMetadataJson
    )

    private fun TrackStatsSnapshotEntity.toSongStats(): SongStats = SongStats(
        playCount = playCount,
        skipCount = skipCount,
        completionCount = completionCount,
        totalListenedMs = totalListenedMs,
        firstPlayedAt = firstPlayedAt,
        lastPlayedAt = lastPlayedAt,
        averageCompletionPercent = averageCompletionPercent
    )

    private fun TrackEntity.albumIdentityArtist(): String =
        albumArtist.ifBlank { artist }

    private fun TrackEntity.albumIdentityKey(): String =
        albumIdentityKey(album, albumIdentityArtist())

    private fun TrackEntity.displayTitle(): String =
        AudioTitleFormatter.displayTitle(title = title, fallbackFilename = originalFilename)

    private fun albumSummaryComparator(): Comparator<AlbumSummary> =
        compareBy<AlbumSummary> { SearchNormalizer.sortKey(it.albumArtist) }
            .thenBy { SearchNormalizer.sortKey(it.title) }

    private fun albumTrackComparator(): Comparator<TrackEntity> =
        compareBy<TrackEntity> { it.discNumber ?: Int.MAX_VALUE }
            .thenBy { it.trackNumber ?: Int.MAX_VALUE }
            .thenBy { SearchNormalizer.sortKey(it.displayTitle()) }

    private fun String.auditValue(): String =
        "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

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
        const val UNKNOWN_ARTIST = "Unknown artist"
        const val UNKNOWN_ALBUM = "Unknown album"

        fun albumIdentityKey(album: String, albumArtist: String): String =
            "${SearchNormalizer.sortKey(album)}\u001F${SearchNormalizer.sortKey(albumArtist)}\u001F$album\u001F$albumArtist"
    }
}

data class TrackMetadataDraft(
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val genre: String,
    val year: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val notes: String
)

data class OriginalMetadata(
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val genre: String,
    val year: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val thumbnailKind: String,
    val rawMetadataJson: String?
)

data class SongDetails(
    val trackId: String,
    val title: String,
    val metadata: TrackMetadataDraft,
    val originalMetadata: OriginalMetadata?,
    val thumbnailUri: Uri?,
    val isFavourite: Boolean,
    val favouritedAt: Long?,
    val importedAt: Long,
    val stats: SongStats,
    val playlistNames: List<String>,
    val source: SongSourceInfo,
    val technical: SongTechnicalInfo
)

data class SongStats(
    val playCount: Int = 0,
    val skipCount: Int = 0,
    val completionCount: Int = 0,
    val totalListenedMs: Long = 0L,
    val firstPlayedAt: Long? = null,
    val lastPlayedAt: Long? = null,
    val averageCompletionPercent: Float = 0f
)

data class SongSourceInfo(
    val sourceType: String,
    val originalFilename: String?,
    val youtubeVideoId: String?,
    val sourceUrl: String?
)

data class SongTechnicalInfo(
    val internalFilename: String,
    val audioHash: String,
    val fileSizeBytes: Long,
    val finalAudioFormat: String,
    val durationMs: Long
)

data class ArtistSummary(
    val name: String,
    val songCount: Int,
    val albumCount: Int,
    val totalDurationMs: Long,
    val totalListenedMs: Long
)

data class ArtistDetail(
    val name: String,
    val songCount: Int,
    val totalDurationMs: Long,
    val totalListenedMs: Long,
    val mostPlayedSongTitle: String?,
    val albums: List<AlbumSummary>,
    val tracks: List<LibraryTrack>
)

data class AlbumSummary(
    val albumKey: String,
    val title: String,
    val album: String,
    val albumArtist: String,
    val songCount: Int,
    val totalDurationMs: Long,
    val totalListenedMs: Long,
    val year: String?,
    val thumbnailUri: Uri?
)

data class AlbumDetail(
    val summary: AlbumSummary,
    val tracks: List<LibraryTrack>,
    val totalListenedMs: Long,
    val averageCompletionPercent: Float,
    val lastPlayedAt: Long?,
    val mostPlayedSongTitle: String?
)

data class AlbumBulkEditDraft(
    val album: String,
    val albumArtist: String,
    val year: String?,
    val genre: String,
    val overwriteFilledFields: Boolean
)
