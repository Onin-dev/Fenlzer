package com.fenl.fenlzer.data.local.entity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueItemEntityTest {
    @Test
    fun hasExactlyOneMediaReferenceForLocalTrack() {
        val item = queueItem(trackId = "track-1", remoteItemId = null)

        assertTrue(item.hasExactlyOneMediaReference())
    }

    @Test
    fun hasExactlyOneMediaReferenceForRemoteItem() {
        val item = queueItem(trackId = null, remoteItemId = "remote-1")

        assertTrue(item.hasExactlyOneMediaReference())
    }

    @Test
    fun rejectsMissingOrDoubleMediaReferences() {
        assertFalse(queueItem(trackId = null, remoteItemId = null).hasExactlyOneMediaReference())
        assertFalse(queueItem(trackId = "track-1", remoteItemId = "remote-1").hasExactlyOneMediaReference())
    }

    private fun queueItem(
        trackId: String?,
        remoteItemId: String?
    ): QueueItemEntity = QueueItemEntity(
        queueItemId = "queue-item",
        queueStateId = "queue",
        trackId = trackId,
        remoteItemId = remoteItemId,
        position = 0,
        state = "CURRENT",
        insertedBy = "RESTORE",
        addedAt = 1L
    )
}
