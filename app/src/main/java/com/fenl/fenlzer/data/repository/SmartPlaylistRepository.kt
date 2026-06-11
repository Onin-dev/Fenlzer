package com.fenl.fenlzer.data.repository

import android.net.Uri
import com.fenl.fenlzer.data.local.dao.PlaybackDao
import com.fenl.fenlzer.data.local.dao.TrackDao
import com.fenl.fenlzer.data.local.entity.PlaybackEventEntity
import com.fenl.fenlzer.data.local.entity.TrackEntity
import com.fenl.fenlzer.data.local.entity.TrackStatsSnapshotEntity
import com.fenl.fenlzer.data.storage.FenlzerStorage
import com.fenl.fenlzer.domain.text.AudioTitleFormatter
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SmartPlaylistRepository(
    private val trackDao: TrackDao,
    private val playbackDao: PlaybackDao,
    private val storage: FenlzerStorage,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    fun observeSmartPlaylists(): Flow<List<SmartPlaylistSummary>> {
        return combine(
            trackDao.observeTracksByRecentlyAdded(),
            playbackDao.observeTrackStatsSnapshots(),
            playbackDao.observePlaybackEvents()
        ) { tracks, stats, events ->
            val thumbnailUrisByTrackId = thumbnailUrisByTrackId(tracks)
            val details = SmartPlaylistBuilder.buildDetails(
                tracks = tracks,
                stats = stats,
                events = events,
                thumbnailUrisByTrackId = thumbnailUrisByTrackId,
                zoneId = zoneId
            )
            details.map { detail ->
                SmartPlaylistSummary(
                    smartPlaylistId = detail.smartPlaylistId,
                    name = detail.name,
                    description = detail.description,
                    songCount = detail.songCount,
                    totalDurationMs = detail.totalDurationMs,
                    thumbnailUris = detail.tracks.take(4).map { it.thumbnailUri }
                )
            }
        }
    }

    fun observeSmartPlaylistDetail(smartPlaylistId: String): Flow<SmartPlaylistDetail?> {
        return combine(
            trackDao.observeTracksByRecentlyAdded(),
            playbackDao.observeTrackStatsSnapshots(),
            playbackDao.observePlaybackEvents()
        ) { tracks, stats, events ->
            val thumbnailUrisByTrackId = thumbnailUrisByTrackId(tracks)
            SmartPlaylistBuilder.buildDetails(
                tracks = tracks,
                stats = stats,
                events = events,
                thumbnailUrisByTrackId = thumbnailUrisByTrackId,
                zoneId = zoneId
            ).firstOrNull { it.smartPlaylistId == smartPlaylistId }
        }
    }

    private suspend fun thumbnailUrisByTrackId(tracks: List<TrackEntity>): Map<String, Uri?> {
        val thumbnailIds = tracks
            .mapNotNull { it.thumbnailAssetId ?: it.embeddedThumbnailAssetId }
            .distinct()
        val thumbnailsById = if (thumbnailIds.isEmpty()) {
            emptyMap()
        } else {
            trackDao.getThumbnailAssets(thumbnailIds).associateBy { it.thumbnailAssetId }
        }

        return tracks.associate { track ->
            val thumbnailId = track.thumbnailAssetId ?: track.embeddedThumbnailAssetId
            val localThumbnailUri = thumbnailId
                ?.let(thumbnailsById::get)
                ?.internalFilename
                ?.let { filename -> Uri.fromFile(File(storage.thumbnailsDir, filename)) }
            track.trackId to (localThumbnailUri ?: track.remoteThumbnailUrl?.let(Uri::parse))
        }
    }
}

