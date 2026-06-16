package com.fenl.fenlzer.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportIntentTest {
    @Test
    fun remoteFavouriteUsesAutomaticPriorityAndDurableFavouriteIntent() {
        val intent = ImportIntent.discoverAutoFavourite()

        assertEquals(ImportSourceType.DISCOVER_AUTO_FAVOURITE, intent.sourceType)
        assertEquals(ImportReason.AUTO_FAVOURITE, intent.reason)
        assertEquals(ImportPriorityClass.AUTO, intent.priorityClass)
        assertEquals(ImportPendingAction.FAVOURITE, intent.pendingActionType)
        assertTrue(intent.targetFavourite)
        assertNull(intent.targetPlaylistId)
    }

    @Test
    fun remotePlaylistUsesAutomaticPriorityAndPersistsTargetPlaylist() {
        val intent = ImportIntent.discoverAutoPlaylist("playlist-1")

        assertEquals(ImportSourceType.DISCOVER_AUTO_PLAYLIST, intent.sourceType)
        assertEquals(ImportReason.AUTO_PLAYLIST_ADD, intent.reason)
        assertEquals(ImportPriorityClass.AUTO, intent.priorityClass)
        assertEquals(ImportPendingAction.ADD_TO_PLAYLIST, intent.pendingActionType)
        assertFalse(intent.targetFavourite)
        assertEquals("playlist-1", intent.targetPlaylistId)
    }
}
