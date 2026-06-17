package com.fenl.fenlzer.playback

import androidx.media3.common.MediaItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarMediaRequestClassifierTest {
    @Test
    fun queueMediaIdsAreNotCarRequests() {
        val queueItem = MediaItem.Builder()
            .setMediaId("queue-item-123")
            .build()

        assertFalse(CarMediaRequestClassifier.isCarLibraryRequest(listOf(queueItem)))
    }

    @Test
    fun carMediaIdsAndSearchQueriesAreCarRequests() {
        val carTrack = MediaItem.Builder()
            .setMediaId(CarMediaIds.track("track-1"))
            .build()
        val search = MediaItem.Builder()
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setSearchQuery("artist song")
                    .build()
            )
            .build()

        assertTrue(CarMediaRequestClassifier.isCarLibraryRequest(listOf(carTrack)))
        assertTrue(CarMediaRequestClassifier.isCarLibraryRequest(listOf(search)))
    }
}
