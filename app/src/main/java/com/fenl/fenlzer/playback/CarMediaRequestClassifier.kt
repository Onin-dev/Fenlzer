package com.fenl.fenlzer.playback

import androidx.media3.common.MediaItem

object CarMediaRequestClassifier {
    fun isCarLibraryRequest(mediaItems: List<MediaItem>): Boolean =
        mediaItems.any(::isCarLibraryItem)

    private fun isCarLibraryItem(item: MediaItem): Boolean =
        item.requestMetadata.searchQuery?.isNotBlank() == true ||
            item.mediaId == CarMediaIds.ROOT ||
            item.mediaId == CarMediaIds.SONGS ||
            item.mediaId == CarMediaIds.PLAYLISTS ||
            item.mediaId == CarMediaIds.SMART_PLAYLISTS ||
            CarMediaIds.trackId(item.mediaId) != null ||
            CarMediaIds.playlistId(item.mediaId) != null ||
            CarMediaIds.smartPlaylistId(item.mediaId) != null
}
