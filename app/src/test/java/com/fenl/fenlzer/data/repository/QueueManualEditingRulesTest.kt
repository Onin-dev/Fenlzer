package com.fenl.fenlzer.data.repository

import com.fenl.fenlzer.data.local.dao.QueueDao
import com.fenl.fenlzer.data.local.entity.QueueItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueManualEditingRulesTest {
    @Test
    fun moveItem_keepsCurrentSongCurrent() {
        val items = listOf(
            item("previous", 0, QueueListEditor.STATE_PREVIOUS),
            item("current", 1, QueueListEditor.STATE_CURRENT),
            item("upcoming", 2, QueueListEditor.STATE_UPCOMING)
        )

        val edited = QueueListEditor.moveItem(items, "current", "previous", 2)

        assertEquals("current", edited.currentQueueItemId)
        assertEquals(QueueListEditor.STATE_CURRENT, edited.items.first { it.queueItemId == "current" }.state)
        assertTrue(edited.items.indexOfFirst { it.queueItemId == "previous" } > edited.items.indexOfFirst { it.queueItemId == "current" })
    }

    @Test
    fun moveItem_rejectsMovingCurrentSong() {
        val items = listOf(
            item("a", 0, QueueListEditor.STATE_PREVIOUS),
            item("current", 1, QueueListEditor.STATE_CURRENT),
            item("b", 2, QueueListEditor.STATE_UPCOMING)
        )

        val edited = QueueListEditor.moveItem(items, "current", "current", 1)

        assertFalse(edited.changed)
        assertEquals("Current song stays fixed", edited.message)
        assertEquals("current", edited.currentQueueItemId)
    }

    @Test
    fun shuffleUpcoming_preservesPreviousAndCurrentPrefix() {
        val items = listOf(
            item("previous", 0, QueueListEditor.STATE_PREVIOUS),
            item("current", 1, QueueListEditor.STATE_CURRENT),
            item("up1", 2, QueueListEditor.STATE_UPCOMING),
            item("up2", 3, QueueListEditor.STATE_UPCOMING),
            item("up3", 4, QueueListEditor.STATE_UPCOMING)
        )

        val edited = QueueListEditor.shuffleUpcoming(items, "current")

        assertEquals("previous", edited.items[0].queueItemId)
        assertEquals("current", edited.items[1].queueItemId)
        assertEquals(QueueListEditor.STATE_CURRENT, edited.items[1].state)
        assertEquals(setOf("up1", "up2", "up3"), edited.items.drop(2).map { it.queueItemId }.toSet())
    }

    private fun item(id: String, position: Int, state: String) = QueueItemEntity(
        queueItemId = id,
        queueStateId = QueueDao.DEFAULT_QUEUE_STATE_ID,
        trackId = "track_$id",
        remoteItemId = null,
        position = position,
        state = state,
        insertedBy = QueueListEditor.INSERTED_BY_TAP,
        addedAt = position.toLong()
    )
}
