package com.fenl.fenlzer.data.repository

import com.fenl.fenlzer.data.local.entity.QueueItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class QueueListEditorTest {
    @Test
    fun playNextMovesDuplicateAfterExistingPlayNextItems() {
        val current = item("current", "track-1", 0, QueueListEditor.STATE_CURRENT, "TAP")
        val firstPlayNext = item("play-next", "track-2", 1, QueueListEditor.STATE_UPCOMING, "PLAY_NEXT")
        val duplicate = item("old-duplicate", "track-3", 2, QueueListEditor.STATE_UPCOMING, "ADD_TO_QUEUE")
        val normalUpcoming = item("upcoming", "track-4", 3, QueueListEditor.STATE_UPCOMING, "ADD_TO_QUEUE")
        val newDuplicate = item("new-duplicate", "track-3", 99, QueueListEditor.STATE_UPCOMING, "PLAY_NEXT")

        val edited = QueueListEditor.playNext(
            existingItems = listOf(current, firstPlayNext, duplicate, normalUpcoming),
            currentQueueItemId = current.queueItemId,
            newItem = newDuplicate
        )

        assertEquals(
            listOf("track-1", "track-2", "track-3", "track-4"),
            edited.items.mapNotNull { it.trackId }
        )
        assertEquals("new-duplicate", edited.items[2].queueItemId)
        assertEquals(QueueListEditor.INSERTED_BY_PLAY_NEXT, edited.items[2].insertedBy)
    }

    @Test
    fun addToQueueMovesDuplicateToEnd() {
        val current = item("current", "track-1", 0, QueueListEditor.STATE_CURRENT, "TAP")
        val duplicate = item("old-duplicate", "track-2", 1, QueueListEditor.STATE_UPCOMING, "PLAY_NEXT")
        val normalUpcoming = item("upcoming", "track-3", 2, QueueListEditor.STATE_UPCOMING, "ADD_TO_QUEUE")
        val newDuplicate = item("new-duplicate", "track-2", 99, QueueListEditor.STATE_UPCOMING, "ADD_TO_QUEUE")

        val edited = QueueListEditor.addToQueue(
            existingItems = listOf(current, duplicate, normalUpcoming),
            currentQueueItemId = current.queueItemId,
            newItem = newDuplicate
        )

        assertEquals(
            listOf("track-1", "track-3", "track-2"),
            edited.items.mapNotNull { it.trackId }
        )
        assertEquals("new-duplicate", edited.items.last().queueItemId)
    }

    @Test
    fun currentSongCannotBeQueuedAgain() {
        val current = item("current", "track-1", 0, QueueListEditor.STATE_CURRENT, "TAP")
        val edited = QueueListEditor.addToQueue(
            existingItems = listOf(current),
            currentQueueItemId = current.queueItemId,
            newItem = item("duplicate", "track-1", 99, QueueListEditor.STATE_UPCOMING, "ADD_TO_QUEUE")
        )

        assertFalse(edited.changed)
        assertEquals("Already playing", edited.message)
        assertEquals(listOf("current"), edited.items.map { it.queueItemId })
    }

    @Test
    fun removingCurrentPromotesNextUpcomingSong() {
        val current = item("current", "track-1", 0, QueueListEditor.STATE_CURRENT, "TAP")
        val next = item("next", "track-2", 1, QueueListEditor.STATE_UPCOMING, "ADD_TO_QUEUE")

        val edited = QueueListEditor.removeItem(
            existingItems = listOf(current, next),
            currentQueueItemId = current.queueItemId,
            queueItemIdToRemove = current.queueItemId
        )

        assertEquals("next", edited.currentQueueItemId)
        assertEquals(QueueListEditor.STATE_CURRENT, edited.items.single().state)
    }

    @Test
    fun replaceWithTrackIdsRemovesDuplicatesAndStartsSelectedSong() {
        var nextId = 0

        val edited = QueueListEditor.replaceWithTrackIds(
            trackIds = listOf("track-1", "track-2", "track-1", "track-3"),
            startTrackId = "track-2",
            queueStateId = "default_queue",
            insertedBy = QueueListEditor.INSERTED_BY_PLAYLIST_START,
            now = { 1L },
            idFactory = { "queue-${nextId++}" }
        )

        assertEquals(listOf("track-1", "track-2", "track-3"), edited.items.mapNotNull { it.trackId })
        assertEquals("queue-1", edited.currentQueueItemId)
        assertEquals(QueueListEditor.STATE_CURRENT, edited.items[1].state)
        assertEquals(
            QueueListEditor.INSERTED_BY_PLAYLIST_START,
            edited.items[1].insertedBy
        )
    }

    @Test
    fun replaceWithTrackIdsFallsBackToFirstSongWhenStartIsMissing() {
        var nextId = 0

        val edited = QueueListEditor.replaceWithTrackIds(
            trackIds = listOf("track-1", "track-2"),
            startTrackId = "missing",
            queueStateId = "default_queue",
            insertedBy = QueueListEditor.INSERTED_BY_SMART_PLAYLIST_START,
            now = { 1L },
            idFactory = { "queue-${nextId++}" }
        )

        assertEquals("queue-0", edited.currentQueueItemId)
        assertEquals(QueueListEditor.STATE_CURRENT, edited.items.first().state)
        assertEquals(
            QueueListEditor.INSERTED_BY_SMART_PLAYLIST_START,
            edited.items.first().insertedBy
        )
    }

    private fun item(
        queueItemId: String,
        trackId: String,
        position: Int,
        state: String,
        insertedBy: String
    ): QueueItemEntity = QueueItemEntity(
        queueItemId = queueItemId,
        queueStateId = "default_queue",
        trackId = trackId,
        remoteItemId = null,
        position = position,
        state = state,
        insertedBy = insertedBy,
        addedAt = position.toLong()
    )
}
