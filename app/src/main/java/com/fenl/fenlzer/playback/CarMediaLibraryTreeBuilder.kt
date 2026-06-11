package com.fenl.fenlzer.playback

import com.fenl.fenlzer.data.local.entity.PlaybackEventEntity
import com.fenl.fenlzer.data.local.entity.PlaylistEntity
import com.fenl.fenlzer.data.local.entity.PlaylistTrackEntity
import com.fenl.fenlzer.data.local.entity.TrackEntity
import com.fenl.fenlzer.data.local.entity.TrackStatsSnapshotEntity
import com.fenl.fenlzer.data.repository.SmartPlaylistBuilder
import com.fenl.fenlzer.data.repository.SmartPlaylistIds
import com.fenl.fenlzer.domain.text.AudioTitleFormatter
import java.time.ZoneId

object CarMediaIds {
    const val ROOT = "fenlzer:auto:root"
    const val SONGS = "fenlzer:auto:songs"
    const val PLAYLISTS = "fenlzer:auto:playlists"
    const val SMART_PLAYLISTS = "fenlzer:auto:smart_playlists"

    private const val TRACK_PREFIX = "fenlzer:auto:track:"
    private const val PLAYLIST_PREFIX = "fenlzer:auto:playlist:"
    private const val SMART_PREFIX = "fenlzer:auto:smart:"

    fun track(trackId: String): String = TRACK_PREFIX + trackId

    fun playlist(playlistId: String): String = PLAYLIST_PREFIX + playlistId

    fun smartPlaylist(smartPlaylistId: String): String = SMART_PREFIX + smartPlaylistId

    fun trackId(mediaId: String): String? =
        mediaId.removePrefixOrNull(TRACK_PREFIX)

    fun playlistId(mediaId: String): String? =
        mediaId.removePrefixOrNull(PLAYLIST_PREFIX)

    fun smartPlaylistId(mediaId: String): String? =
        mediaId.removePrefixOrNull(SMART_PREFIX)

