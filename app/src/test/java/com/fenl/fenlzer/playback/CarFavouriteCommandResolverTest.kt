package com.fenl.fenlzer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarFavouriteCommandResolverTest {
    @Test
    fun trackIdForMediaIdUsesAutoTrackIdsDirectly() {
        assertEquals(
            "track-1",
            CarFavouriteCommandResolver.trackIdForMediaId(
                currentMediaId = CarMediaIds.track("track-1"),
                queueTrackIdForMediaId = { error("Queue lookup should not be needed") }
            )
        )
    }

    @Test
    fun trackIdForMediaIdFallsBackToDownloadedQueueItems() {
        assertEquals(
            "track-2",
            CarFavouriteCommandResolver.trackIdForMediaId(
                currentMediaId = "queue-item-2",
                queueTrackIdForMediaId = { mediaId ->
                    if (mediaId == "queue-item-2") "track-2" else null
                }
            )
        )
    }

    @Test
    fun trackIdForMediaIdRejectsRemoteOrUnknownItems() {
        assertNull(
            CarFavouriteCommandResolver.trackIdForMediaId(
                currentMediaId = "remote-queue-item",
                queueTrackIdForMediaId = { null }
            )
        )
        assertNull(
            CarFavouriteCommandResolver.trackIdForMediaId(
                currentMediaId = null,
                queueTrackIdForMediaId = { "track" }
            )
        )
    }
}
