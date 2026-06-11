package com.fenl.fenlzer.playback

import com.fenl.fenlzer.data.local.entity.PlaybackEventEntity
import com.fenl.fenlzer.data.local.entity.PlaylistEntity
import com.fenl.fenlzer.data.local.entity.PlaylistTrackEntity
import com.fenl.fenlzer.data.local.entity.TrackEntity
import com.fenl.fenlzer.data.local.entity.TrackStatsSnapshotEntity
import com.fenl.fenlzer.data.repository.SmartPlaylistIds
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarMediaLibraryTreeBuilderTest {
    private val builder = CarMediaLibraryTreeBuilder(zoneId = ZoneId.of("UTC"))

    @Test
    fun buildExposesOnlyAndroidAutoAllowedSmartPlaylists() {
        val tracks = listOf(
            track("favourite", title = "Favourite", favourite = true, favouritedAt = 10L),
            track("most", title = "Most"),
            track("recent", title = "Recent"),
            track("never", title = "Never"),
            track("missing", title = "", artist = "", album = "", genre = "", year = null)
        )
        val stats = listOf(stats("most", totalListenedMs = 45_000L))
        val events = listOf(
            event(
                eventId = "recent-event",
                trackId = "recent",
                startedAt = Instant.parse("2026-06-09T06:00:00Z").toEpochMilli()
            )
        )

        val tree = builder.build(
            tracks = tracks,
            playlists = emptyList(),
            playlistTracks = emptyList(),
            stats = stats,
            events = events
        )

        val smartMediaIds = tree.children(CarMediaIds.SMART_PLAYLISTS, page = 0, pageSize = 20)
            .map { it.mediaId }

        assertEquals(
            CarMediaLibraryTreeBuilder.ANDROID_AUTO_SMART_PLAYLIST_IDS.map(CarMediaIds::smartPlaylist),
            smartMediaIds
        )
        assertFalse(smartMediaIds.contains(CarMediaIds.smartPlaylist(SmartPlaylistIds.DISCOVER)))
        assertFalse(smartMediaIds.contains(CarMediaIds.smartPlaylist(SmartPlaylistIds.NEVER_PLAYED)))
        assertFalse(smartMediaIds.contains(CarMediaIds.smartPlaylist(SmartPlaylistIds.MISSING_METADATA)))
    }

    @Test
    fun buildFiltersPlaylistChildrenToLocalLibraryTracks() {
        val playlist = PlaylistEntity(
            playlistId = "playlist-1",
            name = "Road Songs",
            playlistType = CarMediaLibraryTreeBuilder.REGULAR_PLAYLIST_TYPE,
            customThumbnailAssetId = null,
            createdAt = 1L,
            modifiedAt = 1L
        )
        val tree = builder.build(
            tracks = listOf(track("local-1", title = "Local One")),
            playlists = listOf(playlist),
            playlistTracks = listOf(
                playlistTrack("playlist-1", "local-1", position = 0),
                playlistTrack("playlist-1", "missing-or-remote", position = 1)
            ),
            stats = emptyList(),
            events = emptyList()
        )

        val playlistChildren = tree.children(CarMediaIds.playlist("playlist-1"), page = 0, pageSize = 20)

        assertEquals(listOf(CarMediaIds.track("local-1")), playlistChildren.map { it.mediaId })
        assertEquals(listOf("local-1"), tree.playableTracksFor(listOf(CarMediaIds.playlist("playlist-1"))).map { it.trackId })
    }

    @Test
    fun buildProvidesPagedLocalSongsAndNoUnknownRemoteItems() {
        val tree = builder.build(
            tracks = listOf(
                track("b", title = "Bravo"),
                track("a", title = "Alpha"),
                track("c", title = "Charlie")
            ),
            playlists = emptyList(),
            playlistTracks = emptyList(),
            stats = emptyList(),
            events = emptyList()
        )

        assertEquals(
            listOf(CarMediaIds.track("a"), CarMediaIds.track("b")),
            tree.children(CarMediaIds.SONGS, page = 0, pageSize = 2).map { it.mediaId }
        )
        assertTrue(tree.playableTracksFor(listOf("remote-item-id")).isEmpty())
    }

    private fun track(
        trackId: String,
        title: String,
        artist: String = "Artist",
        album: String = "Album",
        genre: String = "Genre",
        year: String? = "2026",
        favourite: Boolean = false,
        favouritedAt: Long? = null
    ): TrackEntity = TrackEntity(
        trackId = trackId,
        title = title,
        titleSortKey = title.lowercase(),
        artist = artist,
        artistSortKey = artist.lowercase(),
        album = album,
        albumSortKey = album.lowercase(),
        albumArtist = artist,
        albumArtistSortKey = artist.lowercase(),
        genre = genre,
        year = year,
        trackNumber = null,
        discNumber = null,
        durationMs = 180_000L,
        notes = "",
        sourceType = "LOCAL_FILE",
        youtubeVideoId = null,
        sourceUrl = null,
        originalFilename = "$trackId.mp3",
        internalFilename = "$trackId.mp3",
        audioHash = "hash-$trackId",
        fileSizeBytes = 100L,
        finalAudioFormat = "MP3",
        thumbnailAssetId = null,
        embeddedThumbnailAssetId = null,
        remoteThumbnailUrl = null,
        isFavourite = favourite,
        favouritedAt = favouritedAt,
        importedAt = 1L,
        updatedAt = 1L
    )

    private fun playlistTrack(
        playlistId: String,
        trackId: String,
        position: Int
    ): PlaylistTrackEntity = PlaylistTrackEntity(
        playlistId = playlistId,
        trackId = trackId,
        position = position,
        addedAt = 1L
    )

    private fun stats(
        trackId: String,
        totalListenedMs: Long
    ): TrackStatsSnapshotEntity = TrackStatsSnapshotEntity(
        trackId = trackId,
        playCount = 1,
        skipCount = 0,
        completionCount = 0,
        totalListenedMs = totalListenedMs,
        firstPlayedAt = 1L,
        lastPlayedAt = 1L,
        averageCompletionPercent = 0.5f
    )

    private fun event(
        eventId: String,
        trackId: String,
        startedAt: Long
    ): PlaybackEventEntity = PlaybackEventEntity(
        eventId = eventId,
        trackId = trackId,
        remoteItemId = null,
        sessionId = "session",
        startedAt = startedAt,
        endedAt = startedAt + 30_000L,
        listenedMs = 30_000L,
        durationMsAtPlayback = 180_000L,
        validListen = true,
        skip = false,
        completion = false,
        completionPercent = 0.5f,
        stopPositionMs = 30_000L,
        privateMode = false,
        sourceContext = "Queue from Home"
    )
}
