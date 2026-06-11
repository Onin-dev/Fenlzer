package com.fenl.fenlzer.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AlbumIdentityRulesTest {
    @Test
    fun albumIdentityUsesAlbumAndAlbumArtist() {
        val first = MetadataRepository.albumIdentityKey("Same Album", "Album Artist")
        val second = MetadataRepository.albumIdentityKey("Same Album", "Album Artist")
        val differentArtist = MetadataRepository.albumIdentityKey("Same Album", "Other Artist")
        val differentAlbum = MetadataRepository.albumIdentityKey("Other Album", "Album Artist")

        assertEquals(first, second)
        assertNotEquals(first, differentArtist)
        assertNotEquals(first, differentAlbum)
    }

    @Test
    fun albumIdentityIsSortKeyStableButPreservesDisplayText() {
        val first = MetadataRepository.albumIdentityKey("  Café Album  ", "Artist")
        val second = MetadataRepository.albumIdentityKey("Cafe Album", "Artist")

        assertNotEquals(first, second)
        assertEquals("cafe album\u001Fartist\u001F  Café Album  \u001FArtist", first)
    }
}