object SmartPlaylistBuilder {
    fun buildDetails(
        tracks: List<TrackEntity>,
        stats: List<TrackStatsSnapshotEntity>,
        events: List<PlaybackEventEntity>,
        thumbnailUrisByTrackId: Map<String, Uri?>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<SmartPlaylistDetail> {
        val statsByTrackId = stats.associateBy { it.trackId }
        val tracksById = tracks.associateBy { it.trackId }
        val trackItem = { track: TrackEntity ->
            PlaylistTrackItem(
                trackId = track.trackId,
                displayTitle = AudioTitleFormatter.displayTitle(
                    title = track.title,
                    fallbackFilename = track.originalFilename
                ),
                artist = track.artist,
                album = track.album,
                genre = track.genre,
                year = track.year,
                durationMs = track.durationMs,
                isFavourite = track.isFavourite,
                thumbnailUri = thumbnailUrisByTrackId[track.trackId]
            )
        }
        val trackItems = { selectedTracks: List<TrackEntity> ->
            selectedTracks.map(trackItem)
        }
        val eligibleEvents = events.filter { !it.privateMode && it.validListen }
        val recentTrackIds = eligibleEvents
            .filter { it.trackId != null }
            .distinctBy { it.trackId }
            .mapNotNull { it.trackId }
            .take(100)

        return SmartPlaylistIds.ordered.map { smartPlaylistId ->
            when (smartPlaylistId) {
                SmartPlaylistIds.DISCOVER -> SmartPlaylistDetail(
                    smartPlaylistId = smartPlaylistId,
                    name = "Discover",
                    description = "Remote recommendations from your listening history.",
                    tracks = emptyList()
                )

                SmartPlaylistIds.FAVOURITES -> SmartPlaylistDetail(
                    smartPlaylistId = smartPlaylistId,
                    name = "Favourites",
                    description = "Songs marked as favourites.",
                    tracks = trackItems(
                        tracks
                            .filter { it.isFavourite }
                            .sortedByDescending { it.favouritedAt ?: 0L }
                    )
                )

                SmartPlaylistIds.MOST_LISTENED -> SmartPlaylistDetail(
                    smartPlaylistId = smartPlaylistId,
                    name = "Most Listened",
                    description = "Top songs by total listening time.",
                    tracks = stats
                        .filter { it.totalListenedMs >= MOST_LISTENED_MIN_MS }
                        .sortedByDescending { it.totalListenedMs }
                        .take(100)
                        .mapNotNull { tracksById[it.trackId] }
                        .map(trackItem)
                )

                SmartPlaylistIds.RECENTLY_PLAYED -> SmartPlaylistDetail(
                    smartPlaylistId = smartPlaylistId,
                    name = "Recently Played",
                    description = "Latest songs from listening history.",
                    tracks = recentTrackIds
                        .mapNotNull(tracksById::get)
                        .map(trackItem)
                )

                SmartPlaylistIds.NEVER_PLAYED -> SmartPlaylistDetail(
                    smartPlaylistId = smartPlaylistId,
                    name = "Never Played",
                    description = "Imported songs without a valid listen.",
                    tracks = trackItems(
                        tracks
                            .filter { track -> (statsByTrackId[track.trackId]?.playCount ?: 0) == 0 }
                            .sortedByDescending { it.importedAt }
                    )
                )

                SmartPlaylistIds.MISSING_METADATA -> SmartPlaylistDetail(
                    smartPlaylistId = smartPlaylistId,
                    name = "Missing Metadata",
                    description = "Songs missing common metadata fields.",
                    tracks = trackItems(
                        tracks
                            .filter { track ->
                                track.title.isBlank() ||
                                    track.artist.isBlank() ||
                                    track.album.isBlank() ||
                                    track.genre.isBlank() ||
                                    track.year.isNullOrBlank() ||
                                    (track.thumbnailAssetId == null &&
                                        track.embeddedThumbnailAssetId == null &&
                                        track.remoteThumbnailUrl == null)
                            }
                            .sortedByDescending { it.importedAt }
                    )
                )

                SmartPlaylistIds.MORNING -> timeBasedPlaylist(
                    smartPlaylistId = smartPlaylistId,
                    name = "Morning Mix",
                    description = "Most listened from 05:00 to 11:00.",
                    events = eligibleEvents,
                    tracksById = tracksById,
                    trackItem = trackItem,
                    zoneId = zoneId,
                    hourMatches = { hour -> hour in 5..10 }
                )

                SmartPlaylistIds.AFTERNOON -> timeBasedPlaylist(
                    smartPlaylistId = smartPlaylistId,
                    name = "Afternoon Mix",
                    description = "Most listened from 11:00 to 17:00.",
                    events = eligibleEvents,
                    tracksById = tracksById,
                    trackItem = trackItem,
                    zoneId = zoneId,
                    hourMatches = { hour -> hour in 11..16 }
                )

                SmartPlaylistIds.EVENING -> timeBasedPlaylist(
                    smartPlaylistId = smartPlaylistId,
                    name = "Evening Mix",
                    description = "Most listened from 17:00 to 22:00.",
                    events = eligibleEvents,
                    tracksById = tracksById,
                    trackItem = trackItem,
                    zoneId = zoneId,
                    hourMatches = { hour -> hour in 17..21 }
                )

                else -> timeBasedPlaylist(
                    smartPlaylistId = smartPlaylistId,
                    name = "Night Mix",
                    description = "Most listened from 22:00 to 05:00.",
                    events = eligibleEvents,
                    tracksById = tracksById,
                    trackItem = trackItem,
                    zoneId = zoneId,
                    hourMatches = { hour -> hour >= 22 || hour < 5 }
                )
            }
        }
    }

    private fun timeBasedPlaylist(
        smartPlaylistId: String,
        name: String,
        description: String,
        events: List<PlaybackEventEntity>,
        tracksById: Map<String, TrackEntity>,
        trackItem: (TrackEntity) -> PlaylistTrackItem,
        zoneId: ZoneId,
        hourMatches: (Int) -> Boolean
    ): SmartPlaylistDetail {
        val listeningByTrack = events
            .filter { event ->
                event.trackId != null &&
                    hourMatches(Instant.ofEpochMilli(event.startedAt).atZone(zoneId).hour)
            }
            .groupBy { it.trackId.orEmpty() }
            .mapValues { (_, trackEvents) -> trackEvents.sumOf { it.listenedMs } }

        return SmartPlaylistDetail(
            smartPlaylistId = smartPlaylistId,
            name = name,
            description = description,
            tracks = listeningByTrack
                .entries
                .filter { it.value > 0L }
                .sortedByDescending { it.value }
                .take(50)
                .mapNotNull { tracksById[it.key] }
                .map(trackItem)
        )
    }

    private const val MOST_LISTENED_MIN_MS = 30_000L
}
