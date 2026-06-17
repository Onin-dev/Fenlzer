package com.fenl.fenlzer.playback

import com.fenl.fenlzer.data.local.entity.RemoteItemEntity
import com.fenl.fenlzer.data.local.entity.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSharePayloadBuilderTest {
    @Test
    fun localFileSharesTitleAndArtistOnly() {
        val payload = PlaybackSharePayloadBuilder.localTrack(
            track(sourceUrl = null, youtubeVideoId = null)
        )

        assertEquals("Local Song - Local Artist", payload)
    }

    @Test
    fun youtubeBackedLocalTrackSharesOriginalUrl() {
        val payload = PlaybackSharePayloadBuilder.localTrack(
            track(
                sourceUrl = "https://youtu.be/video-1",
                youtubeVideoId = "video-1"
            )
        )

        assertEquals("Local Song - Local Artist\nhttps://youtu.be/video-1", payload)
    }

    @Test
    fun remoteItemFallsBackToYoutubeWatchUrlWhenSourceUrlIsMissing() {
        val payload = PlaybackSharePayloadBuilder.remoteItem(
            RemoteItemEntity(
                remoteItemId = "remote-1",
                youtubeVideoId = "video-2",
                sourceUrl = null,
                title = "Remote Song",
                artistOrChannel = "Remote Channel",
                durationMs = 120_000L,
                thumbnailUrl = null,
                canStream = true,
                canDownload = true,
                streamState = "REMOTE_ONLY",
                importState = "NOT_IMPORTED",
                createdAt = 1L,
                updatedAt = 1L
            )
        )

        assertEquals(
            "Remote Song - Remote Channel\nhttps://www.youtube.com/watch?v=video-2",
            payload
        )
    }

    private fun track(sourceUrl: String?, youtubeVideoId: String?) = TrackEntity(
        trackId = "track-1",
        title = "Local Song",
        titleSortKey = "local song",
        artist = "Local Artist",
        artistSortKey = "local artist",
        album = "Album",
        albumSortKey = "album",
        albumArtist = "Local Artist",
        albumArtistSortKey = "local artist",
        genre = "",
        year = null,
        trackNumber = null,
        discNumber = null,
        durationMs = 120_000L,
        notes = "",
        sourceType = if (sourceUrl == null) "LOCAL_FILE" else "YOUTUBE_SEARCH",
        youtubeVideoId = youtubeVideoId,
        sourceUrl = sourceUrl,
        originalFilename = "local-song.mp3",
        internalFilename = "hash.mp3",
        audioHash = "hash",
        fileSizeBytes = 1024L,
        finalAudioFormat = "MP3",
        thumbnailAssetId = null,
        embeddedThumbnailAssetId = null,
        remoteThumbnailUrl = null,
        isFavourite = false,
        favouritedAt = null,
        importedAt = 1L,
        updatedAt = 1L
    )
}
