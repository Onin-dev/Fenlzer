package com.fenl.fenlzer.playback

import com.fenl.fenlzer.data.local.entity.RemoteItemEntity
import com.fenl.fenlzer.data.local.entity.TrackEntity
import com.fenl.fenlzer.domain.text.AudioTitleFormatter

object PlaybackSharePayloadBuilder {
    fun localTrack(track: TrackEntity): String {
        val titleLine = titleLine(
            title = AudioTitleFormatter.displayTitle(
                title = track.title,
                fallbackFilename = track.originalFilename
            ),
            artist = track.artist
        )
        return youtubeUrl(track.sourceUrl, track.youtubeVideoId)
            ?.let { url -> "$titleLine\n$url" }
            ?: titleLine
    }

    fun remoteItem(remoteItem: RemoteItemEntity): String {
        val titleLine = titleLine(
            title = remoteItem.title,
            artist = remoteItem.artistOrChannel.orEmpty()
        )
        return youtubeUrl(remoteItem.sourceUrl, remoteItem.youtubeVideoId)
            ?.let { url -> "$titleLine\n$url" }
            ?: titleLine
    }

    private fun titleLine(title: String, artist: String): String =
        if (artist.isBlank()) title else "$title - $artist"

    private fun youtubeUrl(sourceUrl: String?, youtubeVideoId: String?): String? =
        sourceUrl?.takeIf { it.isNotBlank() }
            ?: youtubeVideoId
                ?.takeIf { it.isNotBlank() }
                ?.let { videoId -> "https://www.youtube.com/watch?v=$videoId" }
}