    private fun String.removePrefixOrNull(prefix: String): String? =
        takeIf { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.takeIf { it.isNotBlank() }
}

class CarMediaLibraryTreeBuilder(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    fun build(
        tracks: List<TrackEntity>,
        playlists: List<PlaylistEntity>,
        playlistTracks: List<PlaylistTrackEntity>,
        stats: List<TrackStatsSnapshotEntity>,
        events: List<PlaybackEventEntity>,
        thumbnailInternalFilenamesByTrackId: Map<String, String?> = emptyMap()
    ): CarMediaLibraryTree {
        val carTracksById = tracks
            .sortedWith(compareBy({ it.titleSortKey }, { it.artistSortKey }, { it.importedAt }))
            .associate { track ->
                track.trackId to track.toCarTrack(thumbnailInternalFilenamesByTrackId[track.trackId])
            }
        val songs = carTracksById.values.toList()
        val root = CarMediaNode.browsable(CarMediaIds.ROOT, "Fenlzer")
        val songsNode = CarMediaNode.browsable(
            mediaId = CarMediaIds.SONGS,
            title = "Songs",
            subtitle = "${songs.size} songs"
        )
        val playlistsNode = CarMediaNode.browsable(
            mediaId = CarMediaIds.PLAYLISTS,
            title = "Playlists"
        )
        val smartPlaylistsNode = CarMediaNode.browsable(
            mediaId = CarMediaIds.SMART_PLAYLISTS,
            title = "Smart Playlists"
        )
        val regularPlaylistNodesAndTracks = playlists
            .filter { it.playlistType == REGULAR_PLAYLIST_TYPE }
            .map { playlist ->
                val playlistTrackIds = playlistTracks
                    .filter { it.playlistId == playlist.playlistId }
                    .sortedBy { it.position }
                    .mapNotNull { carTracksById[it.trackId] }
                CarMediaNode.browsable(
                    mediaId = CarMediaIds.playlist(playlist.playlistId),
                    title = playlist.name,
                    subtitle = "${playlistTrackIds.size} songs"
                ) to playlistTrackIds
            }

        val smartDetails = SmartPlaylistBuilder.buildDetails(
            tracks = tracks,
            stats = stats,
            events = events,
            thumbnailUrisByTrackId = emptyMap(),
            zoneId = zoneId
        ).filter { it.smartPlaylistId in ANDROID_AUTO_SMART_PLAYLIST_IDS }

        val smartPlaylistNodesAndTracks = smartDetails.map { detail ->
            val smartTracks = detail.tracks.mapNotNull { carTracksById[it.trackId] }
            CarMediaNode.browsable(
                mediaId = CarMediaIds.smartPlaylist(detail.smartPlaylistId),
                title = detail.name,
                subtitle = "${smartTracks.size} songs"
            ) to smartTracks
        }

        val childrenByParentId = buildMap {
            put(CarMediaIds.ROOT, listOf(songsNode, playlistsNode, smartPlaylistsNode))
            put(CarMediaIds.SONGS, songs.map { it.toPlayableNode() })
            put(CarMediaIds.PLAYLISTS, regularPlaylistNodesAndTracks.map { it.first })
            put(CarMediaIds.SMART_PLAYLISTS, smartPlaylistNodesAndTracks.map { it.first })
            regularPlaylistNodesAndTracks.forEach { (node, playlistSongs) ->
                put(node.mediaId, playlistSongs.map { it.toPlayableNode() })
            }
            smartPlaylistNodesAndTracks.forEach { (node, smartSongs) ->
                put(node.mediaId, smartSongs.map { it.toPlayableNode() })
            }
        }
        val nodesById = buildMap {
            put(root.mediaId, root)
            childrenByParentId.values.flatten().forEach { put(it.mediaId, it) }
            songs.forEach { put(CarMediaIds.track(it.trackId), it.toPlayableNode()) }
        }

        return CarMediaLibraryTree(
            root = root,
            childrenByParentId = childrenByParentId,
            nodesById = nodesById,
            tracksById = carTracksById
        )
    }

    private fun TrackEntity.toCarTrack(thumbnailInternalFilename: String?): CarLibraryTrack =
        CarLibraryTrack(
            trackId = trackId,
            title = AudioTitleFormatter.displayTitle(
                title = title,
                fallbackFilename = originalFilename
            ),
            artist = artist,
            album = album,
            durationMs = durationMs,
            internalFilename = internalFilename,
            thumbnailInternalFilename = thumbnailInternalFilename,
            remoteThumbnailUrl = remoteThumbnailUrl
        )

    private fun CarLibraryTrack.toPlayableNode(): CarMediaNode =
        CarMediaNode.playable(
            mediaId = CarMediaIds.track(trackId),
            title = title,
            subtitle = artist,
            track = this
        )

    companion object {
        const val REGULAR_PLAYLIST_TYPE = "REGULAR"

        val ANDROID_AUTO_SMART_PLAYLIST_IDS = listOf(
            SmartPlaylistIds.FAVOURITES,
            SmartPlaylistIds.MOST_LISTENED,
            SmartPlaylistIds.RECENTLY_PLAYED,
            SmartPlaylistIds.MORNING,
            SmartPlaylistIds.AFTERNOON,
            SmartPlaylistIds.EVENING,
            SmartPlaylistIds.NIGHT
        )
    }
}

data class CarMediaLibraryTree(
    val root: CarMediaNode,
    private val childrenByParentId: Map<String, List<CarMediaNode>>,
    private val nodesById: Map<String, CarMediaNode>,
    private val tracksById: Map<String, CarLibraryTrack>
) {
    fun node(mediaId: String): CarMediaNode? = nodesById[mediaId]

    fun children(parentId: String, page: Int, pageSize: Int): List<CarMediaNode> =
        childrenByParentId[parentId]
            .orEmpty()
            .paged(page = page, pageSize = pageSize)

    fun trackForMediaId(mediaId: String): CarLibraryTrack? =
        CarMediaIds.trackId(mediaId)?.let(tracksById::get)

    fun playableTracksFor(mediaIds: List<String>): List<CarLibraryTrack> =
        mediaIds.flatMap { mediaId ->
            when {
                mediaId == CarMediaIds.SONGS -> childrenByParentId.getValue(CarMediaIds.SONGS)
                    .mapNotNull { it.track }

                mediaId.startsWith("fenlzer:auto:playlist:") ||
                    mediaId.startsWith("fenlzer:auto:smart:") -> childrenByParentId[mediaId]
                    .orEmpty()
                    .mapNotNull { it.track }

                else -> listOfNotNull(trackForMediaId(mediaId))
            }
        }

    private fun List<CarMediaNode>.paged(page: Int, pageSize: Int): List<CarMediaNode> {
        val safePage = page.coerceAtLeast(0)
        val safePageSize = pageSize.coerceAtLeast(1)
        val fromIndex = safePage * safePageSize
        if (fromIndex >= size) return emptyList()
        return subList(fromIndex, (fromIndex + safePageSize).coerceAtMost(size))
    }
}

data class CarMediaNode(
    val mediaId: String,
    val title: String,
    val subtitle: String? = null,
    val isBrowsable: Boolean,
    val isPlayable: Boolean,
    val track: CarLibraryTrack? = null
) {
    companion object {
        fun browsable(
            mediaId: String,
            title: String,
            subtitle: String? = null
        ): CarMediaNode = CarMediaNode(
            mediaId = mediaId,
            title = title,
            subtitle = subtitle,
            isBrowsable = true,
            isPlayable = false
        )

        fun playable(
            mediaId: String,
            title: String,
            subtitle: String?,
            track: CarLibraryTrack
        ): CarMediaNode = CarMediaNode(
            mediaId = mediaId,
            title = title,
            subtitle = subtitle,
            isBrowsable = false,
            isPlayable = true,
            track = track
        )
    }
}

data class CarLibraryTrack(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val internalFilename: String,
    val thumbnailInternalFilename: String?,
    val remoteThumbnailUrl: String?
)
