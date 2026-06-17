package com.fenl.fenlzer.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.fenl.fenlzer.data.storage.FenlzerStorage
import java.io.File

class CarMediaItemMapper(
    private val storage: FenlzerStorage
) {
    fun mediaItemFor(node: CarMediaNode): MediaItem {
        val track = node.track
        val metadata = MediaMetadata.Builder()
            .setTitle(node.title)
            .setSubtitle(node.subtitle)
            .setIsBrowsable(node.isBrowsable)
            .setIsPlayable(node.isPlayable)
            .setMediaType(mediaTypeFor(node))
            .apply {
                if (track != null) {
                    setArtist(track.artist)
                    setAlbumTitle(track.album)
                    setDurationMs(track.durationMs)
                    setArtworkUri(artworkUriFor(track))
                }
            }
            .build()

        return MediaItem.Builder()
            .setMediaId(node.mediaId)
            .setMediaMetadata(metadata)
            .apply {
                if (track != null) {
                    setUri(Uri.fromFile(File(storage.audioDir, track.internalFilename)))
                }
            }
            .build()
    }

    fun mediaItemFor(track: CarLibraryTrack): MediaItem =
        mediaItemFor(
            CarMediaNode.playable(
                mediaId = CarMediaIds.track(track.trackId),
                title = track.title,
                subtitle = track.artist,
                track = track
            )
        )

    private fun artworkUriFor(track: CarLibraryTrack): Uri? =
        track.thumbnailInternalFilename
            ?.let { filename -> Uri.fromFile(File(storage.thumbnailsDir, filename)) }
            ?: track.remoteThumbnailUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse)

    private fun mediaTypeFor(node: CarMediaNode): Int =
        when {
            node.isPlayable -> MediaMetadata.MEDIA_TYPE_MUSIC
            node.mediaId == CarMediaIds.PLAYLISTS ||
                node.mediaId == CarMediaIds.SMART_PLAYLISTS -> MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
            node.mediaId.startsWith("fenlzer:auto:playlist:") ||
                node.mediaId.startsWith("fenlzer:auto:smart:") -> MediaMetadata.MEDIA_TYPE_PLAYLIST
            else -> MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
        }
}
