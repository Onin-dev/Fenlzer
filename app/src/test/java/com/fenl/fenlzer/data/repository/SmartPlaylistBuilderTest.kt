package com.fenl.fenlzer.data.repository

import com.fenl.fenlzer.data.local.entity.PlaybackEventEntity
import com.fenl.fenlzer.data.local.entity.TrackEntity
import com.fenl.fenlzer.data.local.entity.TrackStatsSnapshotEntity
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPlaylistBuilderTest {
    @Test
    fun buildDetailsAppliesCoreSmartPlaylistRules() {
        val tracks = listOf(
            track("favourite-old", title = "Favourite Old", favourite = true, favouritedAt = 10L),
            track("favourite-new", title = "Favourite New", favourite = true, favouritedAt = 20L),
            track("most-listened", title = "Most Listened"),
            track("below-threshold", title = "Below Threshold"),
            track("never-played", title = "Never Played"),
            track(
                "missing-metadata",
                title = "",
                artist = "",
                album = "",
                genre = "",
                year = null,
                hasThumbnail = false
            )
        )
        val stats = listOf(
            stats("most-listened", playCount = 3, totalListenedMs = 45_000L),
            stats("below-threshold", playCount = 2, totalListenedMs = 29_999L),
            stats("favourite-new", playCount = 1, totalListenedMs = 5_000L)
        )
        val events = listOf(
            event("private-newest", "favourite-old", startedAt = 3_000L, privateMode = true),
            event("recent-newer", "below-threshold", startedAt = 2_000L),
            event("recent-older", "most-listened", startedAt = 1_000L)
        )

        val details = SmartPlaylistBuilder.buildDetails(
            tracks = tracks,
            stats = stats,
            events = events,
            thumbnailUrisByTrackId = emptyMap(),
            zoneId = ZoneId.of("UTC")
        ).associateBy { it.smartPlaylistId }

        assertEquals(
            listOf("favourite-new", "favourite-old"),
            details.getValue(SmartPlaylistIds.FAVOURITES).tracks.map { it.trackId }
        )
        assertEquals(
            listOf("most-listened"),
            details.getValue(SmartPlaylistIds.MOST_LISTENED).tracks.map { it.trackId }
        )
        assertEquals(
            listOf("below-threshold", "most-listened"),
            details.getValue(SmartPlaylistIds.RECENTLY_PLAYED).tracks.map { it.trackId }
        )
        assertTrue(
            details.getValue(SmartPlaylistIds.NEVER_PLAYED)
                .tracks
                .map { it.trackId }
                .contains("never-played")
        )
        assertEquals(
            listOf("missing-metadata"),
            details.getValue(SmartPlaylistIds.MISSING_METADATA).tracks.map { it.trackId }
        )
    }

    @Test
    fun buildDetailsCreatesTimeBasedMixesFromListeningDuration() {
        val tracks = listOf(
            track("morning-long", title = "Morning Long"),
            track("morning-short", title = "Morning Short"),
            track("night-song", title = "Night Song")
        )
        val events = listOf(
            event(
                eventId = "morning-long",
                trackId = "morning-long",
                startedAt = Instant.parse("2026-06-09T06:00:00Z").toEpochMilli(),
                listenedMs = 40_000L
            ),
            event(
                eventId = "morning-short",
                trackId = "morning-short",
                startedAt = Instant.parse("2026-06-09T08:00:00Z").toEpochMilli(),
                listenedMs = 10_000L
            ),
            event(
                eventId = "night-song",
                trackId = "night-song",
                startedAt = Instant.parse("2026-06-09T23:00:00Z").toEpochMilli(),
                listenedMs = 30_000L
            )
        )

        val details = SmartPlaylistBuilder.buildDetails(
            tracks = tracks,
            stats = emptyList(),
            events = events,
            thumbnailUrisByTrackId = emptyMap(),
            zoneId = ZoneId.of("UTC")
        ).associateBy { it.smartPlaylistId }

        assertEquals(
            listOf("morning-long", "morning-short"),
            details.getValue(SmartPlaylistIds.MORNING).tracks.map { it.trackId }
        )
        assertEquals(
            listOf("night-song"),
            details.getValue(SmartPlaylistIds.NIGHT).tracks.map { it.trackId }
        )
    }

    private fun track(
        trackId: String,
        title: String,
        artist: String = "Artist",
        album: String = "Album",
        genre: String = "Genre",
        year: String? = "2026",
        favourite: Boolean = false,
        favouritedAt: Long? = null,
        hasThumbnail: Boolean = true
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
        remoteThumbnailUrl = if (hasThumbnail) "https://example.com/$trackId.jpg" else null,
        isFavourite = favourite,
        favouritedAt = favouritedAt,
        importedAt = 1L,
        updatedAt = 1L
    )

    private fun stats(
        trackId: String,
        playCount: Int,
        totalListenedMs: Long
    ): TrackStatsSnapshotEntity = TrackStatsSnapshotEntity(
        trackId = trackId,
        playCount = playCount,
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
        startedAt: Long,
        listenedMs: Long = 30_000L,
        privateMode: Boolean = false
    ): PlaybackEventEntity = PlaybackEventEntity(
        eventId = eventId,
        trackId = trackId,
        remoteItemId = null,
        sessionId = "session",
        startedAt = startedAt,
        endedAt = startedAt + listenedMs,
        listenedMs = listenedMs,
        durationMsAtPlayback = 180_000L,
        validListen = true,
        skip = false,
        completion = false,
        completionPercent = 0.5f,
        stopPositionMs = listenedMs,
        privateMode = privateMode,
        sourceContext = "Queue from Home"
    )
}
