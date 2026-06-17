package com.fenl.fenlzer.playback

import android.net.Uri
import androidx.media3.common.C
import com.fenl.fenlzer.data.repository.PersistentQueue
import com.fenl.fenlzer.data.repository.QueueTrackItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueMediaItemMapperTest {
    @Test
    fun localResumeItemsExcludeRemoteOnlyEntriesAndPreserveCurrentPosition() = runBlocking {
        val queue = PersistentQueue(
            queueStateId = "default",
            sourceLabel = "Queue from Discover",
            isModified = false,
            currentQueueItemId = "queue-local-2",
            repeatMode = "ALL",
            shuffleEnabled = false,
            playbackPositionMs = 42_000L,
            wasPlaying = true,
            items = listOf(
                localItem("queue-local-1", "track-1", position = 0),
                remoteItem(position = 1),
                localItem("queue-local-2", "track-2", position = 2)
            )
        )

        val resumeItems = QueueMediaItemMapper.localResumeItems(queue)

        assertEquals(listOf("queue-local-1", "queue-local-2"), resumeItems.mediaItems.map { it.mediaId })
        assertEquals(1, resumeItems.startIndex)
        assertEquals(42_000L, resumeItems.startPositionMs)
    }

    @Test
    fun localResumeItemsReturnUnsetWhenQueueHasOnlyRemoteSongs() = runBlocking {
        val queue = PersistentQueue(
            queueStateId = "default",
            sourceLabel = "Queue from Discover",
            isModified = false,
            currentQueueItemId = "queue-remote",
            repeatMode = "ALL",
            shuffleEnabled = false,
            playbackPositionMs = 42_000L,
            wasPlaying = true,
            items = listOf(remoteItem(position = 0))
        )

        val resumeItems = QueueMediaItemMapper.localResumeItems(queue)

        assertEquals(emptyList<String>(), resumeItems.mediaItems.map { it.mediaId })
        assertEquals(C.INDEX_UNSET, resumeItems.startIndex)
        assertEquals(C.TIME_UNSET, resumeItems.startPositionMs)
    }

    private fun localItem(queueItemId: String, trackId: String, position: Int) = QueueTrackItem(
        queueItemId = queueItemId,
        trackId = trackId,
        displayTitle = "Song $trackId",
        artist = "Artist",
        durationMs = 120_000L,
        position = position,
        state = if (position == 0) "CURRENT" else "UPCOMING",
        insertedBy = "TAP",
        isFavourite = false,
        audioUri = Uri.parse("file:///music/$trackId.mp3"),
        thumbnailUri = null
    )

    private fun remoteItem(position: Int) = QueueTrackItem(
        queueItemId = "queue-remote",
        trackId = "remote-1",
        localTrackId = null,
        remoteItemId = "remote-1",
        displayTitle = "Remote",
        artist = "Channel",
        durationMs = 120_000L,
        position = position,
        state = if (position == 0) "CURRENT" else "UPCOMING",
        insertedBy = "DISCOVER",
        isFavourite = false,
        audioUri = Uri.EMPTY,
        thumbnailUri = null,
        isRemote = true
    )
}
