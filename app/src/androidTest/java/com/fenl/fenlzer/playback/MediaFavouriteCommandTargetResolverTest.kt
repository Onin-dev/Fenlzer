package com.fenl.fenlzer.playback

import android.net.Uri
import com.fenl.fenlzer.data.repository.QueueTrackItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaFavouriteCommandTargetResolverTest {
    @Test
    fun queueMediaIdResolvesDownloadedCurrentSong() {
        val target = MediaFavouriteCommandTargetResolver.resolve(
            currentMediaId = "queue-local",
            queueItems = listOf(localItem())
        )

        assertEquals(FavouriteCommandTarget("track-1", isFavourite = false), target)
    }

    @Test
    fun remoteOnlyQueueItemCannotBeFavouritedBySessionCommand() {
        assertNull(
            MediaFavouriteCommandTargetResolver.resolve(
                currentMediaId = "queue-remote",
                queueItems = listOf(remoteItem())
            )
        )
    }

    @Test
    fun carTrackMediaIdResolvesWhenTrackIsInLocalQueue() {
        val target = MediaFavouriteCommandTargetResolver.resolve(
            currentMediaId = CarMediaIds.track("track-1"),
            queueItems = listOf(localItem(isFavourite = true))
        )

        assertEquals(FavouriteCommandTarget("track-1", isFavourite = true), target)
    }

    private fun localItem(isFavourite: Boolean = false) = QueueTrackItem(
        queueItemId = "queue-local",
        trackId = "track-1",
        displayTitle = "Song",
        artist = "Artist",
        durationMs = 120_000L,
        position = 0,
        state = "CURRENT",
        insertedBy = "TAP",
        isFavourite = isFavourite,
        audioUri = Uri.EMPTY,
        thumbnailUri = null
    )

    private fun remoteItem() = QueueTrackItem(
        queueItemId = "queue-remote",
        trackId = "remote-1",
        localTrackId = null,
        remoteItemId = "remote-1",
        displayTitle = "Remote",
        artist = "Channel",
        durationMs = 120_000L,
        position = 0,
        state = "CURRENT",
        insertedBy = "DISCOVER",
        isFavourite = false,
        audioUri = Uri.EMPTY,
        thumbnailUri = null,
        isRemote = true
    )
}
