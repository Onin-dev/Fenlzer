package com.fenl.fenlzer.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.fenl.fenlzer.data.repository.PersistentQueue
import com.fenl.fenlzer.data.repository.QueueTrackItem
import android.net.Uri

object QueueMediaItemMapper {
    fun mediaItemFor(
        item: QueueTrackItem,
        artworkUri: Uri? = item.thumbnailUri
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(item.displayTitle)
            .setArtist(item.artist)
            .setArtworkUri(artworkUri)
            .build()
        return MediaItem.Builder()
            .setMediaId(item.queueItemId)
            .setUri(item.audioUri)
            .setMediaMetadata(metadata)
            .build()
    }

    suspend fun localResumeItems(
        queue: PersistentQueue,
        artworkUriFor: suspend (QueueTrackItem) -> Uri? = { item -> item.thumbnailUri }
    ): QueueResumeItems {
        val localItems = queue.items
            .filter { item -> item.localTrackId != null && !item.isRemote }
        if (localItems.isEmpty()) {
            return QueueResumeItems(
                mediaItems = emptyList(),
                startIndex = C.INDEX_UNSET,
                startPositionMs = C.TIME_UNSET
            )
        }

        val currentLocalIndex = localItems.indexOfFirst { item ->
            item.queueItemId == queue.currentQueueItemId
        }
        val startIndex = currentLocalIndex.takeIf { it >= 0 } ?: 0
        val startPositionMs = if (currentLocalIndex >= 0) {
            queue.playbackPositionMs.coerceAtLeast(0L)
        } else {
            0L
        }

        return QueueResumeItems(
            mediaItems = localItems.map { item ->
                mediaItemFor(item, artworkUriFor(item) ?: item.thumbnailUri)
            },
            startIndex = startIndex,
            startPositionMs = startPositionMs
        )
    }
}

data class QueueResumeItems(
    val mediaItems: List<MediaItem>,
    val startIndex: Int,
    val startPositionMs: Long
)
